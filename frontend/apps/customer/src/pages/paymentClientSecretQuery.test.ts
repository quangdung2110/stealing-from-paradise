import { describe, expect, it } from 'vitest';
import {
  CLIENT_SECRET_NOT_READY_RETRY_LIMIT,
  CLIENT_SECRET_PENDING_POLL_MS,
  getClientSecretErrorMessage,
  getClientSecretPanelState,
  shouldPollClientSecret,
  shouldRetryClientSecret,
} from './paymentClientSecretQuery';

describe('paymentClientSecretQuery', () => {
  const httpError = (status: number, message = 'error') => ({
    response: { status, data: { message } },
  });

  it('retries 404 while payment-service is still creating the transaction', () => {
    expect(shouldRetryClientSecret(CLIENT_SECRET_NOT_READY_RETRY_LIMIT - 1, httpError(404))).toBe(true);
    expect(shouldRetryClientSecret(CLIENT_SECRET_NOT_READY_RETRY_LIMIT, httpError(404))).toBe(false);
  });

  it('does not retry non-transient client errors', () => {
    expect(shouldRetryClientSecret(0, httpError(401))).toBe(false);
    expect(shouldRetryClientSecret(0, httpError(403))).toBe(false);
  });

  it('polls 202/null responses until a client secret exists', () => {
    expect(shouldPollClientSecret(null, null)).toBe(CLIENT_SECRET_PENDING_POLL_MS);
    expect(shouldPollClientSecret({ clientSecret: '' }, null)).toBe(CLIENT_SECRET_PENDING_POLL_MS);
    expect(shouldPollClientSecret({ clientSecret: 'pi_secret' }, null)).toBe(false);
    expect(shouldPollClientSecret(null, httpError(500))).toBe(false);
  });

  it('keeps the panel in initializing state during transient pending states', () => {
    expect(getClientSecretPanelState({
      data: null,
      error: null,
      failureCount: 0,
      isFetching: false,
      isPending: false,
    })).toBe('initializing');
    expect(getClientSecretPanelState({
      data: undefined,
      error: httpError(404),
      failureCount: CLIENT_SECRET_NOT_READY_RETRY_LIMIT - 1,
      isFetching: false,
      isPending: false,
    })).toBe('initializing');
  });

  it('returns failed after retries are exhausted', () => {
    expect(getClientSecretPanelState({
      data: undefined,
      error: httpError(404, 'Payment not found'),
      failureCount: CLIENT_SECRET_NOT_READY_RETRY_LIMIT,
      isFetching: false,
      isPending: false,
    })).toBe('failed');
    expect(getClientSecretErrorMessage(httpError(404, 'Payment not found'))).toBe('Payment not found');
  });
});
