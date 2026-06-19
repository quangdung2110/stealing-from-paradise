import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi, type StripeOnboardingStatus } from '@shared/api/seller.api';
import { parseStripeError, type StripeErrorContext } from '@/lib/stripeError';

type OnboardingStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';

function normalizeStatus(raw?: string | null): OnboardingStatus {
  if (!raw) return 'PENDING';
  if (raw === 'COMPLETE' || raw === 'IN_PROGRESS' || raw === 'SUSPENDED') return raw;
  return 'PENDING';
}

function VerificationChecklist({ status }: { status: StripeOnboardingStatus }) {
  const items = [
    { done: !!status.detailsSubmitted, label: 'Đã xác minh danh tính' },
    { done: !!status.chargesEnabled,   label: 'Đã kích hoạt nhận thanh toán' },
    { done: !!status.payoutsEnabled,   label: 'Đã liên kết tài khoản ngân hàng' },
  ];

  return (
    <div className="space-y-2 mt-3">
      {items.map(item => (
        <div key={item.label} className="flex items-center gap-2 text-sm">
          <span className={`shrink-0 w-5 h-5 rounded-full flex items-center justify-center text-xs ${
            item.done ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'
          }`}>
            {item.done ? '✓' : '—'}
          </span>
          <span className={item.done ? 'text-green-800' : 'text-gray-500'}>
            {item.label}
          </span>
        </div>
      ))}
    </div>
  );
}

export default function StripeOnboardingPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [error, setError] = useState<StripeErrorContext | null>(null);
  const [expressDashboardUrl, setExpressDashboardUrl] = useState<string | null>(null);

  const justReturnedFromStripe = searchParams.get('from') === 'stripe';
  const requestRefresh         = searchParams.get('refresh') === '1';

  const { data: status } = useQuery({
    queryKey: ['stripe-onboarding-status'],
    queryFn: () => sellerApi.getStripeStatus().then(r => r.data.data),
    retry: 1,
    refetchInterval: (query) => {
      const data: any = query.state.data;
      const s = normalizeStatus(data?.onboardingStatus);
      if (s === 'COMPLETE') return false;
      return justReturnedFromStripe ? 3000 : false;
    },
    refetchOnWindowFocus: true,
  });

  const startMut = useMutation({
    mutationFn: () => sellerApi.startStripeOnboarding(),
    onSuccess: (res) => {
      const data = res.data.data;
      if (data?.expressDashboardUrl) {
        setExpressDashboardUrl(data.expressDashboardUrl);
      }
      const url = data?.onboardingUrl;
      if (url) {
        window.location.href = url;
        queryClient.invalidateQueries({ queryKey: ['stripe-onboarding-status'] });
      }
    },
    onError: (err: any) => {
      setError(parseStripeError(err));
    },
  });

  const refreshMut = useMutation({
    mutationFn: () => sellerApi.refreshStripeLink(),
    onSuccess: (res) => {
      const data = res.data.data;
      if (data?.expressDashboardUrl) {
        setExpressDashboardUrl(data.expressDashboardUrl);
      }
      const url = data?.onboardingUrl;
      if (url) {
        window.location.href = url;
      }
    },
    onError: (err: any) => {
      setError(parseStripeError(err));
    },
  });

  // /stripe/refresh redirects here with ?refresh=1 when account link expired.
  // Auto-trigger a fresh AccountLink.
  useEffect(() => {
    if (requestRefresh && !refreshMut.isPending) {
      refreshMut.mutate();
      const next = new URLSearchParams(searchParams);
      next.delete('refresh');
      setSearchParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestRefresh]);

  const currentStatus: OnboardingStatus = normalizeStatus(status?.onboardingStatus);
  const isComplete = currentStatus === 'COMPLETE';
  const isSuspended = currentStatus === 'SUSPENDED';
  const isInProgress = currentStatus === 'IN_PROGRESS';
  const hasOnboardingUrl = !!status?.onboardingUrl;
  const currentExpressUrl = expressDashboardUrl || status?.expressDashboardUrl;

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Kết nối thanh toán Stripe</h1>
        <p className="text-gray-500 mt-1">Hoàn thành các bước sau để bắt đầu nhận thanh toán từ khách hàng</p>
      </div>

      {/* Status badge */}
      <div className={`rounded-2xl border p-4 mb-8 flex items-center gap-3 ${
        isComplete
          ? 'bg-green-50 border-green-200'
          : isSuspended
            ? 'bg-red-50 border-red-200'
            : 'bg-indigo-50 border-indigo-100'
      }`}>
        {isComplete ? (
          <>
            <span className="text-2xl">✅</span>
            <div>
              <p className="font-semibold text-green-900">Tài khoản Stripe đã kích hoạt</p>
              <p className="text-sm text-green-700">Bạn có thể nhận thanh toán từ khách hàng ngay bây giờ.</p>
            </div>
          </>
        ) : isSuspended ? (
          <>
            <span className="text-2xl">⚠️</span>
            <div>
              <p className="font-semibold text-red-900">Tài khoản Stripe bị tạm ngưng</p>
              <p className="text-sm text-red-700">
                Stripe đã hạn chế tài khoản của bạn. Mở Express Dashboard để kiểm tra lý do.
              </p>
            </div>
          </>
        ) : (
          <>
            <span className="text-2xl">🔒</span>
            <div>
              <p className="font-semibold text-indigo-900">Chưa kết nối Stripe</p>
              <p className="text-sm text-indigo-700">
                Trạng thái hiện tại: <strong>{isInProgress ? 'Đang xác minh' : 'Chưa bắt đầu'}</strong>
              </p>
              <p className="text-xs text-indigo-500 mt-1">
                Form của Stripe sẽ hướng dẫn bạn xác minh danh tính và liên kết ngân hàng trong cùng một luồng.
              </p>
            </div>
          </>
        )}
      </div>

      {status?.stripeAccountId?.startsWith('acct_manual_') && (
        <div className="mb-6 bg-blue-50 border border-blue-200 rounded-xl p-4 text-blue-800 text-sm flex items-start gap-3">
          <span className="text-lg">🔗</span>
          <div>
            <p className="font-semibold">Kết nối thủ công (Platform Admin)</p>
            <p>Tài khoản Stripe của bạn được quản trị viên nền tảng liên kết thủ công. Nếu cần thay đổi thông tin thanh toán, vui lòng liên hệ admin.</p>
          </div>
        </div>
      )}

      {justReturnedFromStripe && !isComplete && (
        <div className="mb-6 bg-amber-50 border border-amber-200 rounded-xl p-4 text-amber-800 text-sm flex items-start gap-3">
          <span className="text-lg">⏳</span>
          <div>
            <p className="font-semibold">Đang xác minh với Stripe…</p>
            <p className="text-amber-700">Trang sẽ tự động cập nhật khi Stripe phê duyệt tài khoản (thường vài giây).</p>
          </div>
        </div>
      )}

      {/* SUSPENDED: Detailed guidance */}
      {isSuspended && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-5">
          <h3 className="font-bold text-red-900 mb-2">Tài khoản bị hạn chế</h3>
          <p className="text-sm text-red-700 mb-3">
            Stripe có thể tạm ngưng tài khoản vì nhiều lý do: thông tin chưa đầy đủ, yêu cầu xác minh bổ sung,
            hoặc hoạt động đáng ngờ. Truy cập Express Dashboard để xem chi tiết.
          </p>
          <div className="flex gap-2 flex-wrap">
            {currentExpressUrl && (
              <a
                href={currentExpressUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-block px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-xl transition-colors"
              >
                Mở Stripe Dashboard để xem chi tiết ↗
              </a>
            )}
            <button
              onClick={() => refreshMut.mutate()}
              disabled={refreshMut.isPending}
              className="px-4 py-2 border border-red-200 text-red-700 hover:bg-red-100 text-sm font-medium rounded-xl transition-colors disabled:opacity-50"
            >
              {refreshMut.isPending ? 'Đang tạo...' : 'Tạo lại liên kết onboarding'}
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          <div className="flex items-start gap-2">
            <span className="text-base shrink-0">⚠️</span>
            <div className="flex-1">
              <p className="font-semibold text-red-800">{error.message}</p>
              {error.hint && (
                <p className="mt-1 text-red-600">{error.hint}</p>
              )}
            </div>
          </div>
          <button onClick={() => setError(null)} className="mt-2 ml-8 underline font-medium text-red-700 hover:text-red-800">Đóng</button>
        </div>
      )}

      {/* Verification checklist (IN_PROGRESS) */}
      {isInProgress && status && (
        <div className="mb-6 bg-white rounded-2xl border border-indigo-200 p-5">
          <h3 className="font-bold text-gray-900 mb-1">Tiến trình xác minh</h3>
          <p className="text-sm text-gray-500 mb-1">Trạng thái các bước xác minh với Stripe:</p>
          <VerificationChecklist status={status} />
        </div>
      )}

      {/* Steps — matches Stripe Express onboarding reality:
            1. Tạo Express Account
            2. Xác minh danh tính + liên kết ngân hàng (form Stripe gom chung)
            3. Kích hoạt — nhận thanh toán */}
      <div className="space-y-4 mb-8">
        {[
          { step: 1, title: 'Tạo tài khoản Stripe', desc: 'Đăng ký tài khoản Stripe Connect Express để nhận thanh toán', key: 'inactive' as const },
          { step: 2, title: 'Xác minh danh tính & Liên kết ngân hàng', desc: 'Điền form Stripe: giấy tờ tùy thân, thông tin cá nhân, tài khoản ngân hàng', key: 'IN_PROGRESS' as OnboardingStatus },
          { step: 3, title: 'Kích hoạt bán hàng', desc: 'Stripe phê duyệt tài khoản — bắt đầu nhận tiền từ khách hàng', key: 'COMPLETE' as OnboardingStatus },
        ].map(({ step, title, desc, key }) => {
          let stepState: 'completed' | 'active' | 'pending' = 'pending';
          if (isComplete) {
            stepState = 'completed';
          } else if (isInProgress) {
            // step 1 done, step 2 active, step 3 pending
            stepState = step === 1 ? 'completed' : step === 2 ? 'active' : 'pending';
          } else if (currentStatus === 'PENDING') {
            stepState = step === 1 ? 'active' : 'pending';
          }

          return (
            <div key={step} className={`bg-white rounded-2xl border p-5 flex items-start gap-4 transition-all ${
              stepState === 'completed' ? 'border-green-200 bg-green-50/30' :
              stepState === 'active' ? 'border-blue-200 bg-blue-50/30' :
              'border-gray-100'
            }`}>
              <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 font-bold text-sm ${
                stepState === 'completed' ? 'bg-green-500 text-white' :
                stepState === 'active' ? 'bg-blue-600 text-white' :
                'bg-gray-100 text-gray-500'
              }`}>
                {stepState === 'completed' ? '✓' : step}
              </div>
              <div className="flex-1">
                <h3 className={`font-semibold mb-1 ${
                  stepState === 'completed' ? 'text-green-800' :
                  stepState === 'active' ? 'text-blue-800' :
                  'text-gray-900'
                }`}>{title}</h3>
                <p className="text-sm text-gray-500">{desc}</p>
              </div>
              {stepState === 'completed' && (
                <span className="text-xs font-medium text-green-700 bg-green-100 px-2.5 py-1 rounded-full shrink-0">Hoàn thành</span>
              )}
            </div>
          );
        })}
      </div>

      {/* CTA */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 text-center">
        {isComplete ? (
          <>
            <div className="text-4xl mb-3">🎉</div>
            <h3 className="font-bold text-gray-900 mb-2">Bạn đã sẵn sàng nhận thanh toán!</h3>
            <p className="text-sm text-gray-500 mb-5">
              Tài khoản Stripe đã được kích hoạt. Tiền từ đơn hàng sẽ được chuyển vào tài khoản ngân hàng của bạn.
            </p>
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <a href="/payments" className="inline-block px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-xl text-sm transition-colors">
                Xem thu nhập
              </a>
              {currentExpressUrl && (
                <a
                  href={currentExpressUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-block px-6 py-2.5 border border-indigo-200 text-indigo-700 hover:bg-indigo-50 font-medium rounded-xl text-sm transition-colors"
                >
                  Mở Stripe Express Dashboard ↗
                </a>
              )}
              <a href="/dashboard" className="inline-block px-6 py-2.5 border border-gray-200 hover:bg-gray-50 text-gray-700 font-medium rounded-xl text-sm transition-colors">
                Quay về Dashboard
              </a>
            </div>
          </>
        ) : (
          <>
            <h3 className="font-bold text-gray-900 mb-2">
              {isInProgress ? 'Tiếp tục xác minh với Stripe' : 'Sẵn sàng kết nối?'}
            </h3>
            <p className="text-sm text-gray-500 mb-5">
              {isInProgress
                ? 'Bạn đã bắt đầu onboarding nhưng chưa hoàn tất. Chọn một cách để tiếp tục:'
                : 'Nhấn nút bên dưới để bắt đầu quá trình onboarding với Stripe. Thường mất 5–10 phút để hoàn thành.'
              }
            </p>

            {/* IN_PROGRESS: always show Express Dashboard as primary option */}
            {isInProgress && currentExpressUrl && (
              <div className="mb-4">
                <a
                  href={currentExpressUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-8 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white font-semibold rounded-xl shadow-sm transition-all text-sm inline-block"
                >
                  Tiếp tục trong Stripe Express Dashboard ↗
                </a>
                <p className="text-xs text-gray-400 mt-3">— hoặc —</p>
              </div>
            )}

            <div className="flex flex-col gap-3 items-center">
              <button
                onClick={() => startMut.mutate()}
                disabled={startMut.isPending || refreshMut.isPending}
                className="px-8 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-semibold rounded-xl shadow-sm transition-all text-sm"
              >
                {startMut.isPending
                  ? '⏳ Đang khởi tạo...'
                  : isInProgress
                    ? 'Mở lại form đăng ký Stripe →'
                    : 'Bắt đầu với Stripe →'}
              </button>
              {(isSuspended || isInProgress) && (
                <button
                  onClick={() => refreshMut.mutate()}
                  disabled={startMut.isPending || refreshMut.isPending}
                  className="text-sm text-indigo-600 hover:text-indigo-700 underline disabled:opacity-40"
                >
                  {refreshMut.isPending ? 'Đang tạo liên kết…' : 'Làm mới liên kết đã hết hạn'}
                </button>
              )}
            </div>
            <p className="text-xs text-gray-400 mt-3">
              Miễn phí kết nối · Phí giao dịch 2.9% + 30¢
              {hasOnboardingUrl && !isInProgress && (
                <span className="block text-amber-500 mt-0.5">Liên kết có hiệu lực trong 24 giờ</span>
              )}
            </p>
          </>
        )}
      </div>

      {/* Stripe info banner */}
      <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-100 rounded-2xl p-6 mt-6 flex items-start gap-4">
        <span className="text-4xl shrink-0">🔒</span>
        <div>
          <h3 className="font-semibold text-gray-900 mb-1">Thanh toán bảo mật với Stripe</h3>
          <p className="text-sm text-gray-600">
            Stripe là nền tảng thanh toán hàng đầu thế giới, được sử dụng bởi hàng triệu doanh nghiệp.
            Mọi giao dịch đều được mã hoá và bảo vệ theo tiêu chuẩn PCI DSS.
          </p>
        </div>
      </div>
    </div>
  );
}
