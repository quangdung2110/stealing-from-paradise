import { type PendingProduct } from '@shared/api/admin.api';
import { fmtVnd } from '@shared/utils/format';

const STATUS_COLORS: Record<string, string> = {
  PENDING:  'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
};

interface ProductModerationListProps {
  pendingProducts: PendingProduct[];
  onApprove: (p: PendingProduct) => void;
  onReject: (p: PendingProduct) => void;
}

export default function ProductModerationList({
  pendingProducts,
  onApprove,
  onReject,
}: ProductModerationListProps) {
  return (
    <div className="space-y-4">
      {pendingProducts.map((p) => (
        <div
          key={p.productId}
          className="bg-white rounded-2xl border border-gray-100 p-5 flex items-start gap-4 hover:shadow-sm transition-shadow"
        >
          <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
            {p.images?.[0] ? (
              <img src={p.images[0]} alt="" className="w-full h-full object-cover" />
            ) : (
              '🛍️'
            )}
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1 flex-wrap">
              <h3 className="font-semibold text-gray-900">{p.name}</h3>
              <span
                className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                  STATUS_COLORS[p.status] ?? 'bg-gray-100 text-gray-600'
                }`}
              >
                {p.status === 'PENDING' ? 'Chờ duyệt' : p.status === 'APPROVED' ? 'Đã duyệt' : 'Từ chối'}
              </span>
            </div>
            <p className="text-sm text-gray-500">
              Người bán: #{p.sellerId} {p.sellerName && `(${p.sellerName})`}
              {p.category && ` · ${p.category}`}
            </p>
            {p.price && <p className="font-bold text-gray-900 mt-1">{fmtVnd(p.price)}</p>}
            {p.description && (
              <p className="text-sm text-gray-600 mt-1 line-clamp-2">{p.description}</p>
            )}
          </div>
          {p.status === 'PENDING' && (
            <div className="flex flex-col gap-2 shrink-0">
              <button
                onClick={() => onApprove(p)}
                className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-xl transition-colors whitespace-nowrap"
              >
                ✓ Duyệt
              </button>
              <button
                onClick={() => onReject(p)}
                className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 text-sm font-medium rounded-xl transition-colors border border-red-200 whitespace-nowrap"
              >
                ✗ Từ chối
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
