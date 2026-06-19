import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, type AdminUser } from '@shared/api/admin.api';

const isLocked = (status: string) => status === 'BANNED' || status === 'LOCKED';

export default function BanUserModal({ user, onClose, onSuccess }: { user: AdminUser; onClose: () => void; onSuccess: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const mut = useMutation({
    mutationFn: () => {
      if (isLocked(user.status)) {
        return adminApi.unlockUser(user.userId, 'Mở khoá tài khoản bởi Admin');
      } else {
        return adminApi.lockUser(user.userId, 'Khoá tài khoản bởi Admin');
      }
    },
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Thao tác thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
        <div className="text-5xl mb-4">{isLocked(user.status) ? '🔓' : '🔒'}</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">
          {isLocked(user.status) ? 'Mở khoá tài khoản?' : 'Khoá tài khoản?'}
        </h3>
        <p className="text-sm text-gray-500 mb-6">
          {isLocked(user.status)
            ? `Mở khoá tài khoản "${user.username}" để họ có thể tiếp tục sử dụng.`
            : `Khoá tài khoản "${user.username}" sẽ không cho phép họ đăng nhập.`}
        </p>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-4 py-2.5 rounded-xl mb-4">{error}</div>
        )}
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className={`flex-1 py-2.5 text-white rounded-xl text-sm font-medium ${
              isLocked(user.status) ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700'
            } disabled:opacity-50`}
          >
            {mut.isPending ? 'Đang xử lý...' : isLocked(user.status) ? 'Mở khoá' : 'Khoá'}
          </button>
        </div>
      </div>
    </div>
  );
}
