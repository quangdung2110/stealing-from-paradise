/**
 * CancelOrderModal — UC-ORDER-008 "Cancel Order (Seller)".
 *
 * The seller cancels a PAID sub-order they cannot fulfil, *before* it ships.
 * Requires a reason of at least 10 characters.
 */
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { orderApi } from '@shared/api/order.api';
import { validateCancelReason, CANCEL_REASON_MAX } from '@/lib/orderActions';

interface CancelOrderModalProps {
  orderId: number;
  orderCode: string;
  onClose: () => void;
  onSuccess: () => void;
}

export default function CancelOrderModal({ orderId, orderCode, onClose, onSuccess }: CancelOrderModalProps) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const validation = validateCancelReason(reason);

  const mut = useMutation({
    mutationFn: () => orderApi.cancelOrder(orderId, { reason: reason.trim(), note: note.trim() || undefined }),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Huỷ đơn thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-1">✕ Huỷ đơn hàng</h3>
        <p className="text-sm text-red-600 mb-4">
          Đơn <strong>{orderCode}</strong> sẽ được huỷ và <strong>hoàn tiền toàn bộ</strong> cho khách.
          Hành động này không thể hoàn tác.
        </p>

        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}

        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Lý do huỷ *
              <span className="text-gray-400 font-normal ml-1">(tối thiểu 10 ký tự)</span>
            </label>
            <textarea value={reason} onChange={e => setReason(e.target.value)}
              rows={3} maxLength={CANCEL_REASON_MAX}
              placeholder="VD: Hết hàng, không thể giao đúng hẹn..."
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-500 resize-none" />
            {reason.length > 0 && !validation.valid && (
              <p className="text-xs text-red-600 mt-1">{validation.error}</p>
            )}
            {reason.length > 0 && (
              <p className="text-xs text-gray-400 mt-0.5">{reason.length}/{CANCEL_REASON_MAX}</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú cho khách (tùy chọn)</label>
            <textarea value={note} onChange={e => setNote(e.target.value)} rows={2}
              placeholder="VD: Khách sẽ được hoàn tiền trong 5-10 ngày..."
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-500 resize-none" />
          </div>
        </div>

        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button onClick={() => mut.mutate()} disabled={!validation.valid || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50">
            {mut.isPending ? '⏳ Đang huỷ...' : '✕ Xác nhận huỷ đơn'}
          </button>
        </div>
      </div>
    </div>
  );
}
