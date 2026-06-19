import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, type PendingProduct } from '@shared/api/admin.api';

const getErrMsg = (err: unknown) =>
  (err as { response?: { data?: { message?: string } } })?.response?.data?.message
  ?? 'Có lỗi xảy ra, vui lòng thử lại.';

export default function RejectProductModal({ product, onClose, onSuccess }: { product: PendingProduct; onClose: () => void; onSuccess: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [customReason, setCustomReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const finalReason = reason === '__OTHER__' ? customReason.trim() : reason;
  const canSubmit = finalReason.length >= 10;

  const mut = useMutation({
    mutationFn: () => adminApi.rejectProduct(product.productId, finalReason),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: unknown) => setError(getErrMsg(err)),
  });

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Từ chối sản phẩm</h3>
        <p className="text-sm text-gray-500 mb-4">
          Từ chối "<strong>{product.name}</strong>" của seller #{product.sellerId}.
        </p>
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do từ chối *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Thông tin sản phẩm không đầy đủ">Thông tin sản phẩm không đầy đủ</option>
            <option value="Hình ảnh không hợp lệ">Hình ảnh không hợp lệ</option>
            <option value="Giá không hợp lệ">Giá không hợp lệ</option>
            <option value="Vi phạm chính sách">Vi phạm chính sách</option>
            <option value="Sản phẩm cấm">Sản phẩm cấm</option>
            <option value="__OTHER__">Khác (tự nhập)</option>
          </select>
          {reason === '__OTHER__' && (
            <div className="mt-3">
              <textarea
                value={customReason}
                onChange={e => setCustomReason(e.target.value)}
                rows={3}
                maxLength={500}
                placeholder="Nhập lý do từ chối (tối thiểu 10 ký tự)..."
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">{customReason.trim().length}/500 ký tự (tối thiểu 10)</p>
            </div>
          )}
        </div>
        {error && (
          <p className="text-sm text-red-600 mb-4 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
        )}
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!canSubmit || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {mut.isPending ? '...' : 'Từ chối'}
          </button>
        </div>
      </div>
    </div>
  );
}
