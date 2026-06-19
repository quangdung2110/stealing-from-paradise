import { type RefundResponse } from '@shared/api/refund.api';
import { fmtVnd, fmtDate } from '@shared/utils/format';

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:  { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ duyệt' },
  SUCCESS:  { bg: 'bg-green-100',  color: 'text-green-700', label: 'Đã hoàn' },
  FAILED:   { bg: 'bg-red-100',    color: 'text-red-700',   label: 'Thất bại' },
  REJECTED: { bg: 'bg-gray-100',   color: 'text-gray-600',  label: 'Từ chối' },
};

function formatDate(iso?: string) {
  return fmtDate(iso, true);
}

interface RefundsTableProps {
  refunds: RefundResponse[];
  onDetail: (refund: RefundResponse) => void;
  onApprove: (refund: RefundResponse) => void;
  onReject: (refund: RefundResponse) => void;
}

export default function RefundsTable({
  refunds,
  onDetail,
  onApprove,
  onReject,
}: RefundsTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
      <div className="max-h-[70vh] overflow-auto">
        <table className="w-full text-sm text-left">
          <thead className="sticky top-0 z-10 bg-gray-50/95 backdrop-blur border-b border-gray-100">
            <tr>
              {['Mã YC', 'Đơn hàng', 'Loại', 'Số tiền', 'Người YC', 'Lý do', 'Trạng thái', 'Ngày', 'Thao tác'].map((h) => (
                <th
                  key={h}
                  className="px-4 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {refunds.map((r) => {
              const st = STATUS_STYLE[r.status] ?? { bg: 'bg-gray-100', color: 'text-gray-600', label: r.status };
              return (
                <tr key={r.refundId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                  <td className="px-4 py-4 font-mono text-gray-900">#{r.refundId}</td>
                  <td className="px-4 py-4 font-mono text-gray-500">#{r.orderId}</td>
                  <td className="px-4 py-4">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                        r.type === 'FULL' ? 'bg-blue-50 text-blue-700' : 'bg-purple-50 text-purple-700'
                      }`}
                    >
                      {r.type === 'FULL' ? 'Toàn bộ' : 'Một phần'}
                    </span>
                  </td>
                  <td className="px-4 py-4 font-semibold text-gray-900">{fmtVnd(r.amount)}</td>
                  <td className="px-4 py-4 text-gray-700">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                        r.initiatedBy === 'BUYER' ? 'bg-green-50 text-green-700' : 'bg-orange-50 text-orange-700'
                      }`}
                    >
                      {r.initiatedBy === 'BUYER' ? 'Khách' : 'Người bán'}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-gray-500 max-w-[200px] truncate" title={r.reason}>
                    {r.reason}
                  </td>
                  <td className="px-4 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.bg} ${st.color}`}>
                      {st.label}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-gray-400 whitespace-nowrap text-xs">{formatDate(r.createdAt)}</td>
                  <td className="px-4 py-4">
                    <div className="flex gap-2 flex-wrap">
                      <button
                        onClick={() => onDetail(r)}
                        className="text-xs text-gray-500 hover:text-gray-700 font-medium"
                      >
                        Chi tiết
                      </button>
                      {r.status === 'PENDING' && (
                        <>
                          <button
                            onClick={() => onApprove(r)}
                            className="text-xs text-green-600 hover:text-green-700 font-medium"
                          >
                            Duyệt
                          </button>
                          <button
                            onClick={() => onReject(r)}
                            className="text-xs text-red-500 hover:text-red-600 font-medium"
                          >
                            Từ chối
                          </button>
                        </>
                      )}
                      {r.status === 'FAILED' && (
                        <button
                          onClick={() => onApprove(r)}
                          className="text-xs text-green-600 hover:text-green-700 font-medium"
                        >
                          Retry
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
