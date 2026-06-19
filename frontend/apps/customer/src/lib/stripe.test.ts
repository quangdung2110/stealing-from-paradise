import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';

const { loadStripeMock, setLoadParametersMock } = vi.hoisted(() => {
  const loadStripeMock = vi.fn(() => Promise.resolve(null)) as any;
  const setLoadParametersMock = vi.fn();
  loadStripeMock.setLoadParameters = setLoadParametersMock;

  return { loadStripeMock, setLoadParametersMock };
});

vi.mock('@stripe/stripe-js/pure', () => ({
  loadStripe: loadStripeMock,
}));

describe('getStripe', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', 'pk_test_123');
    loadStripeMock.mockClear();
    setLoadParametersMock.mockClear();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('disables Stripe advanced fraud signals by default in dev', async () => {
    const { getStripe } = await import('./stripe');

    expect(setLoadParametersMock).toHaveBeenCalledWith({ advancedFraudSignals: false });

    await getStripe();

    expect(loadStripeMock).toHaveBeenCalledWith('pk_test_123');
  });

  it('keeps Stripe advanced fraud signals when explicitly enabled', async () => {
    vi.stubEnv('VITE_STRIPE_ADVANCED_FRAUD_SIGNALS', 'true');

    await import('./stripe');

    expect(setLoadParametersMock).not.toHaveBeenCalled();
  });
});
