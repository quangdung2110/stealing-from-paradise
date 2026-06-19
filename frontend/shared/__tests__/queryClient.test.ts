import { describe, it, expect } from 'vitest';
import { createQueryClient } from '../lib/queryClient';

describe('createQueryClient', () => {
  it('creates a QueryClient with correct defaults', () => {
    const client = createQueryClient();
    const defaults = client.getDefaultOptions();

    expect(defaults.queries?.staleTime).toBe(60_000);
    expect(defaults.queries?.refetchOnWindowFocus).toBe(false);
    expect(defaults.queries?.retry).toBe(1);
    expect(defaults.queries?.refetchOnMount).toBe(false);
    expect(defaults.mutations?.retry).toBe(0);
  });

  it('creates independent instances', () => {
    const a = createQueryClient();
    const b = createQueryClient();
    expect(a).not.toBe(b);
  });
});
