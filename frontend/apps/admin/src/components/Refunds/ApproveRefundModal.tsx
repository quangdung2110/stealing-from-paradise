import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { adminRefundApi, type RefundDetailResponse, type RefundResponse } from '@shared/api/refund.api';
import { Skeleton } from '@shared/components/ui';
import { fmtVnd } from '@shared/utils/format';

export default function ApproveRefundModal({ refund, onClose, onSuccess }: { refund: RefundResponse; onClose: () => void; onSuccess: () => void }) {
  const [adminNote, setAdminNote] = useState('');
  const [adjustAmount, setAdjustAmount] = useState('');
  const [causedBy, setCausedBy] = useState<'SELLER' | 'BUYER'>('BUYER');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['admin-refund-detail', refund.refundId],
    queryFn: () => adminRefundApi.getById(refund.refundId).then(r => r.data.data),
    retry: 1,
  });

  const current: RefundDetailResponse = detail ?? refund;
  const evidenceImages = current.evidenceImages ?? [];
  const items = current.items ?? [];

  const mut = useMutation({
    mutationFn: () => adminRefundApi.approve(refund.refundId, {
      adminNote,
      adjustAmount: adjustAmount ? parseFloat(adjustAmount) : undefined,
      causedBy,
      trackingNumber: trackingNumber || undefined,
    }),
    onSuccess: () => { setDone(true); setTimeout(() => { onSuccess(); onClose(); }, 1500); },
    onError: (err: any) => { setError(err?.response?.data?.message || 'Duyệt hoàn tiền thất bại'); },
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Duyệt hoàn tiền thành công!</h3>
          <p className="text-sm text-gray-500">Stripe refund đã được tạo. Khách hàng sẽ nhận được tiền trong 3-5 ngày.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Duyệt hoàn tiền</h3>
        <div className="bg-gray-50 rounded-xl p-3 mb-4 space-y-1 text-sm">
          <p><span className="text-gray-500">Mã hoàn:</span> <span className="font-mono font-medium">#{current.refundId}</span></p>
          <p><span className="text-gray-500">Loại:</span> <span className="font-medium">{current.type === 'FULL' ? 'Hoàn toàn bộ' : 'Hoàn một phần'}</span></p>
          <p><span className="text-gray-500">Số tiền:</span> <span className="font-bold text-red-600">{fmtVnd(current.amount)}</span></p>
          <p><span className="text-gray-500">Lý do:</span> <span className="font-medium">{current.reason}</span></p>
          <p><span className="text-gray-500">Người yêu cầu:</span> <span className="font-medium">{current.initiatedBy === 'BUYER' ? 'Khách hàng' : 'Người bán'}</span></p>
        </div>

        {detailLoading && (
          <div className="mb-4 rounded-xl bg-blue-50 p-3">
            <Skeleton className="h-4 w-2/3" />
          </div>
        )}

        {evidenceImages.length > 0 && (
          <div className="mb-4">
            <p className="mb-2 text-xs font-semibold uppercase text-gray-500">Ảnh bằng chứng</p>
            <div className="grid grid-cols-3 gap-2">
              {evidenceImages.map((url, index) => (
                <a key={`${url}-${index}`} href={url} target="_blank" rel="noreferrer" className="block overflow-hidden rounded-xl border border-gray-100 bg-gray-50">
                  <img src={url} alt={`Bằng chứng ${index + 1}`} className="h-20 w-full object-cover" />
                </a>
              ))}
            </div>
          </div>
        )}

        {items.length > 0 && (
          <div className="mb-4">
            <p className="mb-2 text-xs font-semibold uppercase text-gray-500">Sản phẩm hoàn</p>
            <div className="space-y-2">
              {items.map((item, index) => (
                <div key={`${item.orderItemId ?? item.itemId ?? index}`} className="flex items-start justify-between gap-3 rounded-xl bg-gray-50 p-3 text-sm">
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
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="font-semibold text-gray-900">{fmtVnd(item.refundAmount)}</p>
                    <p className="text-xs text-gray-400">{item.status}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú Admin *</label>
            <textarea
              value={adminNote}
              onChange={e => setAdminNote(e.target.value)}
              placeholder="Lý do duyệt hoàn tiền..."
              rows={2}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Điều chỉnh số tiền (VND)</label>
              <input
                type="number"
                value={adjustAmount}
                onChange={e => setAdjustAmount(e.target.value)}
                placeholder={`Mặc định: ${current.amount}`}
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Nguyên nhân</label>
              <select
                value={causedBy}
                onChange={e => setCausedBy(e.target.value as 'SELLER' | 'BUYER')}
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="BUYER">Lỗi khách hàng</option>
                <option value="SELLER">Lỗi người bán</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn hoàn (v5.3)</label>
            <input
              type="text"
              value={trackingNumber}
              onChange={e => setTrackingNumber(e.target.value)}
              placeholder="Mã vận đơn hàng hoàn về (nếu có)"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-xs text-gray-400 mt-1">Ghi nhận mã vận đơn để truy vết hàng hoàn về.</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Hủy</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!adminNote.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Duyệt hoàn tiền'}
          </button>
        </div>
      </div>
    </div>
  );
}
