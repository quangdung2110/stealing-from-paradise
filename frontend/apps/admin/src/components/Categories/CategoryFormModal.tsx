import { useEffect } from 'react';
import type { Category } from '@shared/api/category.api';

interface CategoryFormModalProps {
  editingCategory: Category | null;
  name: string;
  slug: string;
  parentId: string;
  parentOptions: Category[];
  errorMsg: string;
  isPending: boolean;
  onNameChange: (val: string) => void;
  onSlugChange: (val: string) => void;
  onParentIdChange: (val: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  onClose: () => void;
}

export default function CategoryFormModal({
  editingCategory,
  name,
  slug,
  parentId,
  parentOptions,
  errorMsg,
  isPending,
  onNameChange,
  onSlugChange,
  onParentIdChange,
  onSubmit,
  onClose,
}: CategoryFormModalProps) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handler);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4 animate-fadeIn" role="dialog" aria-modal="true">
      <div className="bg-white rounded-2xl shadow-xl max-w-md w-full border border-gray-100 overflow-hidden transform transition-all animate-scaleUp">
        <div className="px-6 py-4 border-b border-gray-100 bg-gray-50 flex items-center justify-between">
          <h3 className="text-lg font-bold text-gray-900">
            {editingCategory ? 'Chỉnh sửa danh mục' : 'Thêm danh mục mới'}
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            ✕
          </button>
        </div>

        <form onSubmit={onSubmit} className="p-6 space-y-4">
          {errorMsg && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-4 py-2.5 rounded-xl flex items-center gap-2">
              <span>⚠️</span>
              <span>{errorMsg}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Tên danh mục <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => onNameChange(e.target.value)}
              placeholder="Ví dụ: Thiết bị điện tử"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Slug <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={slug}
              onChange={(e) => onSlugChange(e.target.value)}
              placeholder="Ví dụ: thiet-bi-dien-tu"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">
              Danh mục cha (Không bắt buộc)
            </label>
            <select
              value={parentId}
              onChange={(e) => onParentIdChange(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all bg-white"
            >
              <option value="">-- Chọn danh mục cha --</option>
              {parentOptions.map((opt) => (
                <option key={opt.categoryId} value={opt.categoryId}>
                  {opt.level > 1 ? '— '.repeat(opt.level - 1) : ''}
                  {opt.name}
                </option>
              ))}
            </select>
          </div>

          <div className="flex gap-3 pt-4 border-t border-gray-100">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 border border-gray-200 rounded-xl text-sm font-medium hover:bg-gray-50 text-gray-700 transition-all"
            >
              Huỷ
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="flex-1 py-2.5 text-white bg-blue-600 hover:bg-blue-700 rounded-xl text-sm font-medium disabled:opacity-50 transition-all shadow-sm"
            >
              {isPending ? 'Đang lưu...' : 'Lưu'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
