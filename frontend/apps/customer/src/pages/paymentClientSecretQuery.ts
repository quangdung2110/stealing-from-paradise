export const CLIENT_SECRET_NOT_READY_RETRY_LIMIT = 45;
export const CLIENT_SECRET_DEFAULT_RETRY_LIMIT = 3;
export const CLIENT_SECRET_PENDING_POLL_MS = 1000;

type HttpLikeError = {
  response?: {
    status?: number;
    data?: {
      message?: string;
    };
  };
};

type ClientSecretLike = {
  clientSecret?: string | null;
};

export type ClientSecretPanelState = 'initializing' | 'ready' | 'failed';

export function getHttpStatus(error: unknown): number | undefined {
  return (error as HttpLikeError | null | undefined)?.response?.status;
}

export function isClientSecretNotReadyError(error: unknown): boolean {
  return getHttpStatus(error) === 404;
}

export function hasClientSecret(data: ClientSecretLike | null | undefined): data is ClientSecretLike {
  return typeof data?.clientSecret === 'string' && data.clientSecret.length > 0;
}

export function shouldRetryClientSecret(failureCount: number, error: unknown): boolean {
  if (isClientSecretNotReadyError(error)) {
    return failureCount < CLIENT_SECRET_NOT_READY_RETRY_LIMIT;
  }

  const status = getHttpStatus(error);
  if (status && status >= 400 && status < 500) {
    return false;
  }

  return failureCount < CLIENT_SECRET_DEFAULT_RETRY_LIMIT;
}

export function getClientSecretRetryDelay(): number {
  return CLIENT_SECRET_PENDING_POLL_MS;
}

export function shouldPollClientSecret(
  data: ClientSecretLike | null | undefined,
  error: unknown
): false | number {
  if (hasClientSecret(data) || error) {
    return false;
  }
  return CLIENT_SECRET_PENDING_POLL_MS;
}

export function getClientSecretPanelState({
  data,
  error,
  failureCount,
  isFetching,
  isPending,
}: {
  data: ClientSecretLike | null | undefined;
  error: unknown;
  failureCount: number;
  isFetching: boolean;
  isPending: boolean;
}): ClientSecretPanelState {
  if (hasClientSecret(data)) {
    return 'ready';
  }

  if (isPending || isFetching || !error || shouldRetryClientSecret(failureCount, error)) {
    return 'initializing';
  }

  return 'failed';
}

export function getClientSecretErrorMessage(error: unknown): string | null {
  const message = (error as HttpLikeError | null | undefined)?.response?.data?.message;
  return typeof message === 'string' && message.length > 0 ? message : null;
}
