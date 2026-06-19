import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bannerApi, uploadBannerImage, type Banner, type BannerPosition, type BannerRequest } from '@shared/api/banner.api';
import ConfirmDialog from '@shared/components/ConfirmDialog';

const POSITIONS: { value: BannerPosition; label: string }[] = [
  { value: 'HERO', label: 'Hero (đầu trang)' },
  { value: 'SIDEBAR', label: 'Sidebar' },
  { value: 'POPUP', label: 'Popup' },
];

const emptyForm: BannerRequest = { title: '', imageUrl: '', position: 'HERO', active: true, startsAt: null, endsAt: null };

// "2026-06-14T10:00:00Z" ↔ "2026-06-14T10:00" for datetime-local inputs.
const toLocal = (iso?: string | null) => (iso ? iso.slice(0, 16) : '');
const fromLocal = (v: string) => (v ? new Date(v).toISOString() : null);

export default function BannerManagementPage() {
  const queryClient = useQueryClient();
  const [isOpen, setIsOpen] = useState(false);
  const [editing, setEditing] = useState<Banner | null>(null);
  const [form, setForm] = useState<BannerRequest>(emptyForm);
  const [errorMsg, setErrorMsg] = useState('');
  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState<Banner | null>(null);

  const { data: banners = [], isLoading, error } = useQuery({
    queryKey: ['admin-banners'],
    queryFn: () => bannerApi.list().then((r) => r.data.data ?? []),
  });

  useEffect(() => {
    if (isOpen) {
      const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setIsOpen(false); };
      document.addEventListener('keydown', handler);
      document.body.style.overflow = 'hidden';
      return () => {
        document.removeEventListener('keydown', handler);
        document.body.style.overflow = '';
      };
    }
  }, [isOpen]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-banners'] });

  const saveMut = useMutation({
    mutationFn: () => (editing ? bannerApi.update(editing.id, form) : bannerApi.create(form)),
    onSuccess: () => { invalidate(); setIsOpen(false); },
    onError: (err: any) => setErrorMsg(err?.response?.data?.message || 'Lưu banner thất bại'),
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => bannerApi.remove(id),
    onSuccess: () => { invalidate(); setDeleting(null); },
  });

  const openCreate = () => { setEditing(null); setForm(emptyForm); setErrorMsg(''); setIsOpen(true); };
  const openEdit = (b: Banner) => {
    setEditing(b);
    setForm({ title: b.title, imageUrl: b.imageUrl, position: b.position, active: b.active, startsAt: b.startsAt ?? null, endsAt: b.endsAt ?? null });
    setErrorMsg('');
    setIsOpen(true);
  };

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true); setErrorMsg('');
    try {
      const url = await uploadBannerImage(file);
      setForm((f) => ({ ...f, imageUrl: url }));
    } catch {
      setErrorMsg('Tải ảnh thất bại. Bạn có thể dán URL ảnh thay thế.');
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = () => {
    if (!form.title.trim() || !form.imageUrl.trim()) { setErrorMsg('Vui lòng nhập tiêu đề và ảnh banner.'); return; }
    if (form.startsAt && form.endsAt && new Date(form.endsAt) < new Date(form.startsAt)) {
      setErrorMsg('Thời gian kết thúc phải sau thời gian bắt đầu.'); return;
    }
    setErrorMsg(''); saveMut.mutate();
  };

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý Banner</h1>
          <p className="text-sm text-gray-500 mt-1">Ảnh quảng cáo hiển thị trên trang khách hàng</p>
        </div>
        <button onClick={openCreate} className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-colors">
          + Thêm banner
        </button>
      </div>

      {error && <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm">Không thể tải danh sách banner.</div>}

      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <div className="aspect-[3/1] bg-gray-200 animate-pulse" />
              <div className="p-4 space-y-2"><div className="h-4 bg-gray-200 rounded w-2/3 animate-pulse" /></div>
            </div>
          ))}
        </div>
      ) : banners.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-2xl border border-gray-100">
          <div className="text-5xl mb-3">🖼️</div>
          <h3 className="font-semibold text-gray-900 mb-1">Chưa có banner nào</h3>
          <p className="text-sm text-gray-500 mb-5">Thêm banner để hiển thị trên trang chủ khách hàng.</p>
          <button onClick={openCreate} className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-colors">+ Thêm banner đầu tiên</button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {banners.map((b) => (
            <div key={b.id} className="bg-white rounded-2xl border border-gray-100 overflow-hidden flex flex-col">
              <div className="relative aspect-[3/1] bg-gray-100">
                <img src={b.imageUrl} alt={b.title} className="w-full h-full object-cover" />
                <span className={`absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-semibold ${b.active ? 'bg-green-500 text-white' : 'bg-gray-400 text-white'}`}>
                  {b.active ? 'Đang bật' : 'Đã tắt'}
                </span>
              </div>
              <div className="p-4 flex flex-col flex-1">
                <h3 className="font-semibold text-gray-900 line-clamp-1">{b.title}</h3>
                {(b.startsAt || b.endsAt) && (
                  <p className="text-xs text-gray-400 mt-1">
                    Lịch: {b.startsAt ? new Date(b.startsAt).toLocaleDateString('vi-VN') : '—'} → {b.endsAt ? new Date(b.endsAt).toLocaleDateString('vi-VN') : '—'}
                  </p>
                )}
                <div className="flex gap-2 mt-3 pt-3 border-t border-gray-50">
                  <button onClick={() => openEdit(b)} className="flex-1 py-1.5 text-xs font-semibold rounded-lg border border-blue-200 text-blue-600 hover:bg-blue-50 transition-colors">Sửa</button>
                  <button onClick={() => setDeleting(b)} className="flex-1 py-1.5 text-xs font-semibold rounded-lg border border-red-200 text-red-500 hover:bg-red-50 transition-colors">Xoá</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" onClick={() => setIsOpen(false)}>
          <div className="bg-white rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto p-6" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-gray-900 mb-4">{editing ? 'Chỉnh sửa banner' : 'Thêm banner mới'}</h2>
            {errorMsg && <div className="mb-4 p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm">{errorMsg}</div>}

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Tiêu đề</label>
                <input type="text" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="vd: Siêu Sale Mùa Hè" className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Ảnh banner</label>
                {form.imageUrl && <img src={form.imageUrl} alt="preview" className="w-full aspect-[3/1] object-cover rounded-xl mb-2 border border-gray-100" />}
                <div className="flex gap-2 items-center">
                  <label className="px-3 py-2 text-sm font-medium rounded-xl border border-gray-200 text-gray-700 hover:bg-gray-50 cursor-pointer transition-colors whitespace-nowrap">
                    {uploading ? 'Đang tải...' : '📁 Tải ảnh lên'}
                    <input type="file" accept="image/*" className="hidden" disabled={uploading} onChange={(e) => handleFile(e.target.files?.[0])} />
                  </label>
                  <span className="text-xs text-gray-400">hoặc</span>
                  <input type="text" value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                    placeholder="Dán URL ảnh..." className="flex-1 min-w-0 px-3 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Vị trí</label>
                <select value={form.position} onChange={(e) => setForm({ ...form, position: e.target.value as BannerPosition })}
                  className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {POSITIONS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Bắt đầu (tuỳ chọn)</label>
                  <input type="datetime-local" value={toLocal(form.startsAt)} onChange={(e) => setForm({ ...form, startsAt: fromLocal(e.target.value) })}
                    className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Kết thúc (tuỳ chọn)</label>
                  <input type="datetime-local" value={toLocal(form.endsAt)} onChange={(e) => setForm({ ...form, endsAt: fromLocal(e.target.value) })}
                    className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>

              <label className="flex items-center gap-2 text-sm text-gray-700">
                <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="w-4 h-4 rounded" />
                Bật banner ngay
              </label>
            </div>

            <div className="flex gap-3 mt-6">
              <button onClick={handleSubmit} disabled={saveMut.isPending || uploading}
                className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-colors disabled:opacity-50">
                {saveMut.isPending ? 'Đang lưu...' : editing ? 'Cập nhật' : 'Tạo banner'}
              </button>
              <button onClick={() => setIsOpen(false)} className="px-5 py-2.5 border border-gray-200 text-gray-700 font-semibold text-sm rounded-xl hover:bg-gray-50 transition-colors">Huỷ</button>
            </div>
          </div>
        </div>
      )}

      {deleting && (
        <ConfirmDialog
          title="Xoá banner?"
          message={`Bạn chắc chắn muốn xoá banner "${deleting.title}"?`}
          confirmLabel="Xoá"
          danger
          onConfirm={() => deleteMut.mutate(deleting.id)}
          onCancel={() => setDeleting(null)}
          loading={deleteMut.isPending}
        />
      )}
    </div>
  );
}
