import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi, type UserProfileResponse } from '@shared/api/user.api';
import { Skeleton, Spinner } from '@shared/components/ui';

function StatusBadge({ status }: { status: string }) {
  const colors =
    status === 'ACTIVE'  ? 'bg-green-100 text-green-700' :
    status === 'LOCKED'   ? 'bg-red-100 text-red-700' :
    status === 'BANNED'  ? 'bg-red-100 text-red-700' :
                            'bg-gray-100 text-gray-600';
  return <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${colors}`}>{status}</span>;
}

function AvatarUpload({
  currentAvatar,
  username,
  onUploadSuccess,
}: {
  currentAvatar?: string;
  username: string;
  onUploadSuccess: (cdnUrl: string) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(currentAvatar ?? null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('Vui lòng chọn file hình ảnh');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Kích thước file không được vượt quá 5MB');
      return;
    }

    const objectUrl = URL.createObjectURL(file);
    setPreview(objectUrl);
    setUploading(true);
    setError('');

    try {
      const contentType = file.type;
      const { data: urlData } = await userApi.getAvatarPresignedUrl(contentType);
      const { uploadUrl, cdnUrl } = urlData.data!;

      const uploadResp = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': contentType },
      });
      if (!uploadResp.ok) throw new Error('Upload failed');

      onUploadSuccess(cdnUrl);
    } catch (err: any) {
      setError(err?.response?.data?.message ?? err.message ?? 'Upload ảnh thất bại');
      setPreview(currentAvatar ?? null);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex items-center gap-4">
      <div className="relative shrink-0">
        {preview ? (
          <img src={preview} alt={username} className="w-16 h-16 rounded-full object-cover ring-4 ring-gray-100" />
        ) : (
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xl font-bold ring-4 ring-gray-100">
            {username.charAt(0).toUpperCase()}
          </div>
        )}
        {uploading && (
          <div className="absolute inset-0 bg-black/40 rounded-full flex items-center justify-center">
            <svg className="animate-spin w-5 h-5 text-white" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          </div>
        )}
      </div>
      <div>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
          className="px-4 py-2 text-sm border border-gray-300 rounded-xl hover:bg-gray-50 transition-colors disabled:opacity-50"
        >
          Đổi ảnh đại diện
        </button>
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          onChange={handleFileChange}
          className="hidden"
        />
        <p className="text-xs text-gray-400 mt-1">JPG, PNG, tối đa 5MB</p>
        {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
      </div>
    </div>
  );
}

function EditModal({ profile, onClose, onSuccess }: {
  profile: UserProfileResponse;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    fullName: profile.fullName ?? '',
    phone: profile.phone ?? '',
    avatarUrl: profile.avatarUrl ?? '',
  });
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => userApi.updateProfile(form),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-profile'] }); onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Cập nhật thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-5">Chỉnh sửa hồ sơ</h3>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
            {error}
          </div>
        )}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ảnh đại diện</label>
            <AvatarUpload
              currentAvatar={profile.avatarUrl}
              username={profile.username}
              onUploadSuccess={(cdnUrl) => setForm(f => ({ ...f, avatarUrl: cdnUrl }))}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Họ tên</label>
            <input
              type="text"
              value={form.fullName}
              onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Nhập họ tên"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại</label>
            <input
              type="tel"
              value={form.phone}
              onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="0xxx xxx xxx"
            />
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
            Huỷ
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
          >
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function ProfilePage() {
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ['user-profile'],
    queryFn: () => userApi.getProfile().then(r => r.data.data!),
    retry: 1,
  });

  const registerSellerMut = useMutation({
    mutationFn: () => userApi.registerAsSeller(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-profile'] }),
  });

  const fmtDate = (iso?: string) =>
    iso ? new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '-';

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Hồ sơ của tôi</h1>
        <p className="text-gray-500 mt-1 text-sm">Quản lý thông tin cá nhân và tài khoản</p>
      </div>

      {isLoading && (
        <div className="space-y-5">
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <div className="flex items-center gap-5">
              <Skeleton className="h-20 w-20 rounded-full" />
              <div className="flex-1 space-y-3">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-4 w-56" />
                <Skeleton className="h-6 w-20 rounded-full" />
              </div>
            </div>
          </div>
          <div className="bg-white rounded-2xl border border-gray-100 p-6 space-y-3">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-10 w-full rounded-xl" />
            <Skeleton className="h-10 w-full rounded-xl" />
          </div>
        </div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải hồ sơ.
        </div>
      )}

      {data && (
        <>
          {/* Avatar & Name Card */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
            <div className="flex items-center gap-5">
              <div className="relative shrink-0">
                {data.avatarUrl ? (
                  <img src={data.avatarUrl} alt={data.username} className="w-20 h-20 rounded-full object-cover ring-4 ring-gray-100" />
                ) : (
                  <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-2xl font-bold ring-4 ring-gray-100">
                    {data.username.charAt(0).toUpperCase()}
                  </div>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-3 mb-1 flex-wrap">
                  <h2 className="text-xl font-bold text-gray-900">{data.fullName || data.username}</h2>
                  <StatusBadge status={data.status} />
                </div>
                <p className="text-gray-500 text-sm">@{data.username}</p>
                <p className="text-gray-400 text-xs mt-1">Tham gia {fmtDate(data.createdAt)}</p>
              </div>
              <button
                onClick={() => setEditOpen(true)}
                className="shrink-0 px-4 py-2 text-sm font-medium border border-gray-300 rounded-xl hover:bg-gray-50 transition-colors"
              >
                Chỉnh sửa
              </button>
            </div>
          </div>

          {/* Info Grid */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
            <h3 className="text-base font-semibold text-gray-900 mb-4">Thông tin tài khoản</h3>
            <div className="space-y-4">
              {[
                { label: 'Email', value: data.email },
                { label: 'Số điện thoại', value: data.phone || 'Chưa cập nhật' },
                { label: 'Họ tên', value: data.fullName || 'Chưa cập nhật' },
                { label: 'Vai trò', value: data.roles?.join(', ') || data.status },
                { label: 'Trạng thái', value: data.status },
              ].map(({ label, value }) => (
                <div key={label} className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
                  <span className="text-sm text-gray-500">{label}</span>
                  <span className="text-sm font-medium text-gray-900">{value}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Seller Upgrade */}
          {!data.roles?.includes('SELLER') && (
            <div className="bg-gradient-to-r from-violet-50 to-blue-50 border border-violet-100 rounded-2xl p-6">
              <div className="flex items-start gap-4">
                <span className="text-3xl">🏪</span>
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900 mb-1">Trở thành người bán</h3>
                  <p className="text-sm text-gray-600 mb-3">
                    Mở cửa hàng của riêng bạn, tiếp cận hàng triệu khách hàng và bắt đầu kiếm thu nhập ngay hôm nay.
                  </p>
                  <button
                    onClick={() => registerSellerMut.mutate()}
                    disabled={registerSellerMut.isPending}
                    className="px-5 py-2 bg-gradient-to-r from-violet-600 to-blue-600 hover:from-violet-700 hover:to-blue-700 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-all"
                  >
                    {registerSellerMut.isPending ? 'Đang xử lý...' : 'Đăng ký bán hàng'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {editOpen && data && (
        <EditModal
          profile={data}
          onClose={() => setEditOpen(false)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['user-profile'] })}
        />
      )}
    </div>
  );
}
