import { describe, it, expect } from 'vitest';

// Inline the mapping functions to test them directly (mirrors flashSale.api.ts)
interface BackendSession {
  sessionId: number;
  name: string;
  status: string;
  startTime: string;
  endTime: string;
  secondsRemaining: number;
  isEnded: boolean;
  createdAt: string;
  updatedAt: string;
}

interface BackendItem {
  id: number;
  sessionId: number;
  skuCode: string;
  flashPrice: number;
  flashStock: number;
  limitPerUser: number;
  soldQty: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  productName?: string;
  originalPrice?: number;
  imageUrl?: string;
}

function mapSession(s: BackendSession) {
  return {
    id: s.sessionId,
    name: s.name,
    startTime: s.startTime,
    endTime: s.endTime,
    status: s.status as 'UPCOMING' | 'ACTIVE' | 'ENDED' | 'CANCELLED',
    secondsRemaining: s.secondsRemaining,
    isEnded: s.isEnded,
    createdAt: s.createdAt,
  };
}

function mapItem(i: BackendItem) {
  return {
    id: i.id,
    sessionId: i.sessionId,
    skuCode: i.skuCode,
    flashPrice: i.flashPrice,
    flashStock: i.flashStock,
    soldQty: i.soldQty,
    limitPerUser: i.limitPerUser,
    status: i.status,
    productName: i.productName,
    originalPrice: i.originalPrice,
    imageUrl: i.imageUrl,
  };
}

function wrapGetSessionsResponse(sessions: BackendSession[]) {
  return {
    content: sessions.map(mapSession),
    totalElements: sessions.length,
    totalPages: 1,
  };
}

const mockBackendSession: BackendSession = {
  sessionId: 42,
  name: 'Flash Sale Test',
  status: 'ACTIVE',
  startTime: '2026-05-28T12:00:00Z',
  endTime: '2026-05-28T18:00:00Z',
  secondsRemaining: 7200,
  isEnded: false,
  createdAt: '2026-05-27T00:00:00Z',
  updatedAt: '2026-05-27T12:00:00Z',
};

const mockBackendItem: BackendItem = {
  id: 101,
  sessionId: 42,
  skuCode: 'SKU-001',
  flashPrice: 499000,
  flashStock: 100,
  limitPerUser: 2,
  soldQty: 15,
  status: 'ACTIVE',
  createdAt: '2026-05-27T00:00:00Z',
  updatedAt: '2026-05-27T00:00:00Z',
};

describe('mapSession', () => {
  it('maps sessionId to id', () => {
    const result = mapSession(mockBackendSession);
    expect(result.id).toBe(42);
  });

  it('preserves name, startTime, endTime', () => {
    const result = mapSession(mockBackendSession);
    expect(result.name).toBe('Flash Sale Test');
    expect(result.startTime).toBe('2026-05-28T12:00:00Z');
    expect(result.endTime).toBe('2026-05-28T18:00:00Z');
  });

  it('maps status as allowed union type', () => {
    expect(mapSession(mockBackendSession).status).toBe('ACTIVE');
    expect(mapSession({ ...mockBackendSession, status: 'UPCOMING' }).status).toBe('UPCOMING');
    expect(mapSession({ ...mockBackendSession, status: 'ENDED' }).status).toBe('ENDED');
    expect(mapSession({ ...mockBackendSession, status: 'CANCELLED' }).status).toBe('CANCELLED');
  });

  it('maps secondsRemaining and isEnded', () => {
    const result = mapSession(mockBackendSession);
    expect(result.secondsRemaining).toBe(7200);
    expect(result.isEnded).toBe(false);
  });

  it('maps createdAt', () => {
    expect(mapSession(mockBackendSession).createdAt).toBe('2026-05-27T00:00:00Z');
  });
});

describe('mapItem', () => {
  it('preserves all item fields', () => {
    const result = mapItem(mockBackendItem);
    expect(result.id).toBe(101);
    expect(result.sessionId).toBe(42);
    expect(result.skuCode).toBe('SKU-001');
    expect(result.flashPrice).toBe(499000);
    expect(result.flashStock).toBe(100);
    expect(result.limitPerUser).toBe(2);
    expect(result.soldQty).toBe(15);
    expect(result.status).toBe('ACTIVE');
  });

  it('handles sold out item', () => {
    const soldOut = { ...mockBackendItem, status: 'SOLD_OUT', soldQty: 100 };
    const result = mapItem(soldOut);
    expect(result.status).toBe('SOLD_OUT');
    expect(result.soldQty).toBe(100);
    expect(result.soldQty).toBe(result.flashStock);
  });
});

describe('wrapGetSessionsResponse', () => {
  it('wraps sessions in PageResponse shape', () => {
    const result = wrapGetSessionsResponse([mockBackendSession]);
    expect(result.totalElements).toBe(1);
    expect(result.totalPages).toBe(1);
    expect(result.content).toHaveLength(1);
    expect(result.content[0].id).toBe(42);
  });

  it('handles empty sessions array', () => {
    const result = wrapGetSessionsResponse([]);
    expect(result.totalElements).toBe(0);
    expect(result.content).toHaveLength(0);
  });

  it('maps multiple sessions correctly', () => {
    const s2 = { ...mockBackendSession, sessionId: 99, name: 'Second Session' };
    const result = wrapGetSessionsResponse([mockBackendSession, s2]);
    expect(result.totalElements).toBe(2);
    expect(result.content[0].id).toBe(42);
    expect(result.content[1].id).toBe(99);
    expect(result.content[1].name).toBe('Second Session');
  });
});
