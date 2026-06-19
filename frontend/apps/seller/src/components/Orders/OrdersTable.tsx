import { type SellerOrderSummary } from '@shared/api/order.api';
import { fmtVnd, fmtDate } from '@shared/utils/format';
import { OrderStatusBadge } from '@/lib/orderStatus';
import { canShip, canCancel, canReturnToSender } from '@/lib/orderActions';

interface OrdersTableProps {
  orders: SellerOrderSummary[];
  onViewDetail: (order: SellerOrderSummary) => void;
  onShip: (order: SellerOrderSummary) => void;
  onCancel: (order: SellerOrderSummary) => void;
  onReturnToSender: (order: SellerOrderSummary) => void;
}

export default function OrdersTable({
  orders,
  onViewDetail,
  onShip,
  onCancel,
  onReturnToSender,
}: OrdersTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 border-b border-gray-100">
            <tr>
              {['Mã đơn', 'Khách hàng', 'Sản phẩm', 'Tổng tiền', 'Trạng thái', 'Ngày đặt', 'Thao tác'].map((h) => (
                <th
                  key={h}
                  className="px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.orderId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                <td className="px-5 py-4">
                  <button
                    onClick={() => onViewDetail(order)}
                    className="font-mono font-medium text-gray-900 hover:text-blue-600 focus:outline-none"
                  >
                    {order.orderCode}
                  </button>
                  {order.isFlashSale && (
                    <span className="ml-2 text-[10px] bg-orange-100 text-orange-600 px-1.5 py-0.5 rounded font-medium">
                      ⚡FS
                    </span>
                  )}
                </td>
                <td className="px-5 py-4 text-gray-700">
                  <p className="font-medium">{order.buyerName || `User #${order.buyerId}`}</p>
                  {order.buyerUsername && <p className="text-xs text-gray-400">@{order.buyerUsername}</p>}
                </td>
                <td className="px-5 py-4 text-gray-500">{order.itemCount} sản phẩm</td>
                <td className="px-5 py-4 font-semibold text-gray-900">{fmtVnd(order.finalAmt)}</td>
                <td className="px-5 py-4">
                  <OrderStatusBadge status={order.status} />
                </td>
                <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{fmtDate(order.createdAt)}</td>
                <td className="px-5 py-4">
                  <div className="flex gap-2 flex-wrap">
                    {canShip(order.status) && (
                      <button
                        onClick={() => onShip(order)}
                        className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 font-medium whitespace-nowrap"
                      >
                        📦 Vận đơn
                      </button>
                    )}
                    {canCancel(order.status, order.trackingNumber) && (
                      <button
                        onClick={() => onCancel(order)}
                        className="inline-flex items-center gap-1 text-xs text-red-500 hover:text-red-600 font-medium whitespace-nowrap"
                      >
                        ✕ Huỷ
                      </button>
                    )}
                    {canReturnToSender(order.status) && (
                      <button
                        onClick={() => onReturnToSender(order)}
                        className="inline-flex items-center gap-1 text-xs text-orange-600 hover:text-orange-700 font-medium whitespace-nowrap"
                      >
                        ↩ Hoàn
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
