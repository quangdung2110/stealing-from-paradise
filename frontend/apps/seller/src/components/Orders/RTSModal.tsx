/**
 * RTSModal — UC-ORDER-006 Flow B "Return-To-Sender".
 *
 * Used while an order is SHIPPING: the seller confirms the carrier returned the
 * package, which triggers an automatic full refund. Requires return tracking
 * number + 1-5 evidence images.
 */
import { useState, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { orderApi } from '@shared/api/order.api';
import { validateRtsForm, RTS_MAX_IMAGES } from '@/lib/orderActions';

interface RTSModalProps {
  orderId: number;
  orderCode: string;
  onClose: () => void;
  onSuccess: () => void;
}

export default function RTSModal({ orderId, orderCode, onClose, onSuccess }: RTSModalProps) {
  const [returnTracking, setReturnTracking] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const imageCount = files.length;
  const imageNames = files.map(f => f.name);
  const validation = validateRtsForm(returnTracking, imageCount);

  const mut = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      files.forEach(f => fd.append('evidence_images', f));
      fd.append('return_tracking_number', returnTracking.trim());
      if (note.trim()) fd.append('note', note.trim());
      return orderApi.returnToSender(orderId, fd);
    },
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Xác nhận hoàn hàng thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-1">↩ Xác nhận hoàn hàng</h3>
        <p className="text-sm text-gray-500 mb-4">
          Đơn <strong>{orderCode}</strong> đã được hoàn về. Xác nhận để kích hoạt hoàn tiền tự động cho khách.
        </p>

        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}

        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Mã vận đơn hoàn *
            </label>
            <input type="text" value={returnTracking} onChange={e => setReturnTracking(e.target.value)}
              placeholder="Mã vận đơn từ đơn vị vận chuyển" autoFocus
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Ảnh bằng chứng *
              <span className="text-gray-400 font-normal ml-1">(1-{RTS_MAX_IMAGES} ảnh)</span>
            </label>
            <div
              onClick={() => fileInputRef.current?.click()}
              className="border-2 border-dashed border-gray-300 rounded-xl p-4 text-center cursor-pointer hover:border-blue-400 hover:bg-blue-50/30 transition-all"
            >
              {imageCount > 0 ? (
                <div>
                  <span className="text-sm font-medium text-green-700">✓ {imageCount} ảnh đã chọn</span>
                  <div className="mt-2 text-xs text-gray-400 space-y-0.5">
                    {imageNames.slice(0, 5).map((name, i) => (
                      <p key={i} className="truncate">{name}</p>
                    ))}
                  </div>
                  <button onClick={(e) => { e.stopPropagation(); setFiles([]); }}
                    className="text-xs text-red-500 hover:text-red-600 underline mt-1 block">
                    Xoá tất cả
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-1">
                  <span className="text-2xl">📷</span>
                  <p className="text-sm text-gray-500">Nhấn để chọn ảnh chụp hàng hoàn</p>
                </div>
              )}
            </div>
            <input ref={fileInputRef} type="file" multiple accept="image/*" className="hidden"
              onChange={e => setFiles(Array.from(e.target.files || []))} />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
            <textarea value={note} onChange={e => setNote(e.target.value)} rows={2}
              placeholder="Mô tả tình trạng hàng hoàn..."
              className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
          </div>

          {!validation.valid && (returnTracking || imageCount > 0) && (
            <p className="text-xs text-orange-600">{validation.error}</p>
          )}
        </div>

        <div className="flex gap-3">
          <button onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button onClick={() => mut.mutate()} disabled={!validation.valid || mut.isPending}
            className="flex-1 py-2.5 bg-orange-600 text-white rounded-xl text-sm font-medium hover:bg-orange-700 disabled:opacity-50">
            {mut.isPending ? '⏳ Đang xử lý...' : '↩ Xác nhận hoàn hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}
