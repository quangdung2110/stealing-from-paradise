import { vi, describe, it, expect, beforeEach } from 'vitest';

// Mock the notification API so the store tests stay pure (no network).
vi.mock('../api/notification.api', () => ({
  normalizeNotification: (notif: any) => ({
    id: String(notif.id ?? ''),
    userId: Number(notif.userId ?? notif.user_id ?? 0),
    type: notif.type ?? 'INFO',
    title: notif.title ?? '',
    message: notif.message ?? notif.body ?? '',
    data: notif.data ?? notif.metadata,
    read: Boolean(notif.read ?? notif.isRead ?? false),
    createdAt: notif.createdAt ?? notif.created_at ?? '2026-01-01T00:00:00Z',
  }),
  notificationApi: {
    getNotifications: vi.fn(),
    markAsRead: vi.fn(() => Promise.resolve({ data: {} })),
    markAllAsRead: vi.fn(() => Promise.resolve({ data: {} })),
    getUnreadCount: vi.fn(),
  },
}));

import { useNotificationStore } from '../store/notificationStore';
import { notificationApi } from '../api/notification.api';

const notif = (id: string, read = false) => ({
  id, userId: 1, type: 'ORDER_STATUS', title: 't', message: 'm', read, createdAt: '2026-01-01T00:00:00Z',
});

beforeEach(() => {
  vi.clearAllMocks();
  useNotificationStore.setState({ notifications: [], unreadCount: 0, isLoading: false, error: null });
});

describe('notificationStore.addNotification (SSE → bell)', () => {
  it('prepends a new notification and increments unread when unread', () => {
    useNotificationStore.getState().addNotification(notif('a'));
    const s = useNotificationStore.getState();
    expect(s.notifications[0].id).toBe('a');
    expect(s.unreadCount).toBe(1);
  });

  it('does not increment unread for an already-read notification', () => {
    useNotificationStore.getState().addNotification(notif('a', true));
    expect(useNotificationStore.getState().unreadCount).toBe(0);
  });

  it('de-duplicates by id (idempotent SSE delivery)', () => {
    const { addNotification } = useNotificationStore.getState();
    addNotification(notif('a'));
    addNotification(notif('a'));
    const s = useNotificationStore.getState();
    expect(s.notifications).toHaveLength(1);
    expect(s.unreadCount).toBe(1);
  });
});

describe('notificationStore — read state', () => {
  it('markAsRead flips one notification and decrements unread', async () => {
    useNotificationStore.setState({ notifications: [notif('a'), notif('b')], unreadCount: 2 });
    await useNotificationStore.getState().markAsRead('a');
    const s = useNotificationStore.getState();
    expect(s.notifications.find(n => n.id === 'a')?.read).toBe(true);
    expect(s.unreadCount).toBe(1);
    expect(notificationApi.markAsRead).toHaveBeenCalledWith('a');
  });

  it('markAllAsRead clears unread', async () => {
    useNotificationStore.setState({ notifications: [notif('a'), notif('b')], unreadCount: 2 });
    await useNotificationStore.getState().markAllAsRead();
    const s = useNotificationStore.getState();
    expect(s.notifications.every(n => n.read)).toBe(true);
    expect(s.unreadCount).toBe(0);
  });
});

describe('notificationStore — fetch', () => {
  it('fetchUnreadCount stores unread_count', async () => {
    (notificationApi.getUnreadCount as any).mockResolvedValue(5);
    await useNotificationStore.getState().fetchUnreadCount();
    expect(useNotificationStore.getState().unreadCount).toBe(5);
  });

  it('fetchNotifications stores list', async () => {
    (notificationApi.getNotifications as any).mockResolvedValue([notif('a'), notif('b')]);
    await useNotificationStore.getState().fetchNotifications({ page: 0, size: 20 });
    expect(useNotificationStore.getState().notifications).toHaveLength(2);
  });
});
