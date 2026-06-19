export const MOCK_NOTIFICATIONS = [
  {
    id: 'notif_1',
    userId: 1,
    type: 'ORDER_STATUS',
    title: 'Đơn hàng đã được xác nhận',
    message: 'Đơn hàng #PO-20240101-1002 của bạn đã được người bán xác nhận và đang chuẩn bị giao.',
    data: { orderId: 1002 },
    read: false,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString()
  },
  {
    id: 'notif_2',
    userId: 1,
    type: 'PROMOTION',
    title: 'Khuyến mãi Flash Sale 50%',
    message: 'Sự kiện Flash Sale cực lớn sắp bắt đầu vào lúc 12:00 hôm nay. Đừng bỏ lỡ!',
    data: { sessionId: 1 },
    read: true,
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString()
  },
  {
    id: 'notif_3',
    userId: 1,
    type: 'REFUND_STATUS',
    title: 'Yêu cầu trả hàng/hoàn tiền được chấp nhận',
    message: 'Yêu cầu hoàn tiền cho đơn hàng #PO-20240101-1001 đã được duyệt. Số tiền sẽ hoàn lại vào tài khoản của bạn trong 2-3 ngày làm việc.',
    data: { refundId: 1 },
    read: false,
    createdAt: new Date(Date.now() - 600000).toISOString()
  }
];
