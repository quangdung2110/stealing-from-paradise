import { useNavigate } from 'react-router-dom';
import { type SellerOrderSummary } from '@shared/api/order.api';
import { fmtVnd } from '@shared/utils/format';
import { OrderStatusBadge } from '@/lib/orderStatus';

export default function OrderDrawer({ order, onClose }: { order: SellerOrderSummary; onClose: () => void }) {
  const navigate = useNavigate();

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex justify-end" onClick={onClose}>
      <div
        className="bg-white w-full max-w-md h-full overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="sticky top-0 bg-white border-b p-5 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-gray-900">Chi tiết đơn #{order.orderCode}</h3>
            <OrderStatusBadge status={order.status} />
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl">×</button>
        </div>
        <div className="p-5 space-y-5">
          {/* Flash sale badge */}
          {order.isFlashSale && (
            <div className="bg-gradient-to-r from-orange-50 to-red-50 border border-orange-200 rounded-xl p-3 flex items-center gap-2">
              <span>⚡</span>
              <span className="text-sm font-medium text-orange-800">Đơn hàng Flash Sale</span>
            </div>
          )}

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">👤 Thông tin khách hàng</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <p><span className="text-gray-500">Tên:</span> <span className="font-medium">{order.buyerName || `User #${order.buyerId}`}</span></p>
              {order.buyerUsername && <p><span className="text-gray-500">Username:</span> <span className="font-medium">@{order.buyerUsername}</span></p>}
              <p><span className="text-gray-500">Địa chỉ:</span> <span className="font-medium">{order.shippingAddress?.fullAddress || '—'}</span></p>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">📦 Vận chuyển</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <p><span className="text-gray-500">Mã vận đơn:</span> <span className="font-medium font-mono">{order.trackingNumber || '—'}</span></p>
              <p><span className="text-gray-500">Đơn vị:</span> <span className="font-medium">{order.carrier || '—'}</span></p>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">💳 Thanh toán</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <div className="flex justify-between">
                <span className="text-gray-500">Tổng tiền:</span>
                <span className="text-gray-900">{fmtVnd(order.totalAmt)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Thanh toán:</span>
                <span className="font-bold text-red-600">{fmtVnd(order.finalAmt)}</span>
              </div>
            </div>
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => { onClose(); navigate(`/orders/${order.orderId}`); }}
              className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium transition-colors"
            >
              Xem chi tiết đầy đủ →
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
