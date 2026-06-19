import { type FlashSaleSession } from '@shared/api/flashSale.api';

interface FlashSaleSessionFormProps {
  editingSession: FlashSaleSession | null;
  name: string;
  startTime: string;
  endTime: string;
  formError: string | null;
  isMutating: boolean;
  onNameChange: (val: string) => void;
  onStartTimeChange: (val: string) => void;
  onEndTimeChange: (val: string) => void;
  onSubmit: () => void;
  onCancel: () => void;
}

export default function FlashSaleSessionForm({
  editingSession,
  name,
  startTime,
  endTime,
  formError,
  isMutating,
  onNameChange,
  onStartTimeChange,
  onEndTimeChange,
  onSubmit,
  onCancel,
}: FlashSaleSessionFormProps) {
  return (
    <div className="bg-white rounded-2xl border border-violet-100 p-6 mb-6 shadow-sm">
      <h2 className="font-bold text-gray-900 mb-4">
        {editingSession ? `Chỉnh sửa: ${editingSession.name}` : 'Tạo phiên Flash Sale mới'}
      </h2>
      {formError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">
          {formError}
        </div>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="sm:col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên phiên</label>
          <input
            type="text"
            value={name}
            onChange={(e) => onNameChange(e.target.value)}
            placeholder="vd: Flash Sale 18:00 Thứ Sáu"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian bắt đầu</label>
          <input
            type="datetime-local"
            value={startTime}
            onChange={(e) => onStartTimeChange(e.target.value)}
            min={new Date().toISOString().slice(0, 16)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian kết thúc</label>
          <input
            type="datetime-local"
            value={endTime}
            onChange={(e) => onEndTimeChange(e.target.value)}
            min={startTime || new Date().toISOString().slice(0, 16)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
          />
          <p className="text-xs text-gray-400 mt-1">Thời gian theo múi giờ địa phương (UTC+7)</p>
        </div>
      </div>
      <div className="flex gap-3 mt-5">
        <button
          onClick={onSubmit}
          disabled={isMutating}
          className="px-5 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors disabled:opacity-50"
        >
          {isMutating ? 'Đang xử lý...' : editingSession ? 'Cập nhật' : 'Tạo phiên'}
        </button>
        <button
          onClick={onCancel}
          className="px-5 py-2.5 border border-gray-200 text-gray-700 font-semibold text-sm rounded-xl hover:bg-gray-50 transition-colors"
        >
          Huỷ
        </button>
      </div>
    </div>
  );
}
