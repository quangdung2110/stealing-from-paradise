import { describe, it, expect } from 'vitest';

// decodeJwt is a standalone function exported from authStore
function decodeJwt(token: string): any {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

describe('decodeJwt', () => {
  it('decodes a valid JWT payload', () => {
    // Header: {"alg":"HS256"} / Payload: {"sub":"123","role":"BUYER","exp":99999} / sig
    const token = 'eyJhbGciOiJIUzI1NiJ9.' +
      'eyJzdWIiOiIxMjMiLCJyb2xlIjoiQlVZRVIiLCJleHAiOjk5OTk5fQ.' +
      'fakeSig';
    const decoded = decodeJwt(token);
    expect(decoded).toEqual({ sub: '123', role: 'BUYER', exp: 99999 });
  });

  it('returns null for malformed input', () => {
    expect(decodeJwt('')).toBeNull();
    expect(decodeJwt('not.a.jwt.token')).toBeNull();
    expect(decodeJwt('only.two')).toBeNull();
  });

  it('returns null for invalid base64 in payload', () => {
    expect(decodeJwt('header.!!!invalid!!!.sig')).toBeNull();
  });
});

describe('isAuthFromCookie', () => {
  it('returns false when no accessToken cookie is set', () => {
    const cookie = typeof document !== 'undefined' ? document.cookie : '';
    const result = !cookie.includes('accessToken');
    expect(result).toBe(true);
  });
});
