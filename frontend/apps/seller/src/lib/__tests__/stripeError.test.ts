import { describe, it, expect } from 'vitest';
import { parseStripeError } from '../stripeError';

describe('parseStripeError (StripeOnboardingPage)', () => {
  it('maps CONNECT_NOT_ACTIVATED code to a platform error', () => {
    const r = parseStripeError({ response: { data: { code: 'CONNECT_NOT_ACTIVATED' } } });
    expect(r.isPlatformError).toBe(true);
    expect(r.message).toMatch(/Stripe Connect/i);
    expect(r.hint).toBeTruthy();
  });

  it('maps the "signed up for Connect" message to a platform error', () => {
    const r = parseStripeError({ response: { data: { message: 'You must be signed up for Connect' } } });
    expect(r.isPlatformError).toBe(true);
  });

  it('maps country_unsupported to a platform error', () => {
    const r = parseStripeError({ response: { data: { message: 'country_unsupported' } } });
    expect(r.isPlatformError).toBe(true);
    expect(r.message).toMatch(/Quốc gia/i);
  });

  it('falls back to a generic, non-platform error', () => {
    const r = parseStripeError({ response: { data: { message: 'Something else' } } });
    expect(r.isPlatformError).toBe(false);
    expect(r.message).toBe('Something else');
  });

  it('uses a default message when none is provided', () => {
    const r = parseStripeError({});
    expect(r.isPlatformError).toBe(false);
    expect(r.message).toMatch(/Không thể khởi tạo Stripe/);
  });
});
