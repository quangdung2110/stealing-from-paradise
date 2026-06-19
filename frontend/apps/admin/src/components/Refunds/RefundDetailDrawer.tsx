import { useQuery } from '@tanstack/react-query';
import { adminRefundApi, type RefundDetailResponse, type RefundResponse } from '@shared/api/refund.api';
import { Skeleton } from '@shared/components/ui';
import { fmtVnd, fmtDate } from '@shared/utils/format';

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING: { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ duyệt' },
  SUCCESS: { bg: 'bg-green-100', color: 'text-green-700', label: 'Đã hoàn' },
  FAILED: { bg: 'bg-red-100', color: 'text-red-700', label: 'Thất bại' },
  REJECTED: { bg: 'bg-gray-100', color: 'text-gray-600', label: 'Từ chối' },
  APPROVED: { bg: 'bg-blue-100', color: 'text-blue-700', label: 'Đã duyệt' },
  PROCESSING: { bg: 'bg-purple-100', color: 'text-purple-700', label: 'Đang xử lý' },
  COMPLETED: { bg: 'bg-green-100', color: 'text-green-700', label: 'Hoàn thành' },
};

export default function RefundDetailDrawer({ refund, onClose }: { refund: RefundResponse; onClose: () => void }) {
  const { data: detail, isLoading, error } = useQuery({
    queryKey: ['admin-refund-detail', refund.refundId],
    queryFn: () => adminRefundApi.getById(refund.refundId).then(r => r.data.data),
    retry: 1,
  });

  const current: RefundDetailResponse = detail ?? refund;
  const evidenceImages = current.evidenceImages ?? [];
  const items = current.items ?? [];
  const returnEvidence = current.returnEvidence ?? [];
  const status = STATUS_STYLE[current.status] ?? { bg: 'bg-gray-100', color: 'text-gray-600', label: current.status };

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex justify-end" onClick={onClose}>
      <div className="bg-white w-full max-w-md h-full overflow-y-auto" onClick={e => e.stopPropagation()}>
        <div className="sticky top-0 bg-white border-b p-5 flex items-center justify-between">
          <h3 className="font-bold text-gray-900">Chi tiết hoàn tiền</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl">×</button>
        </div>

        <div className="p-5 space-y-5">
          <div className="bg-gray-50 rounded-xl p-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-500">Mã hoàn</span>
              <span className="font-mono font-medium">#{current.refundId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Mã đơn</span>
              <span className="font-mono font-medium">#{current.orderId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Loại</span>
              <span className="font-medium">{current.type === 'FULL' ? 'Hoàn toàn bộ' : 'Hoàn một phần'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Trạng thái</span>
              <span className={`font-medium ${status.color}`}>{status.label}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Số tiền</span>
              <span className="font-bold text-red-600">{fmtVnd(current.amount)}</span>
            </div>
            {current.adjustAmount && (
              <div className="flex justify-between">
                <span className="text-gray-500">Đã điều chỉnh</span>
                <span className="font-medium text-blue-600">{fmtVnd(current.adjustAmount)}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-gray-500">Người yêu cầu</span>
              <span className="font-medium">{current.initiatedBy === 'BUYER' ? 'Khách hàng' : 'Người bán'}</span>
            </div>
            {current.causedBy && (
              <div className="flex justify-between">
                <span className="text-gray-500">Nguyên nhân</span>
                <span className="font-medium">{current.causedBy === 'SELLER' ? 'Người bán' : 'Khách hàng'}</span>
              </div>
            )}
          </div>

          {isLoading && (
            <div className="rounded-xl bg-blue-50 p-3">
              <Skeleton className="h-4 w-2/3" />
            </div>
          )}

          {error && (
            <div className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
              Không thể tải chi tiết mới. Đang hiển thị dữ liệu danh sách.
            </div>
          )}

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do</h4>
            <p className="text-sm text-gray-700 bg-gray-50 rounded-xl p-3">{current.reason}</p>
          </div>

          {evidenceImages.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Ảnh bằng chứng</h4>
              <div className="grid grid-cols-3 gap-2">
                {evidenceImages.map((url, index) => (
                  <a key={`${url}-${index}`} href={url} target="_blank" rel="noreferrer" className="block overflow-hidden rounded-xl border border-gray-100 bg-gray-50">
                    <img src={url} alt={`Bằng chứng ${index + 1}`} className="h-24 w-full object-cover" />
                  </a>
                ))}
              </div>
            </div>
          )}

          {items.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Sản phẩm hoàn</h4>
              <div className="space-y-2">
                {items.map((item, index) => (
                  <div key={`${item.orderItemId ?? item.itemId ?? index}`} className="rounded-xl bg-gray-50 p-3 text-sm">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-start gap-3">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-gray-100 text-lg">
                          {item.imageSnapshot
                            ? <img src={item.imageSnapshot} alt={item.productName ?? ''} className="h-full w-full object-cover" />
                            : '📦'}
                        </div>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-gray-900">{item.productName ?? `Item #${item.orderItemId ?? item.itemId}`}</p>
                          <p className="text-xs text-gray-500">Số lượng: {item.quantity}</p>
                          {item.itemReason && <p className="mt-1 text-xs text-gray-500">{item.itemReason}</p>}
                          {item.returnTrackingNumber && (
                            <p className="mt-1 text-xs text-blue-600">Mã hoàn: {item.returnTrackingNumber}</p>
                          )}
                        </div>
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="font-semibold text-gray-900">{fmtVnd(item.refundAmount)}</p>
                        <p className="text-xs text-gray-400">{item.status}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(returnEvidence.length > 0 || current.trackingNumber) && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Bằng chứng hoàn hàng</h4>
              <div className="space-y-2 rounded-xl bg-blue-50 p-3 text-sm text-blue-800">
                {current.trackingNumber && <p>Mã vận đơn: {current.trackingNumber}</p>}
                {returnEvidence.map((evidence, index) => (
                  <p key={index}>
                    {evidence.type}: {evidence.trackingNumber ?? '—'}
                    {evidence.recordedAt ? ` · ${fmtDate(evidence.recordedAt, true)}` : ''}
                  </p>
                ))}
              </div>
            </div>
          )}

          {current.adminNote && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Ghi chú Admin</h4>
              <p className="text-sm text-gray-700 bg-blue-50 rounded-xl p-3">{current.adminNote}</p>
            </div>
          )}

          {current.rejectReason && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do từ chối</h4>
              <p className="text-sm text-red-700 bg-red-50 rounded-xl p-3">{current.rejectReason}</p>
            </div>
          )}

          {current.stripeRefundId && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Stripe Refund ID</h4>
              <p className="text-xs font-mono text-gray-600 bg-gray-100 rounded-xl p-3 break-all">{current.stripeRefundId}</p>
            </div>
          )}

          <div className="space-y-2 text-xs text-gray-400">
            <p>Tạo: {fmtDate(current.createdAt, true)}</p>
            {current.reviewedAt && <p>Duyệt lúc: {fmtDate(current.reviewedAt, true)}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
