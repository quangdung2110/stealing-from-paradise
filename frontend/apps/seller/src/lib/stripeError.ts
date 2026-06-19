/**
 * Stripe onboarding error interpretation.
 *
 * Maps a raw axios error from the Stripe onboarding endpoints into a
 * seller-friendly Vietnamese message, distinguishing platform-level
 * misconfiguration (which the seller cannot fix) from generic failures.
 * Extracted from StripeOnboardingPage so the mapping is unit-testable.
 */
export interface StripeErrorContext {
  /** True when the failure is a platform/admin configuration issue, not the seller's. */
  isPlatformError: boolean;
  message: string;
  hint?: string;
}

export function parseStripeError(err: any): StripeErrorContext {
  const raw = err?.response?.data?.message || err?.message || '';
  const code = err?.response?.data?.code || '';

  if (raw.includes('signed up for Connect') || code === 'CONNECT_NOT_ACTIVATED') {
    return {
      isPlatformError: true,
      message: 'Nền tảng chưa kích hoạt Stripe Connect.',
      hint: 'Đây là lỗi cấu hình phía nền tảng. Vui lòng liên hệ admin để kích hoạt Stripe Connect.',
    };
  }
  if (raw.includes('country_unsupported')) {
    return {
      isPlatformError: true,
      message: 'Quốc gia của bạn chưa được Stripe hỗ trợ.',
      hint: 'Stripe hiện chưa hỗ trợ Vietnam làm quốc gia Connected Account. Vui lòng liên hệ admin.',
    };
  }
  return {
    isPlatformError: false,
    message: err?.response?.data?.message || 'Không thể khởi tạo Stripe. Vui lòng thử lại.',
  };
}
