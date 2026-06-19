import { type AdminUser } from '@shared/api/admin.api';
import { fmtDate } from '@shared/utils/format';

const ROLE_COLORS: Record<string, string> = {
  ADMIN:  'bg-red-100 text-red-700',
  SELLER: 'bg-purple-100 text-purple-700',
  BUYER:  'bg-blue-100 text-blue-700',
};

const STATUS_COLORS: Record<string, string> = {
  ACTIVE:  'bg-green-100 text-green-700',
  BANNED:  'bg-red-100 text-red-700',
  LOCKED:  'bg-red-100 text-red-700',
  PENDING: 'bg-yellow-100 text-yellow-700',
};

const isLocked = (status: string) => status === 'BANNED' || status === 'LOCKED';

interface UsersTableProps {
  users: AdminUser[];
  onBanClick: (user: AdminUser) => void;
  onViewClick?: (user: AdminUser) => void;
}

export default function UsersTable({
  users,
  onBanClick,
  onViewClick,
}: UsersTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
      <div className="max-h-[70vh] overflow-auto">
        <table className="w-full text-sm text-left">
          <thead className="sticky top-0 z-10 bg-gray-50/95 backdrop-blur border-b border-gray-100">
            <tr>
              {['#', 'Người dùng', 'Email', 'Vai trò', 'Trạng thái', 'Ngày tạo', 'Thao tác'].map((h) => (
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
            {users.map((u) => (
              <tr key={u.userId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                <td className="px-5 py-4 text-gray-400 font-mono text-xs">{u.userId}</td>
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold uppercase shrink-0">
                      {u.username.charAt(0)}
                    </div>
                    <span className="font-medium text-gray-900">{u.username}</span>
                  </div>
                </td>
                <td className="px-5 py-4 text-gray-500">{u.email}</td>
                <td className="px-5 py-4">
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                      ROLE_COLORS[u.role] ?? 'bg-gray-100 text-gray-600'
                    }`}
                  >
                    {u.role}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                      STATUS_COLORS[u.status] ?? 'bg-gray-100 text-gray-600'
                    }`}
                  >
                    {u.status}
                  </span>
                </td>
                <td className="px-5 py-4 text-gray-400 whitespace-nowrap text-xs">{fmtDate(u.createdAt)}</td>
                <td className="px-5 py-4">
                  {u.role !== 'ADMIN' && (
                    <div className="flex gap-2">
                      <button
                        onClick={() => onViewClick?.(u)}
                        className="text-xs text-blue-600 hover:text-blue-700 font-medium"
                      >
                        Xem
                      </button>
                      <button
                        onClick={() => onBanClick(u)}
                        className={`text-xs font-medium ${
                          isLocked(u.status) ? 'text-green-600 hover:text-green-700' : 'text-red-500 hover:text-red-600'
                        }`}
                      >
                        {isLocked(u.status) ? 'Mở khoá' : 'Khoá'}
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
