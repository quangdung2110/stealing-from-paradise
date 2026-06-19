import apiClient, { handleAuthFailure } from '../lib/axios';
import { authCookies } from '../utils/cookie';
import type { ApiResponse } from '../types/api';
import { isMockMode } from './mock';

export interface ChatMessage {
  id?: string;
  sessionId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL_CALL' | 'TOOL_RESULT';
  content: string;
  toolName?: string;
  sequenceNo?: number;
  createdAt?: string;
}

export interface ChatSession {
  id: string;
  userId?: number;
  status: string;
  createdAt: string;
}

export interface PendingConfirmation {
  confirmId: string;
  actionType: string;
  orderId?: number;
  summary: string;
}

export interface ChatSuggestion {
  text: string;
  icon?: string;
}

export interface StreamChatCallbacks {
  onDelta: (chunk: string) => void;
  onEvent: (event: string, data: any) => void;
  onError: (error: string) => void;
  onDone: () => void;
}

const getChatBaseUrl = () => {
  const baseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';
  return baseUrl.replace('/v1', '') + '/ai';
};

export const chatApi = {
  /** Create a new chat session */
  createSession: () =>
    apiClient.post<ApiResponse<ChatSession>>('/sessions', {}, { baseURL: getChatBaseUrl() }),

  /** Close chat session */
  closeSession: (sessionId: string) =>
    apiClient.delete<ApiResponse<void>>(`/sessions/${sessionId}`, { baseURL: getChatBaseUrl() }),

  /** Get session history */
  getHistory: (sessionId: string, params?: { pageSize?: number; before?: string }) =>
    apiClient.get<ApiResponse<ChatMessage[]>>('/chat/history', {
      baseURL: getChatBaseUrl(),
      params: { sessionId, ...params },
    }),

  /** Get personalized suggestions */
  getSuggestions: () =>
    apiClient.get<ApiResponse<string[]>>('/suggest', { baseURL: getChatBaseUrl() }),

  /**
   * Confirm/Reject a Level-3 (Mức 3) action — UC-AICHAT-003.
   * Sends both the legacy `confirmed` boolean and the documented
   * `decision` enum + `sessionId` so the call satisfies either backend contract.
   */
  confirmAction: (confirmId: string, confirmed: boolean, sessionId?: string | null) =>
    apiClient.post<ApiResponse<ChatMessage>>(
      '/confirm',
      { confirmId, sessionId: sessionId ?? undefined, confirmed, decision: confirmed ? 'CONFIRMED' : 'REJECTED' },
      { baseURL: getChatBaseUrl() },
    ),
};

/** Stream chat response via SSE (POST /chat) */
export function streamChat(
  sessionId: string,
  message: string,
  callbacks: StreamChatCallbacks
): AbortController {
  const abortController = new AbortController();

  if (isMockMode()) {
    // Simulate streaming in mock mode
    (async () => {
      try {
        await new Promise((resolve) => setTimeout(resolve, 500));
        if (abortController.signal.aborted) return;

        const lowerMessage = message.toLowerCase();
        let responseText = '';
        let hasConfirmation = false;
        let hasProducts = false;

        if (lowerMessage.includes('refund') || lowerMessage.includes('hoàn tiền') || lowerMessage.includes('hủy đơn')) {
          responseText = 'Tôi hiểu bạn muốn yêu cầu hoàn tiền cho đơn hàng. Để thực hiện việc này, tôi cần tạo một yêu cầu hoàn tiền mức độ 3. Vui lòng xác nhận hành động bên dưới để tôi có thể gửi yêu cầu lên hệ thống.';
          hasConfirmation = true;
        } else if (lowerMessage.includes('sản phẩm') || lowerMessage.includes('tìm kiếm') || lowerMessage.includes('mua')) {
          responseText = 'Dưới đây là một số sản phẩm bạn có thể quan tâm:';
          hasProducts = true;
        } else {
          responseText = `Chào bạn! Tôi là trợ lý ảo Stealing From Paradise. Tôi có thể hỗ trợ bạn tìm kiếm sản phẩm, kiểm tra đơn hàng, hoặc xử lý các yêu cầu hoàn tiền một cách nhanh chóng. Bạn đang cần hỗ trợ gì?`;
        }

        // Stream text chunks
        const chunks = responseText.split(/(?<=\s)/);
        for (const chunk of chunks) {
          if (abortController.signal.aborted) return;
          callbacks.onDelta(chunk);
          await new Promise((resolve) => setTimeout(resolve, 60));
        }

        if (abortController.signal.aborted) return;

        if (hasConfirmation) {
          await new Promise((resolve) => setTimeout(resolve, 400));
          if (abortController.signal.aborted) return;
          callbacks.onEvent('confirmation_required', {
            confirmId: 'confirm_mock_' + Date.now(),
            actionType: 'REFUND',
            orderId: 1001,
            summary: 'Yêu cầu hoàn tiền đầy đủ cho đơn hàng #1001 lý do: Khách hàng yêu cầu hỗ trợ.',
          });
        } else if (hasProducts) {
          await new Promise((resolve) => setTimeout(resolve, 400));
          if (abortController.signal.aborted) return;
          callbacks.onEvent('products', [
            { id: 'prod_1', name: 'Tai nghe Sony WH-1000XM4', price: 6990000, slug: 'tai-nghe-sony-wh-1000xm4' },
            { id: 'prod_2', name: 'Apple AirPods Pro 2', price: 5990000, slug: 'apple-airpods-pro-2' }
          ]);
        }

        callbacks.onDone();
      } catch (err) {
        callbacks.onError(err instanceof Error ? err.message : String(err));
      }
    })();

    return abortController;
  }

  // Real connection using fetch + ReadableStream
  (async () => {
    try {
      const token = authCookies.get('accessToken');
      const response = await fetch(`${getChatBaseUrl()}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ sessionId, message }),
        signal: abortController.signal,
      });

      if (!response.ok) {
        if (response.status === 401) {
          handleAuthFailure();
        }
        throw new Error(`Kết nối thất bại với mã lỗi: ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('Không thể đọc dữ liệu phản hồi');
      }

      const decoder = new TextDecoder();
      let buffer = '';
      let currentEvent = 'message';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmedStart = line.trimStart();
          if (!trimmedStart) {
            currentEvent = 'message'; // reset
            continue;
          }

          if (trimmedStart.startsWith('event:')) {
            currentEvent = trimmedStart.substring(6).trim();
          } else if (trimmedStart.startsWith('data:')) {
            let data = trimmedStart.substring(5);
            if (data.startsWith(' ')) {
              data = data.substring(1);
            }
            if (currentEvent === 'delta') {
              // raw text delta, preserve spaces
              callbacks.onDelta(data);
            } else if (currentEvent === 'done') {
              callbacks.onDone();
            } else if (currentEvent === 'error') {
              const trimmed = data.trim();
              try {
                const parsed = JSON.parse(trimmed);
                callbacks.onError(parsed.error || 'Lỗi hệ thống');
              } catch {
                callbacks.onError(trimmed || 'Lỗi hệ thống');
              }
            } else {
              // Custom event: confirmation_required, products, order, etc.
              const trimmed = data.trim();
              try {
                const parsedData = JSON.parse(trimmed);
                callbacks.onEvent(currentEvent, parsedData);
              } catch {
                callbacks.onEvent(currentEvent, trimmed);
              }
            }
          }
        }
      }
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        // Ignored, request cancelled
        return;
      }
      callbacks.onError(err instanceof Error ? err.message : String(err));
    }
  })();

  return abortController;
}
