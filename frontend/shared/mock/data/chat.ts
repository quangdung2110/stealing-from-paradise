export const MOCK_CHAT_SESSIONS = [
  { id: 'sess_1', status: 'ACTIVE', createdAt: new Date().toISOString() }
];

export const MOCK_CHAT_SUGGESTIONS = [
  { text: 'Kiểm tra tình trạng đơn hàng mới nhất', icon: '📦' },
  { text: 'Tôi muốn yêu cầu hoàn tiền', icon: '💰' },
  { text: 'Tìm sản phẩm tai nghe chống ồn', icon: '🎧' }
];

export const MOCK_CHAT_HISTORY = [
  { id: 'msg_1', sessionId: 'sess_1', role: 'SYSTEM', content: 'Bạn là một trợ lý AI hữu ích phục vụ cho sàn thương mại điện tử Stealing From Paradise.', sequenceNo: 1, createdAt: new Date(Date.now() - 100000).toISOString() },
  { id: 'msg_2', sessionId: 'sess_1', role: 'ASSISTANT', content: 'Xin chào! Tôi có thể giúp gì cho bạn hôm nay?', sequenceNo: 2, createdAt: new Date(Date.now() - 90000).toISOString() }
];
