import { useState, useEffect, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { sellerApi, type SellerVariant } from '@shared/api/seller.api';

function generateSku(name: string) {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toUpperCase()
    .slice(0, 18);
}

export default function VariantModal({
  productId,
  initial,
  onClose,
  onSuccess,
}: {
  productId: string;
  initial?: SellerVariant;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [sku, setSku] = useState(initial?.skuCode ?? '');
  const [name, setName] = useState(initial?.variantName ?? '');
  const [price, setPrice] = useState(initial?.price?.toString() ?? '');
  const [stock, setStock] = useState(initial?.stock?.toString() ?? '1');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [skuAuto, setSkuAuto] = useState(!initial);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  // Auto-generate SKU when name changes (for new variants)
  useEffect(() => {
    if (skuAuto && !initial && name.trim()) {
      setSku(generateSku(name));
    }
  }, [name, skuAuto, initial]);

  const mut = useMutation({
    mutationFn: () => {
      const data = { skuCode: sku.trim(), variantName: name.trim(), price: Number(price), stock: Number(stock) };
      if (Number(price) <= 0) throw new Error('Giá phải lớn hơn 0');
      if (Number(stock) < 0) throw new Error('Số lượng không hợp lệ');
      if (!sku.trim()) throw new Error('SKU không được để trống');
      if (!name.trim()) throw new Error('Tên biến thể không được để trống');
      return initial
        ? sellerApi.updateVariant(initial.id, data)
        : sellerApi.createVariant(productId, data);
    },
    onSuccess: () => { setDone(true); timerRef.current = setTimeout(() => { onSuccess(); onClose(); }, 1000); },
    onError: (err: any) => setError(err?.response?.data?.message || err.message || 'Lưu biến thể thất bại'),
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60] p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">
            {initial ? 'Cập nhật thành công!' : 'Tạo biến thể thành công!'}
          </h3>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60] p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">
          {initial ? '✏️ Chỉnh sửa biến thể' : '➕ Thêm biến thể'}
        </h3>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Tên biến thể *
              <span className="text-gray-400 font-normal ml-1">VD: Đen 128GB</span>
            </label>
            <input type="text" value={name} onChange={e => setName(e.target.value)}
              placeholder="VD: iPhone 15 Black 128GB" autoFocus={!initial}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-sm font-medium text-gray-700">SKU *</label>
              {!initial && (
                <button onClick={() => setSkuAuto(!skuAuto)}
                  className={`text-xs font-medium ${skuAuto ? 'text-blue-600' : 'text-gray-400'}`}>
                  {skuAuto ? '🔁 Tự động' : '✏️ Nhập tay'}
                </button>
              )}
            </div>
            <input type="text" value={sku} onChange={e => { setSku(e.target.value); setSkuAuto(false); }}
              placeholder="VD: IPHONE15-BLK-128"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá (VND) *</label>
              <div className="relative">
                <input type="number" value={price} onChange={e => setPrice(e.target.value)} min="0"
                  className="w-full px-3 py-2 pr-7 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400">₫</span>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tồn kho *</label>
              <input type="number" value={stock} onChange={e => setStock(e.target.value)} min="0"
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button onClick={() => { setError(''); mut.mutate(); }} disabled={mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
            {mut.isPending ? 'Đang lưu...' : initial ? 'Cập nhật' : 'Tạo biến thể'}
          </button>
        </div>
      </div>
    </div>
  );
}
