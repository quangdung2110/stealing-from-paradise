import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, type AdminUser } from '@shared/api/admin.api';
import BanUserModal from '@/components/UserManagement/BanUserModal';
import UserFilters from '@/components/UserManagement/UserFilters';
import UsersTable from '@/components/UserManagement/UsersTable';
import Pagination from '@shared/components/Pagination';
import { EmptyState, Skeleton } from '@shared/components/ui';

export default function UserManagementPage() {
  const queryClient = useQueryClient();
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [banUser, setBanUser] = useState<AdminUser | null>(null);

  // Debounce ô tìm kiếm: chờ 400ms ngừng gõ mới gọi API, và nhảy về trang 1.
  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(t);
  }, [searchQuery]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-users', roleFilter, statusFilter, debouncedSearch, page],
    queryFn: () =>
      adminApi.getUsers({
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        search: debouncedSearch || undefined,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const users: AdminUser[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý người dùng</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && `${totalElements} tài khoản`}
          </p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-users'] })}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Filters */}
      <UserFilters
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        roleFilter={roleFilter}
        onRoleFilterChange={(r) => { setRoleFilter(r); setPage(0); }}
        statusFilter={statusFilter}
        onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
      />

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
          <div className="grid grid-cols-[80px_1.5fr_2fr_1fr_1fr_1fr_1fr] gap-4 border-b border-gray-100 bg-gray-50 px-5 py-3.5">
            {Array.from({ length: 7 }).map((_, i) => (
              <Skeleton key={i} className="h-3 w-full" />
            ))}
          </div>
          <div className="divide-y divide-gray-50">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="grid grid-cols-[80px_1.5fr_2fr_1fr_1fr_1fr_1fr] items-center gap-4 px-5 py-4">
                <Skeleton className="h-4 w-12" />
                <div className="flex items-center gap-3">
                  <Skeleton className="h-8 w-8 rounded-full" />
                  <Skeleton className="h-4 w-24" />
                </div>
                <Skeleton className="h-4 w-44" />
                <Skeleton className="h-6 w-16 rounded-full" />
                <Skeleton className="h-6 w-20 rounded-full" />
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-4 w-16" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải danh sách người dùng.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && users.length === 0 && (
        <EmptyState
          iconKey="users"
          title="Không có người dùng nào"
          description={
            debouncedSearch || roleFilter || statusFilter
              ? 'Thử đổi từ khóa hoặc bộ lọc để xem thêm tài khoản.'
              : 'Tài khoản mới sẽ xuất hiện ở đây sau khi người dùng đăng ký.'
          }
          className="bg-white rounded-2xl border border-gray-100"
        />
      )}

      {/* Table */}
      {!isLoading && !error && users.length > 0 && (
        <>
          <UsersTable
            users={users}
            onBanClick={setBanUser}
          />

          {/* Pagination */}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      {banUser && (
        <BanUserModal
          user={banUser}
          onClose={() => setBanUser(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-users'] })}
        />
      )}
    </div>
  );
}
