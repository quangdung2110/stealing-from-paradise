/**
 * TrackingModal — UC-ORDER-004 "Ship Order".
 *
 * Lets the seller attach a carrier tracking number to a PAID order, which the
 * backend uses to transition it PAID → SHIPPING.
 */
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { orderApi } from '@shared/api/order.api';

const CARRIERS = ['ViettelPost', 'GHN', 'GHTK', 'Ninja Van', 'J&T Express', 'Bưu điện', 'GrabExpress', 'Ahamove', 'Khác'];

interface TrackingModalProps {
  orderId: number;
  orderCode: string;
  customerLabel?: string;
  onClose: () => void;
  onSuccess: () => void;
}

export default function TrackingModal({ orderId, orderCode, customerLabel, onClose, onSuccess }: TrackingModalProps) {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [carrier, setCarrier] = useState(CARRIERS[0]);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => orderApi.updateTracking(orderId, {
      trackingNumber: trackingNumber.trim(),
      carrier,
      note: note.trim() || undefined,
    }),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Cập nhật vận đơn thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-1">📦 Cập nhật vận đơn</h3>
        <p className="text-sm text-gray-500 mb-5">
          Đơn: <strong>{orderCode}</strong>
          {customerLabel && <> · Khách: {customerLabel}</>}
        </p>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>
        )}

        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn *</label>
            <input
              type="text"
              value={trackingNumber}
              onChange={e => setTrackingNumber(e.target.value)}
              placeholder="VD: VT123456789"
              autoFocus
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Đơn vị vận chuyển</label>
            <select value={carrier} onChange={e => setCarrier(e.target.value)}
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              {CARRIERS.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
            <textarea value={note} onChange={e => setNote(e.target.value)} rows={2}
              placeholder="VD: Giao trong giờ hành chính..."
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
          </div>
        </div>

        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button onClick={() => mut.mutate()}
            disabled={!trackingNumber.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
            {mut.isPending ? '⏳ Đang cập nhật...' : '✅ Xác nhận giao hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}
