import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import NotificationBell from '@shared/components/NotificationBell';
import { handleAuthFailure } from '../../../../../shared/lib/axios';
import { authCookies } from '../../../../../shared/utils/cookie';
import type { Notification } from '../api/notification.api';

// Hoisted store doubles.
const h = vi.hoisted(() => ({
  store: {
    notifications: [
      { id: 'n1', userId: 1, type: 'ORDER_STATUS', title: 'Đơn mới', message: 'Bạn có đơn mới', read: false, createdAt: new Date().toISOString() },
    ],
    unreadCount: 2,
    fetchNotifications: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    fetchUnreadCount: vi.fn(),
    addNotification: vi.fn(),
  },
  authState: { user: null as any },
}));

vi.mock('../../../../../shared/store/notificationStore', () => ({ useNotificationStore: () => h.store }));
vi.mock('../../../../../shared/store/authStore', () => ({ useAuthStore: () => h.authState }));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock('../../../../../shared/lib/axios', () => ({
  handleAuthFailure: vi.fn(),
}));

// Vitest không set VITE_API_URL nên isMockMode() mặc định true → component rẽ nhánh mock,
// không bao giờ connect SSE thật. Ép về false để test đường SSE.
vi.mock('../../../../../shared/api/mock', async (importOriginal) => ({
  ...(await importOriginal() as any),
  isMockMode: () => false,
}));

const originalFetch = global.fetch;

beforeEach(() => {
  vi.clearAllMocks();
  h.authState.user = null; // Reset to default
  global.fetch = vi.fn();
  if (typeof window !== 'undefined') {
    delete (window as any).location;
    window.location = new URL('http://localhost/seller') as any;
  }
  authCookies.set('accessToken', 'mock-token');
});

afterEach(() => {
  global.fetch = originalFetch;
  authCookies.remove('accessToken');
});

describe('NotificationBell', () => {
  it('renders the unread badge count', () => {
    render(<NotificationBell />);
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('opens the dropdown and marks a notification read on click', () => {
    render(<NotificationBell />);
    fireEvent.click(screen.getByLabelText('Thông báo'));
    fireEvent.click(screen.getByText('Đơn mới'));
    expect(h.store.markAsRead).toHaveBeenCalledWith('n1');
  });

  it('marks all as read', () => {
    render(<NotificationBell />);
    fireEvent.click(screen.getByLabelText('Thông báo'));
    fireEvent.click(screen.getByText(/Đánh dấu đã đọc tất cả/i));
    expect(h.store.markAllAsRead).toHaveBeenCalled();
  });

  it('connects to SSE, reads stream, parses notification and adds it to store', async () => {
    h.authState.user = { userId: 1 };

    let controller: ReadableStreamDefaultController | null = null;
    const stream = new ReadableStream({
      start(c) {
        controller = c;
      }
    });

    const mockResponse = {
      ok: true,
      status: 200,
      body: stream,
    };

    (global.fetch as any).mockResolvedValue(mockResponse);

    render(<NotificationBell />);

    await waitFor(() => expect(global.fetch).toHaveBeenCalled());

    const encoder = new TextEncoder();
    const notificationPayload = {
      id: 'n_sse_1',
      title: 'SSE Title',
      message: 'SSE Message',
      read: false,
      createdAt: new Date().toISOString(),
    };

    await act(async () => {
      controller?.enqueue(encoder.encode(`data: ${JSON.stringify(notificationPayload)}\n`));
    });

    await waitFor(() => {
      expect(h.store.addNotification).toHaveBeenCalledWith(expect.objectContaining({
        id: 'n_sse_1',
        title: 'SSE Title',
      }));
    });

    await act(async () => {
      controller?.close();
    });
  });

  it('calls handleAuthFailure on 401 response status', async () => {
    h.authState.user = { userId: 1 };

    const mockResponse = {
      ok: false,
      status: 401,
    };

    (global.fetch as any).mockResolvedValue(mockResponse);

    render(<NotificationBell />);

    await waitFor(() => {
      expect(handleAuthFailure).toHaveBeenCalled();
    });
  });

  it('retries SSE connection after 5 seconds on non-401 failure', async () => {
    vi.useFakeTimers();
    try {
      h.authState.user = { userId: 1 };

      const mockResponse = {
        ok: false,
        status: 500,
      };

      (global.fetch as any).mockResolvedValue(mockResponse);

      render(<NotificationBell />);

      // Flush microtasks and timers to execute the first fetch call and the 5s retry
      await act(async () => {
        await vi.runOnlyPendingTimersAsync();
      });

      expect(global.fetch).toHaveBeenCalledTimes(2);

      // Advance timers by another 5000ms to trigger the second retry (third call)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });

      expect(global.fetch).toHaveBeenCalledTimes(3);
    } finally {
      vi.useRealTimers();
    }
  });
});
