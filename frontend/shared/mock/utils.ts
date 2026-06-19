export const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

export function isMockMode(): boolean {
  return import.meta.env.VITE_BACKEND_MODE === 'mock' || !import.meta.env.VITE_API_URL;
}

export function isNetworkError(error: unknown): boolean {
  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    return (
      error.name === 'NetworkError' ||
      msg.includes('network') ||
      msg.includes('failed to fetch') ||
      msg.includes('econnrefused') ||
      msg.includes('err_connection') ||
      msg.includes('net::err') ||
      msg.includes('timeout') ||
      (error as any).code === 'ECONNREFUSED' ||
      (error as any).code === 'ERR_NETWORK' ||
      (error as any).code === 'ETIMEDOUT' ||
      (error as any).code === 'ERR_CANCELED'
    );
  }
  return false;
}

export function shouldUseMock(error: unknown): boolean {
  if (isMockMode()) return true;
  return isNetworkError(error);
}
