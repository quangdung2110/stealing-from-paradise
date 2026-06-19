import { type FlashSaleSession } from '@shared/api/flashSale.api';

const STATUS_COLORS: Record<string, string> = {
  UPCOMING:  'bg-blue-100 text-blue-700',
  ACTIVE:    'bg-green-100 text-green-700',
  ENDED:     'bg-gray-100 text-gray-600',
  CANCELLED: 'bg-red-100 text-red-700',
};

function formatDateTime(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

interface FlashSaleSessionsTableProps {
  sessions: FlashSaleSession[];
  onEdit: (session: FlashSaleSession) => void;
  onDelete: (session: FlashSaleSession) => void;
}

export default function FlashSaleSessionsTable({
  sessions,
  onEdit,
  onDelete,
}: FlashSaleSessionsTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left">
          <thead className="bg-gray-50 border-b border-gray-100">
            <tr>
              {['Tên phiên', 'Bắt đầu', 'Kết thúc', 'Sản phẩm', 'Trạng thái', 'Thao tác'].map((h) => (
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
            {sessions.map((s) => (
              <tr key={s.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                <td className="px-5 py-4 font-medium text-gray-900">{s.name}</td>
                <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{formatDateTime(s.startTime)}</td>
                <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{formatDateTime(s.endTime)}</td>
                <td className="px-5 py-4 text-gray-700">{s.items?.length ?? 0} sản phẩm</td>
                <td className="px-5 py-4">
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                      STATUS_COLORS[s.status] ?? 'bg-gray-100 text-gray-600'
                    }`}
                  >
                    {s.status === 'UPCOMING' ? 'Sắp diễn ra' : s.status === 'ACTIVE' ? 'Đang chạy' : s.status === 'CANCELLED' ? 'Đã huỷ' : 'Đã kết thúc'}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <div className="flex gap-2">
                    {s.status === 'UPCOMING' && (
                      <button
                        onClick={() => onEdit(s)}
                        className="text-xs text-blue-600 hover:text-blue-700 font-medium"
                      >
                        Chỉnh sửa
                      </button>
                    )}
                    {s.status !== 'ACTIVE' && (
                      <button
                        onClick={() => onDelete(s)}
                        disabled={s.status === 'ACTIVE'}
                        className="text-xs text-red-500 hover:text-red-600 font-medium disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        {s.status === 'CANCELLED' ? 'Đã xoá' : 'Xoá'}
                      </button>
                    )}
                    {s.status === 'ACTIVE' && (
                      <span className="text-xs text-gray-400 italic cursor-not-allowed">Đang chạy</span>
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
