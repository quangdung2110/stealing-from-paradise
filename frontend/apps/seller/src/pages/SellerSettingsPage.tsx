import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { userApi } from '@shared/api/user.api';
import { sellerApi } from '@shared/api/seller.api';
import { Skeleton, Badge } from '@shared/components/ui';

function SellerProfileCard({ profile }: {
  profile: { fullName?: string; phone?: string; email: string; avatarUrl?: string };
}) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
      <div className="flex items-start gap-5 mb-4">
        {profile.avatarUrl ? (
          <img src={profile.avatarUrl} alt="" className="w-16 h-16 rounded-full object-cover ring-4 ring-gray-100" />
        ) : (
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xl font-bold ring-4 ring-gray-100">
            {profile.fullName?.charAt(0) ?? profile.email.charAt(0).toUpperCase()}
          </div>
        )}
        <div className="flex-1">
          <h3 className="text-lg font-bold text-gray-900">{profile.fullName || 'Người bán'}</h3>
          <p className="text-sm text-gray-500">{profile.email}</p>
          {profile.phone && <p className="text-sm text-gray-400">{profile.phone}</p>}
        </div>
      </div>

      <div className="space-y-3">
        {[
          { label: 'Tên hiển thị', value: profile.fullName || 'Chưa cập nhật' },
          { label: 'Email', value: profile.email },
          { label: 'Số điện thoại', value: profile.phone || 'Chưa cập nhật' },
        ].map(({ label, value }) => (
          <div key={label} className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
            <span className="text-sm text-gray-500">{label}</span>
            <span className="text-sm font-medium text-gray-900">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function EditProfileModal({ profile, onClose }: {
  profile: { fullName?: string; phone?: string; email: string };
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ fullName: profile.fullName ?? '', phone: profile.phone ?? '' });
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => userApi.updateProfile(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-profile'] });
      onClose();
    },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Cập nhật thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-5">Chỉnh sửa hồ sơ</h3>
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{error}</div>}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên cửa hàng / Họ tên</label>
            <input type="text" value={form.fullName}
              onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Nhập tên cửa hàng" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại</label>
            <input type="tel" value={form.phone}
              onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="0xxx xxx xxx" />
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose}
            className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button onClick={() => mut.mutate()} disabled={mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60">
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function SellerSettingsPage() {
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);

  const { data: profile, isLoading } = useQuery({
    queryKey: ['seller-profile'],
    queryFn: () => userApi.getProfile().then(r => r.data.data!),
    retry: 1,
  });

  const { data: stripeStatus } = useQuery({
    queryKey: ['seller-stripe-status'],
    queryFn: () => sellerApi.getStripeStatus().then(r => r.data.data),
    retry: 1,
    staleTime: 30_000,
  });

  const stripeBadge = (() => {
    if (!stripeStatus) return null;
    if (stripeStatus.onboardingStatus === 'COMPLETE')
      return <Badge tone="success" dot>Đã kết nối</Badge>;
    if (stripeStatus.onboardingStatus === 'IN_PROGRESS')
      return <Badge tone="warning" dot>Đang xác minh</Badge>;
    if (stripeStatus.onboardingStatus === 'SUSPENDED')
      return <Badge tone="danger" dot>Bị tạm ngưng</Badge>;
    return <Badge tone="neutral">Chưa kết nối</Badge>;
  })();

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Hồ sơ người bán</h1>
          <p className="text-gray-500 mt-1 text-sm">Quản lý thông tin cửa hàng của bạn</p>
        </div>
        {profile && (
          <button onClick={() => setEditOpen(true)}
            className="px-4 py-2 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
            ✏️ Chỉnh sửa
          </button>
        )}
      </div>

      {isLoading && (
        <div className="space-y-5">
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <div className="flex items-start gap-5">
              <Skeleton className="h-16 w-16 rounded-full" />
              <div className="flex-1 space-y-3">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-4 w-56" />
                <Skeleton className="h-4 w-44" />
              </div>
            </div>
          </div>
          <div className="bg-white rounded-2xl border border-gray-100 p-6 space-y-3">
            <Skeleton className="h-5 w-36" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-10 w-36 rounded-xl" />
          </div>
        </div>
      )}

      {profile && (
        <>
          <SellerProfileCard profile={profile} />

          {/* Stripe Status */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-semibold text-gray-900">💳 Thanh toán Stripe</h3>
              {stripeBadge}
            </div>
            <p className="text-sm text-gray-500 mb-4">
              Quản lý tài khoản Stripe để nhận thanh toán từ khách hàng.
            </p>
            {stripeStatus?.stripeAccountId && (
              <div className="mb-4 bg-gray-50 rounded-xl p-3 text-sm">
                <div className="flex justify-between mb-1">
                  <span className="text-gray-500">Account ID:</span>
                  <span className="font-mono text-xs text-gray-700">{stripeStatus.stripeAccountId}</span>
                </div>
                {stripeStatus.chargesEnabled && (
                  <div className="flex items-center gap-1.5 text-green-700">
                    <span className="w-2 h-2 rounded-full bg-green-500" />
                    <span>Có thể nhận thanh toán</span>
                  </div>
                )}
              </div>
            )}
            <Link to="/stripe-onboarding"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-gradient-to-r from-violet-600 to-blue-600 hover:from-violet-700 hover:to-blue-700 text-white rounded-xl text-sm font-semibold transition-all">
              ⚙️ Quản lý Stripe
            </Link>
          </div>

          {/* Change Password */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
            <h3 className="text-base font-semibold text-gray-900 mb-4">🔑 Đổi mật khẩu</h3>
            <SellerChangePasswordSection />
          </div>

          {/* Store info */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <h3 className="text-base font-semibold text-gray-900 mb-4">ℹ️ Thông tin cửa hàng</h3>
            <p className="text-sm text-gray-500">
              Email liên hệ: {profile.email}
            </p>
            <p className="text-sm text-gray-400 mt-1">
              Seller account · FlashSale Platform
            </p>
          </div>
        </>
      )}

      {editOpen && profile && (
        <EditProfileModal profile={profile} onClose={() => setEditOpen(false)} />
      )}
    </div>
  );
}

function SellerChangePasswordSection() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [showPw, setShowPw] = useState({ current: false, new: false, confirm: false });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const mut = useMutation({
    mutationFn: async () => {
      if (form.newPassword !== form.confirmPassword) throw new Error('Mật khẩu xác nhận không khớp');
      if (form.newPassword.length < 6) throw new Error('Mật khẩu mới phải có ít nhất 6 ký tự');
      await userApi.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      });
    },
    onSuccess: () => {
      setSuccess('Đổi mật khẩu thành công!');
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setError('');
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message ?? err.message ?? 'Đổi mật khẩu thất bại');
      setSuccess('');
    },
  });

  return (
    <div>
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{error}</div>}
      {success && <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">{success}</div>}
      <div className="max-w-md">
        <PwInput name="currentPassword" label="Mật khẩu hiện tại" placeholder="Nhập mật khẩu"
          value={form.currentPassword} show={showPw.current}
          onChange={val => setForm(f => ({ ...f, currentPassword: val }))}
          onToggleShow={() => setShowPw(p => ({ ...p, current: !p.current }))} />
        <PwInput name="newPassword" label="Mật khẩu mới" placeholder="Ít nhất 6 ký tự"
          value={form.newPassword} show={showPw.new}
          onChange={val => setForm(f => ({ ...f, newPassword: val }))}
          onToggleShow={() => setShowPw(p => ({ ...p, new: !p.new }))} />
        <PwInput name="confirmPassword" label="Xác nhận mật khẩu mới" placeholder="Nhập lại mật khẩu mới"
          value={form.confirmPassword} show={showPw.confirm}
          onChange={val => setForm(f => ({ ...f, confirmPassword: val }))}
          onToggleShow={() => setShowPw(p => ({ ...p, confirm: !p.confirm }))} />
      </div>
      <button onClick={() => mut.mutate()}
        disabled={mut.isPending || !form.currentPassword || !form.newPassword || !form.confirmPassword}
        className="mt-2 px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-all">
        {mut.isPending ? 'Đang đổi...' : 'Đổi mật khẩu'}
      </button>
    </div>
  );
}

interface PwInputProps {
  name: 'currentPassword' | 'newPassword' | 'confirmPassword';
  label: string;
  placeholder: string;
  value: string;
  show: boolean;
  onChange: (val: string) => void;
  onToggleShow: () => void;
}

function PwInput({ label, placeholder, value, show, onChange, onToggleShow }: PwInputProps) {
  return (
    <div className="mb-4">
      <label className="block text-sm font-medium text-gray-700 mb-1.5">{label}</label>
      <div className="relative">
        <input type={show ? 'text' : 'password'} value={value}
          onChange={e => onChange(e.target.value)} placeholder={placeholder}
          className="w-full px-4 py-2.5 pr-11 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <button type="button" onClick={onToggleShow}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
        </button>
      </div>
    </div>
  );
}
