import { create } from 'zustand';
import { chatApi, streamChat, type ChatMessage, type PendingConfirmation } from '../api/chat.api';

interface ChatState {
  currentSessionId: string | null;
  messages: (ChatMessage & { products?: any[] })[];
  isStreaming: boolean;
  /** Label shown while the AI is running a tool lookup (UC-AICHAT-002 step 8). */
  toolStatus: string | null;
  pendingConfirmation: PendingConfirmation | null;
  suggestions: string[];
  isOpen: boolean;
  isLoading: boolean;
  error: string | null;
  abortController: AbortController | null;

  setOpen: (isOpen: boolean) => void;
  toggleChat: () => void;
  createSession: () => Promise<string | null>;
  closeSession: () => Promise<void>;
  fetchHistory: (sessionId: string) => Promise<void>;
  fetchSuggestions: () => Promise<void>;
  sendMessage: (message: string) => Promise<void>;
  confirmAction: (confirmId: string) => Promise<void>;
  rejectAction: (confirmId: string) => Promise<void>;
  clearChat: () => void;
  cancelStreaming: () => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  currentSessionId: null,
  messages: [],
  isStreaming: false,
  toolStatus: null,
  pendingConfirmation: null,
  suggestions: [],
  isOpen: false,
  isLoading: false,
  error: null,
  abortController: null,

  setOpen: (isOpen) => set({ isOpen }),
  toggleChat: () => set((state) => ({ isOpen: !state.isOpen })),

  createSession: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await chatApi.createSession();
      if (data?.data?.id) {
        const sessId = data.data.id;
        set({ currentSessionId: sessId, isLoading: false });
        // Initial assistant message
        set({
          messages: [
            {
              role: 'ASSISTANT',
              content: 'Xin chào! Tôi có thể giúp gì cho bạn hôm nay?',
              sessionId: sessId,
              createdAt: new Date().toISOString(),
            },
          ],
        });
        return sessId;
      }
      throw new Error('Không nhận được ID phiên chat');
    } catch (err: any) {
      const errMsg = err?.response?.data?.message || 'Không thể tạo phiên chat AI';
      set({ error: errMsg, isLoading: false });
      return null;
    }
  },

  closeSession: async () => {
    const { currentSessionId } = get();
    if (!currentSessionId) return;

    try {
      await chatApi.closeSession(currentSessionId);
      get().clearChat();
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Không thể đóng phiên chat AI' });
    }
  },

  fetchHistory: async (sessionId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await chatApi.getHistory(sessionId, { pageSize: 50 });
      // The backend returns user, assistant, tool_call, tool_result messages.
      // We'll filter and sort to display them nicely.
      // Filter out tool messages if we only want conversation, or let's keep assistant and user messages.
      const displayMsgs = (data?.data || [])
        .filter((msg) => msg.role === 'USER' || msg.role === 'ASSISTANT')
        .sort((a, b) => (a.sequenceNo || 0) - (b.sequenceNo || 0));

      set({ messages: displayMsgs, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Không thể lấy lịch sử chat',
        isLoading: false,
      });
    }
  },

  fetchSuggestions: async () => {
    try {
      const { data } = await chatApi.getSuggestions();
      set({ suggestions: data?.data || [] });
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Không thể tải gợi ý chat AI' });
    }
  },

  sendMessage: async (message) => {
    let sessId = get().currentSessionId;
    if (!sessId) {
      sessId = await get().createSession();
      if (!sessId) return;
    }

    // Append user message
    const userMsg: ChatMessage = {
      role: 'USER',
      content: message,
      sessionId: sessId,
      createdAt: new Date().toISOString(),
    };

    const assistantMsg: ChatMessage & { products?: any[] } = {
      role: 'ASSISTANT',
      content: '',
      sessionId: sessId,
      createdAt: new Date().toISOString(),
    };

    set((state) => ({
      messages: [...state.messages, userMsg, assistantMsg],
      isStreaming: true,
      error: null,
    }));

    // Start SSE streaming
    const controller = streamChat(sessId, message, {
      onDelta: (chunk) => {
        set((state) => {
          const updatedMessages = [...state.messages];
          const lastIdx = updatedMessages.length - 1;
          if (lastIdx >= 0 && updatedMessages[lastIdx].role === 'ASSISTANT') {
            updatedMessages[lastIdx] = {
              ...updatedMessages[lastIdx],
              content: updatedMessages[lastIdx].content + chunk,
            };
          }
          // First token means any tool lookup has finished.
          return { messages: updatedMessages, toolStatus: null };
        });
      },
      onEvent: (event, data) => {
        if (event === 'tool_start' || event === 'tool_call') {
          // UC-AICHAT-002 step 8: surface "đang tra cứu" while a tool runs.
          set({ toolStatus: data?.label || data?.toolName || 'Đang tra cứu thông tin...' });
        } else if (event === 'confirmation_required') {
          set({ pendingConfirmation: data, toolStatus: null });
        } else if (event === 'products') {
          set((state) => {
            const updatedMessages = [...state.messages];
            const lastIdx = updatedMessages.length - 1;
            if (lastIdx >= 0 && updatedMessages[lastIdx].role === 'ASSISTANT') {
              updatedMessages[lastIdx] = {
                ...updatedMessages[lastIdx],
                products: data,
              };
            }
            return { messages: updatedMessages };
          });
        }
      },
      onError: (error) => {
        // UC-AICHAT-001 A2 / UC-AICHAT-002 422: session expired → drop the
        // sessionId so the next message auto-creates a fresh session.
        const expired = /\b422\b/.test(error) || /expired/i.test(error);
        set({
          error: expired
            ? 'Phiên chat đã hết hạn. Vui lòng gửi lại tin nhắn để bắt đầu phiên mới.'
            : error,
          isStreaming: false,
          abortController: null,
          toolStatus: null,
          ...(expired ? { currentSessionId: null } : {}),
        });
      },
      onDone: () => {
        set({ isStreaming: false, abortController: null, toolStatus: null });
      },
    });

    set({ abortController: controller });
  },

  confirmAction: async (confirmId) => {
    const sessId = get().currentSessionId;
    set({ isStreaming: true, error: null, pendingConfirmation: null });
    try {
      await chatApi.confirmAction(confirmId, true, sessId);
      if (sessId) {
        await get().fetchHistory(sessId);
      }
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Lỗi xác nhận hành động',
      });
    } finally {
      set({ isStreaming: false });
    }
  },

  rejectAction: async (confirmId) => {
    const sessId = get().currentSessionId;
    set({ isStreaming: true, error: null, pendingConfirmation: null });
    try {
      await chatApi.confirmAction(confirmId, false, sessId);
      if (sessId) {
        await get().fetchHistory(sessId);
      }
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Lỗi hủy bỏ hành động',
      });
    } finally {
      set({ isStreaming: false });
    }
  },

  cancelStreaming: () => {
    const { abortController } = get();
    if (abortController) {
      abortController.abort();
      set({ isStreaming: false, abortController: null });
    }
  },

  clearChat: () => {
    set({
      currentSessionId: null,
      messages: [],
      isStreaming: false,
      toolStatus: null,
      pendingConfirmation: null,
      error: null,
      abortController: null,
    });
  },
}));
