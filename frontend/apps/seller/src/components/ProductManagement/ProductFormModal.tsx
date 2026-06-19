import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import { categoryApi } from '@shared/api/category.api';
import { fmtVnd } from '@shared/utils/format';
import VariantModal from './VariantModal';
import ImageUploader from './ImageUploader';
import InventoryPanel from './InventoryPanel';
import { notify } from '@shared/lib/toast';
import ConfirmDialog from '@shared/components/ConfirmDialog';

type ProductFormTab = 'info' | 'images' | 'variants' | 'inventory';

function makeDefaultSku(name: string) {
  const base = name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toUpperCase()
    .slice(0, 24) || 'PRODUCT';
  return `${base}-${Date.now().toString(36).toUpperCase()}`;
}

export default function ProductFormModal({
  product,
  initialTab = 'info',
  onClose,
  onSuccess,
}: {
  product?: SellerProduct;
  initialTab?: ProductFormTab;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(product?.name ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [category, setCategory] = useState(product?.categoryId ?? '');
  const [images, setImages] = useState<string[]>(product?.images ?? []);
  const [price, setPrice] = useState(product?.price?.toString() ?? '');
  const [stock, setStock] = useState(product?.stockAvailable?.toString() ?? '1');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);
  const [activeTab, setActiveTab] = useState<ProductFormTab>(initialTab);

  useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab, product?.productId]);

  // Categories query
  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoryApi.getCategories().then(r => r.data.data ?? []),
  });

  useEffect(() => {
    if (!category && categories.length > 0) {
      setCategory(categories[0].categoryId);
    }
  }, [categories, category]);

  // Variants
  const { data: variants = [] } = useQuery({
    queryKey: ['seller-variants', product?.productId],
    queryFn: () => sellerApi.getVariants(product!.productId).then(r => r.data.data ?? []),
    enabled: !!product?.productId,
  });
  const [showVariant, setShowVariant] = useState<SellerVariant | undefined>(undefined);
  const [showVariantForm, setShowVariantForm] = useState(false);
  const [deletingVariant, setDeletingVariant] = useState<SellerVariant | null>(null);
  const isNew = !product;

  const mut = useMutation({
    mutationFn: async (data: { name: string; description: string; categoryId: string; images?: string[] }) => {
      if (product) {
        return sellerApi.updateProduct(product.productId, data);
      }

      const created = await sellerApi.createProduct(data);
      const createdProduct = created.data.data as any;
      const createdProductId = createdProduct?.productId ?? createdProduct?.id;

      if (createdProductId) {
        await sellerApi.createVariant(createdProductId, {
          skuCode: makeDefaultSku(data.name),
          variantName: 'Default',
          price: Number(price),
          stock: Number(stock),
        });
      }

      return created;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      setDone(true);
      timerRef.current = setTimeout(() => { onSuccess(); onClose(); }, 1200);
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Lưu sản phẩm thất bại'),
  });

  const deleteVariantMut = useMutation({
    mutationFn: (variantId: string) => sellerApi.deleteVariant(variantId),
    onSuccess: () => {
      if (product) {
        queryClient.invalidateQueries({ queryKey: ['seller-variants', product.productId] });
        queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      }
      setDeletingVariant(null);
      notify.success('Đã xoá biến thể');
    },
    onError: (err: any) => notify.error(err?.response?.data?.message || 'Xóa biến thể thất bại'),
  });

  const handleSaveInfo = () => {
    const priceNumber = Number(price);
    const stockNumber = Number(stock);
    const selectedCategoryId = category || categories[0]?.categoryId;
    if (!name.trim() || !Number.isFinite(priceNumber) || priceNumber <= 0) {
      setError('Vui lòng điền tên và giá sản phẩm.');
      return;
    }
    if (!selectedCategoryId) {
      setError('Vui lòng chọn danh mục sản phẩm.');
      return;
    }
    if (isNew && (!Number.isFinite(stockNumber) || stockNumber < 0)) {
      setError('Số lượng ban đầu không hợp lệ.');
      return;
    }
    setError(null);
    mut.mutate({ name: name.trim(), description, categoryId: selectedCategoryId, images });
  };

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">
            {product ? 'Cập nhật thành công!' : 'Tạo sản phẩm thành công!'}
          </h3>
          <p className="text-sm text-gray-500">Sản phẩm đã được lưu.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-lg w-full my-4 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-lg font-bold text-gray-900">
              {product ? '✏️ Chỉnh sửa sản phẩm' : '➕ Thêm sản phẩm mới'}
            </h3>
            {product && (
              <p className="text-xs text-gray-400 mt-0.5">ID: {product.productId}</p>
            )}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">×</button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-gray-100 mb-4">
          {(['info', 'images', 'variants', 'inventory'] as const).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>
              {tab === 'info' ? '📝 Thông tin' : tab === 'images' ? '🖼 Ảnh' : tab === 'variants' ? '🏷 Biến thể' : '📦 Kho'}
              {tab === 'variants' && variants.length > 0 && (
                <span className="ml-1 text-xs text-gray-400">({variants.length})</span>
              )}
            </button>
          ))}
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4 flex items-start gap-2">
            <span>⚠️</span>
            <span className="flex-1">{error}</span>
            <button onClick={() => setError(null)} className="text-red-500 hover:text-red-700 font-bold">×</button>
          </div>
        )}

        {/* Tab: Info */}
        {activeTab === 'info' && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên sản phẩm *</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)}
                placeholder="VD: Tai nghe Bluetooth Sony WH-1000XM5" autoFocus={!product}
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Mô tả</label>
              <textarea value={description} onChange={e => setDescription(e.target.value)} rows={4}
                placeholder="Mô tả chi tiết sản phẩm..."
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Danh mục</label>
                <select value={category} onChange={e => setCategory(e.target.value)}
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {categories.length === 0 && <option value="">Đang tải...</option>}
                  {categories.map(c => (
                    <option key={c.categoryId} value={c.categoryId}>{c.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá (VND) *</label>
                <div className="relative">
                  <input type="number" value={price} onChange={e => setPrice(e.target.value)} min="0"
                    className="w-full px-3 py-2.5 pr-7 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400">₫</span>
                </div>
              </div>
            </div>
            {isNew && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Số lượng ban đầu</label>
                <input type="number" value={stock} onChange={e => setStock(e.target.value)} min="0"
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            )}
            {!isNew && price && (
              <div className="bg-gray-50 rounded-xl p-3 text-sm">
                <div className="flex justify-between text-gray-500">
                  <span>Giá hiện tại</span>
                  <span className="font-semibold text-gray-900">{fmtVnd(Number(price))}</span>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab: Images */}
        {activeTab === 'images' && (
          <div className="min-h-[200px]">
            <ImageUploader productId={product?.productId ?? 'new'} images={images} onChange={setImages} />
          </div>
        )}

        {/* Tab: Variants */}
        {activeTab === 'variants' && (
          <div className="min-h-[200px]">
            {!product ? (
              <div className="flex flex-col items-center justify-center py-10 text-gray-400 text-sm gap-2">
                <span className="text-3xl">💾</span>
                <p>Lưu sản phẩm trước để thêm biến thể.</p>
              </div>
            ) : (
              <>
                <div className="space-y-2 mb-4 max-h-52 overflow-y-auto">
                  {variants.length === 0 && (
                    <p className="text-sm text-gray-400 text-center py-6">Chưa có biến thể nào. Thêm biến thể đầu tiên!</p>
                  )}
                  {variants.map(v => (
                    <div key={v.skuCode} className="flex items-center gap-3 p-3 border border-gray-100 rounded-xl hover:border-gray-200 transition-colors">
                      <div className={`w-2 h-8 rounded-full ${v.stock > 0 ? 'bg-green-400' : 'bg-red-400'}`} />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{v.variantName}</p>
                        <p className="text-xs text-gray-400">
                          SKU: {v.skuCode} · {fmtVnd(v.price)} · Kho: <span className={v.stock > 0 ? 'text-green-700' : 'text-red-600'}>{v.stock}</span>
                        </p>
                      </div>
                      <button onClick={() => { setShowVariant(v); setShowVariantForm(true); }}
                        className="px-3 py-1 text-xs text-blue-600 hover:bg-blue-50 rounded-lg font-medium transition-colors">Sửa</button>
                      <button onClick={() => setDeletingVariant(v)}
                        className="px-3 py-1 text-xs text-red-500 hover:bg-red-50 rounded-lg font-medium transition-colors">Xoá</button>
                    </div>
                  ))}
                </div>
                <button onClick={() => { setShowVariant(undefined); setShowVariantForm(true); }}
                  className="w-full py-2.5 border-2 border-dashed border-gray-300 rounded-xl text-sm text-gray-500 hover:border-gray-400 hover:text-gray-600 transition-colors font-medium">
                  + Thêm biến thể
                </button>
              </>
            )}
          </div>
        )}

        {/* Tab: Inventory */}
        {activeTab === 'inventory' && (
          <div className="min-h-[200px]">
            {!product ? (
              <div className="flex flex-col items-center justify-center py-10 text-gray-400 text-sm gap-2">
                <span className="text-3xl">💾</span>
                <p>Lưu sản phẩm trước để quản lý tồn kho.</p>
              </div>
            ) : (
              <InventoryPanel productId={product.productId} variants={variants} />
            )}
          </div>
        )}

        {/* Footer buttons */}
        <div className="flex gap-3 mt-6 pt-4 border-t border-gray-100">
          <button onClick={onClose}
            className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50 transition-colors">Huỷ</button>
          {activeTab === 'info' && (
            <button onClick={handleSaveInfo} disabled={mut.isPending}
              className="flex-1 py-2.5 bg-gradient-to-r from-blue-600 to-violet-600 text-white rounded-xl text-sm font-semibold hover:from-blue-700 hover:to-violet-700 disabled:opacity-50 transition-all">
              {mut.isPending ? '⏳ Đang lưu...' : product ? '💾 Cập nhật' : '✨ Tạo sản phẩm'}
            </button>
          )}
        </div>
      </div>

      {showVariantForm && product && (
        <VariantModal
          productId={product.productId}
          initial={showVariant}
          onClose={() => { setShowVariantForm(false); setShowVariant(undefined); }}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['seller-variants', product.productId] });
            queryClient.invalidateQueries({ queryKey: ['seller-products'] });
          }}
        />
      )}

      {deletingVariant && (
        <ConfirmDialog
          title="Xóa biến thể?"
          message={`Biến thể "${deletingVariant.variantName}" sẽ bị xóa khỏi sản phẩm.`}
          confirmLabel="Xóa"
          danger
          loading={deleteVariantMut.isPending}
          onConfirm={() => deleteVariantMut.mutate(deletingVariant.id)}
          onCancel={() => setDeletingVariant(null)}
        />
      )}
    </div>
  );
}
