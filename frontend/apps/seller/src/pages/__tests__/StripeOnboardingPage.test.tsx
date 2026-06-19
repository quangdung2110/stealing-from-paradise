import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, act } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import StripeOnboardingPage from '../StripeOnboardingPage';
import { sellerApi } from '@shared/api/seller.api';

// The page reflects the backend state produced by Stripe `account.updated`
// webhooks via getStripeStatus — so we drive that mock through every state.
vi.mock('@shared/api/seller.api', () => ({
  sellerApi: {
    getStripeStatus: vi.fn(),
    startStripeOnboarding: vi.fn(() => Promise.resolve({ data: { data: {} } })),
    refreshStripeLink: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
}));

const setStatus = (status: any) => (sellerApi.getStripeStatus as any).mockResolvedValue({ data: { data: status } });

beforeEach(() => vi.clearAllMocks());

describe('StripeOnboardingPage — account.updated state matrix', () => {
  it('COMPLETE → activated banner + earnings CTA', async () => {
    setStatus({ onboardingStatus: 'COMPLETE', detailsSubmitted: true, chargesEnabled: true, payoutsEnabled: true });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding' });
    expect(await screen.findByText(/Tài khoản Stripe đã kích hoạt/i)).toBeInTheDocument();
    expect(screen.getByText('Xem thu nhập')).toBeInTheDocument();
  });

  it('SUSPENDED → restricted banner + recreate-link action', async () => {
    setStatus({ onboardingStatus: 'SUSPENDED', expressDashboardUrl: 'https://dash.stripe/x' });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding' });
    expect(await screen.findByText(/Tài khoản Stripe bị tạm ngưng/i)).toBeInTheDocument();
    expect(screen.getByText(/Tạo lại liên kết onboarding/i)).toBeInTheDocument();
  });

  it('IN_PROGRESS → verification checklist + continue CTA', async () => {
    setStatus({ onboardingStatus: 'IN_PROGRESS', detailsSubmitted: true, chargesEnabled: false, payoutsEnabled: false });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding' });
    expect(await screen.findByText(/Tiến trình xác minh/i)).toBeInTheDocument();
    expect(screen.getByText(/Tiếp tục xác minh với Stripe/i)).toBeInTheDocument();
  });

  it('PENDING → not-connected banner + start CTA', async () => {
    setStatus({ onboardingStatus: 'PENDING' });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding' });
    expect(await screen.findByText(/Chưa kết nối Stripe/i)).toBeInTheDocument();
    expect(screen.getByText(/Bắt đầu với Stripe/i)).toBeInTheDocument();
  });

  it('manual account (acct_manual_*) → manual-connect banner', async () => {
    setStatus({ onboardingStatus: 'IN_PROGRESS', stripeAccountId: 'acct_manual_123' });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding' });
    expect(await screen.findByText(/Kết nối thủ công \(Platform Admin\)/i)).toBeInTheDocument();
  });
});

describe('StripeOnboardingPage — return/refresh redirects', () => {
  it('?from=stripe shows the verifying banner while not COMPLETE', async () => {
    setStatus({ onboardingStatus: 'IN_PROGRESS', detailsSubmitted: true });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding?from=stripe' });
    expect(await screen.findByText(/Đang xác minh với Stripe/i)).toBeInTheDocument();
  });

  it('?refresh=1 auto-triggers refreshStripeLink once', async () => {
    setStatus({ onboardingStatus: 'IN_PROGRESS' });
    renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding?refresh=1' });
    await waitFor(() => expect(sellerApi.refreshStripeLink).toHaveBeenCalledTimes(1));
  });

  it('polling fetches status every 3s when from=stripe and stops when COMPLETE', async () => {
    vi.useFakeTimers();
    try {
      let callCount = 0;
      (sellerApi.getStripeStatus as any).mockImplementation(() => {
        callCount++;
        if (callCount === 1) {
          return Promise.resolve({ data: { data: { onboardingStatus: 'IN_PROGRESS' } } });
        }
        return Promise.resolve({ data: { data: { onboardingStatus: 'COMPLETE', detailsSubmitted: true, chargesEnabled: true, payoutsEnabled: true } } });
      });

      renderWithProviders(<StripeOnboardingPage />, { route: '/stripe-onboarding?from=stripe' });

      // Run pending timers to let the initial query fetch and subsequent 3s refetch execute
      await act(async () => {
        await vi.runOnlyPendingTimersAsync();
      });

      // It should have called twice: first call (returns IN_PROGRESS), second call (after 3s, returns COMPLETE)
      expect(sellerApi.getStripeStatus).toHaveBeenCalledTimes(2);
      expect(screen.getByText(/Tài khoản Stripe đã kích hoạt/i)).toBeInTheDocument();

      // If we advance timers by another 3000ms, it should NOT make any more calls
      await act(async () => {
        await vi.advanceTimersByTimeAsync(3000);
      });

      expect(sellerApi.getStripeStatus).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });
});
