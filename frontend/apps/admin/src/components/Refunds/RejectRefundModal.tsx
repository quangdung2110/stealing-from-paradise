import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { adminRefundApi, type RefundResponse } from '@shared/api/refund.api';
import { fmtVnd } from '@shared/utils/format';

export default function RejectRefundModal({ refund, onClose, onSuccess }: { refund: RefundResponse; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [fraudEvidence, setFraudEvidence] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const mut = useMutation({
    mutationFn: () => adminRefundApi.reject(refund.refundId, {
      rejectReason: reason,
      fraudEvidence: fraudEvidence,
    }),
    onSuccess: () => { setDone(true); setTimeout(() => { onSuccess(); onClose(); }, 1500); },
    onError: (err: any) => { setError(err?.response?.data?.message || 'Từ chối thất bại'); },
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Đã từ chối yêu cầu hoàn tiền</h3>
          <p className="text-sm text-gray-500">Khách hàng sẽ nhận được thông báo từ chối.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Từ chối hoàn tiền</h3>
        <div className="bg-gray-50 rounded-xl p-3 mb-4 space-y-1 text-sm">
          <p><span className="text-gray-500">Mã hoàn:</span> <span className="font-mono font-medium">#{refund.refundId}</span></p>
          <p><span className="text-gray-500">Số tiền:</span> <span className="font-bold text-red-600">{fmtVnd(refund.amount)}</span></p>
          <p><span className="text-gray-500">Lý do khách:</span> <span className="font-medium">{refund.reason}</span></p>
        </div>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do từ chối *</label>
            <select
              value={reason}
              onChange={e => setReason(e.target.value)}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Chọn lý do</option>
              <option value="Yêu cầu không hợp lệ">Yêu cầu không hợp lệ</option>
              <option value="Không có bằng chứng đầy đủ">Không có bằng chứng đầy đủ</option>
              <option value="Đã quá thời hạn hoàn tiền">Đã quá thời hạn hoàn tiền</option>
              <option value="Hàng đã sử dụng / hư hỏng">Hàng đã sử dụng / hư hỏng</option>
              <option value="Trùng lặp yêu cầu hoàn">Trùng lặp yêu cầu hoàn</option>
              <option value="Khác">Khác</option>
            </select>
          </div>
          <label className="flex items-center gap-3 p-3 border rounded-xl cursor-pointer hover:bg-gray-50">
            <input
              type="checkbox"
              checked={fraudEvidence}
              onChange={e => setFraudEvidence(e.target.checked)}
              className="w-4 h-4 accent-red-600"
            />
            <div>
              <p className="text-sm font-medium text-gray-900">Cảnh báo gian lận</p>
              <p className="text-xs text-gray-500">Đánh dấu nếu phát hiện hành vi lạm dụng hoàn tiền. Điểm tin cậy của khách sẽ bị trừ.</p>
            </div>
          </label>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Từ chối'}
          </button>
        </div>
      </div>
    </div>
  );
}
