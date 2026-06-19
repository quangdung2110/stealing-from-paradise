import type { StreamChatCallbacks } from '../api/chat.api';

/**
 * Simulate AI chat streaming with realistic Vietnamese responses.
 * Invoked by chat.api.ts::streamChat when mock mode is active.
 */
export function mockChatStream(
  _sessionId: string,
  message: string,
  callbacks: StreamChatCallbacks,
): AbortController {
  const abortController = new AbortController();

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
        responseText = 'Chào bạn! Tôi là trợ lý ảo Stealing From Paradise. Tôi có thể hỗ trợ bạn tìm kiếm sản phẩm, kiểm tra đơn hàng, hoặc xử lý các yêu cầu hoàn tiền một cách nhanh chóng. Bạn đang cần hỗ trợ gì?';
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
          { id: 'prod_2', name: 'Apple AirPods Pro 2', price: 5990000, slug: 'apple-airpods-pro-2' },
        ]);
      }

      callbacks.onDone();
    } catch (err) {
      callbacks.onError(err instanceof Error ? err.message : String(err));
    }
  })();

  return abortController;
}
