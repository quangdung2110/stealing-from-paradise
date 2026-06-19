import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import apiClient from '@shared/lib/axios';
import { sellerApi, type SellerProduct } from '@shared/api/seller.api';
import type { ApiResponse, PageResponse } from '@shared/types/api';
import ProductFormModal from '@/components/ProductManagement/ProductFormModal';
import ProductTabs from '@/components/ProductManagement/ProductTabs';
import ProductsTable from '@/components/ProductManagement/ProductsTable';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import Pagination from '@shared/components/Pagination';
import { Skeleton, EmptyState, Button } from '@shared/components/ui';

type ProductFormTab = 'info' | 'images' | 'variants' | 'inventory';

export default function ProductManagementPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editProduct, setEditProduct] = useState<SellerProduct | undefined>(undefined);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [deletingProduct, setDeletingProduct] = useState<SellerProduct | null>(null);
  const [formInitialTab, setFormInitialTab] = useState<ProductFormTab>('info');

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-products', statusFilter, page, debouncedSearch],
    queryFn: () =>
      apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
        params: { status: statusFilter || undefined, page, size: 20, search: debouncedSearch || undefined },
      }).then(r => {
        const res = r.data.data;
        if (!res) {
          return { content: [], totalElements: 0, totalPages: 0, last: true };
        }
        return {
          ...res,
          content: (res.content || []).map((p: any) => ({
            ...p,
            productId: p.id,
            stockAvailable: p.totalStock ?? 0,
            images: p.thumbnailUrl ? [p.thumbnailUrl] : [],
          })),
        };
      }),
    retry: 1,
  });

  const products: SellerProduct[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  // Count low-stock products
  const lowStockProducts = products.filter(p => p.stockAvailable > 0 && p.stockAvailable < 10).length;

  // Action mutations
  const submitMut = useMutation({ mutationFn: sellerApi.submitForReview, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const publishMut = useMutation({ mutationFn: sellerApi.publishProduct, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const unpublishMut = useMutation({ mutationFn: sellerApi.unpublishProduct, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const deleteMut = useMutation({
    mutationFn: sellerApi.deleteProduct,
    onSuccess: () => { setDeletingProduct(null); queryClient.invalidateQueries({ queryKey: ['seller-products'] }); },
  });

  const handleEdit = (product: SellerProduct) => {
    setEditProduct(product);
    setFormInitialTab('info');
    setShowForm(true);
  };
  const handleInventory = (product: SellerProduct) => {
    setEditProduct(product);
    setFormInitialTab('inventory');
    setShowForm(true);
  };
  const handleAdd = () => {
    setEditProduct(undefined);
    setFormInitialTab('info');
    setShowForm(true);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">📦 Sản phẩm của tôi</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && <span>{totalElements} sản phẩm · </span>}
            Quản lý danh sách sản phẩm đang bán
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate('/flash-sales')}
            className="inline-flex items-center gap-1.5 px-3 py-2 border border-violet-200 rounded-xl text-sm text-violet-700 hover:bg-violet-50 transition-colors font-medium"
          >
            ⚡ Flash Sale
          </button>
          <button
            onClick={() => queryClient.invalidateQueries({ queryKey: ['seller-products'] })}
            className="px-3 py-2 border rounded-xl text-sm text-gray-600 hover:bg-gray-50"
          >
            🔄 Làm mới
          </button>
          <Button onClick={handleAdd}>
            + Thêm sản phẩm
          </Button>
        </div>
      </div>

      {/* Low stock alert */}
      {!isLoading && lowStockProducts > 0 && (
        <div className="mb-4 bg-orange-50 border border-orange-200 rounded-xl p-3 text-orange-800 text-sm flex items-center gap-2">
          <span>⚠️</span>
          <span>
            <strong>{lowStockProducts} sản phẩm</strong> sắp hết hàng (tồn &lt; 10). Nhấn <strong>"Kho"</strong> để nhập thêm.
          </span>
        </div>
      )}

      {/* Filters */}
      <ProductTabs
        statusFilter={statusFilter}
        onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
        searchQuery={searchQuery}
        onSearchQueryChange={(q) => { setSearchQuery(q); setPage(0); }}
      />

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden divide-y divide-gray-50">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4 px-5 py-4">
              <Skeleton className="h-12 w-12 rounded-xl" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-3 w-1/4" />
              </div>
              <Skeleton className="h-6 w-16 rounded-full" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      )}

      {/* Error */}
      {error && !isLoading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải sản phẩm. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && products.length === 0 && (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <div className="w-20 h-20 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-5 text-4xl">📦</div>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">
            {searchQuery || statusFilter ? 'Không tìm thấy sản phẩm' : 'Chưa có sản phẩm nào'}
          </h3>
          <p className="text-gray-500 text-sm mb-6 max-w-sm mx-auto">
            {searchQuery || statusFilter
              ? 'Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.'
              : 'Hãy thêm sản phẩm đầu tiên để bắt đầu bán hàng trên nền tảng'}
          </p>
          {!searchQuery && !statusFilter && (
            <Button onClick={handleAdd}>Thêm sản phẩm đầu tiên</Button>
          )}
        </div>
      )}

      {/* Table */}
      {!isLoading && !error && products.length > 0 && (
        <>
          <ProductsTable
            products={products}
            onEdit={handleEdit}
            onInventory={handleInventory}
            onSubmitForReview={(id) => submitMut.mutate(id)}
            onPublish={(id) => publishMut.mutate(id)}
            onUnpublish={(id) => unpublishMut.mutate(id)}
            onDelete={setDeletingProduct}
            submitPending={submitMut.isPending}
            publishPending={publishMut.isPending}
            unpublishPending={unpublishMut.isPending}
            deletePending={deleteMut.isPending}
          />
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}

      {/* Form modal */}
      {showForm && (
        <ProductFormModal
          product={editProduct}
          initialTab={formInitialTab}
          onClose={() => setShowForm(false)}
          onSuccess={() => {}}
        />
      )}

      {/* Delete confirmation */}
      {deletingProduct && (
        <ConfirmDialog
          title="Xóa sản phẩm?"
          message={`Bạn có chắc chắn muốn xóa sản phẩm "${deletingProduct.name}"? Hành động này không thể hoàn tác.`}
          confirmLabel="Xóa"
          danger
          loading={deleteMut.isPending}
          onConfirm={() => deleteMut.mutate(deletingProduct.productId)}
          onCancel={() => setDeletingProduct(null)}
        />
      )}
    </div>
  );
}
