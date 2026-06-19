import { type SellerProduct } from '@shared/api/seller.api';
import { fmtVnd } from '@shared/utils/format';
import { ProductStatusBadge } from '@/lib/productStatus';
import { canSubmit, canPublish, canUnpublish, canDelete } from '@/lib/productActions';

interface ProductsTableProps {
  products: SellerProduct[];
  onEdit: (product: SellerProduct) => void;
  onInventory: (product: SellerProduct) => void;
  onSubmitForReview: (productId: string) => void;
  onPublish: (productId: string) => void;
  onUnpublish: (productId: string) => void;
  onDelete: (product: SellerProduct) => void;
  submitPending: boolean;
  publishPending: boolean;
  unpublishPending: boolean;
  deletePending: boolean;
}

export default function ProductsTable({
  products,
  onEdit,
  onInventory,
  onSubmitForReview,
  onPublish,
  onUnpublish,
  onDelete,
  submitPending,
  publishPending,
  unpublishPending,
  deletePending,
}: ProductsTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 border-b border-gray-100">
            <tr>
              {['Sản phẩm', 'Danh mục', 'Giá', 'Tồn kho', 'Trạng thái', 'Ngày tạo', 'Thao tác'].map((h) => (
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
            {products.map((p) => (
              <tr key={p.productId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-xl bg-gray-100 flex items-center justify-center text-xl shrink-0 overflow-hidden">
                      {p.images?.[0] ? (
                        <img src={p.images[0]} alt="" className="w-full h-full object-cover" />
                      ) : (
                        '📦'
                      )}
                    </div>
                    <div>
                      <p className="font-medium text-gray-900 line-clamp-1 max-w-[200px]">{p.name}</p>
                      {p.variantsCount > 0 && <p className="text-xs text-gray-400">{p.variantsCount} biến thể</p>}
                    </div>
                  </div>
                </td>
                <td className="px-5 py-4 text-gray-500 capitalize">{p.categoryName || p.category || '—'}</td>
                <td className="px-5 py-4 font-semibold text-gray-900">{p.price ? fmtVnd(p.price) : '—'}</td>
                <td className="px-5 py-4">
                  <span className={`font-medium ${p.stockAvailable > 0 ? (p.stockAvailable < 10 ? 'text-orange-600' : 'text-green-700') : 'text-red-600'}`}>
                    {p.stockAvailable}
                  </span>
                  {p.stockAvailable > 0 && p.stockAvailable < 10 && (
                    <span className="ml-1 text-[10px] text-orange-500">(sắp hết)</span>
                  )}
                </td>
                <td className="px-5 py-4">
                  <ProductStatusBadge status={p.status} />
                </td>
                <td className="px-5 py-4 text-gray-400 whitespace-nowrap text-xs">
                  {p.createdAt
                    ? new Date(p.createdAt).toLocaleDateString('vi-VN', {
                        day: '2-digit', month: '2-digit', year: 'numeric',
                      })
                    : '—'}
                </td>
                <td className="px-5 py-4">
                  <div className="flex gap-2 flex-wrap">
                    <button onClick={() => onEdit(p)}
                      className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 font-medium">
                      ✏️ Sửa
                    </button>
                    <button onClick={() => onInventory(p)}
                      className="inline-flex items-center gap-1 text-xs text-violet-600 hover:text-violet-700 font-medium">
                      📊 Kho
                    </button>
                    {canSubmit(p.status) && (
                      <button onClick={() => onSubmitForReview(p.productId)} disabled={submitPending}
                        className="inline-flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                        {submitPending ? '...' : '📨 Gửi duyệt'}
                      </button>
                    )}
                    {canPublish(p.status) && (
                      <button onClick={() => onPublish(p.productId)} disabled={publishPending}
                        className="inline-flex items-center gap-1 text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                        {publishPending ? '...' : '🌐 Hiển thị'}
                      </button>
                    )}
                    {canUnpublish(p.status) && (
                      <button onClick={() => onUnpublish(p.productId)} disabled={unpublishPending}
                        className="inline-flex items-center gap-1 text-xs text-orange-600 hover:text-orange-700 font-medium disabled:opacity-50">
                        {unpublishPending ? '...' : '🙈 Ẩn'}
                      </button>
                    )}
                    {canDelete(p.status) && (
                      <button onClick={() => onDelete(p)} disabled={deletePending}
                        className="inline-flex items-center gap-1 text-xs text-red-500 hover:text-red-600 font-medium disabled:opacity-50">
                        {deletePending ? '...' : '🗑 Xóa'}
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
