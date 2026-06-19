import apiClient from '../lib/axios';

/** Matches NotificationService backend: raw Notification objects, no ApiResponse wrapper */
export interface Notification {
  id: string;
  userId: number;
  type: string;
  title: string;
  message: string;
  data?: Record<string, any>;
  read: boolean;
  createdAt: string;
}

type NotificationRecord = Record<string, any>;

function isRecord(value: unknown): value is NotificationRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseNotificationData(value: unknown): NotificationRecord | undefined {
  if (!value) return undefined;
  if (isRecord(value)) return value;
  if (typeof value !== 'string') return undefined;

  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

export function normalizeNotification(raw: unknown): Notification {
  const source = isRecord(raw) ? raw : {};
  const data = parseNotificationData(source.data ?? source.metadata);
  const payload = isRecord(data?.payload) ? data.payload : undefined;

  return {
    id: String(source.id ?? source.notificationId ?? source.notification_id ?? ''),
    userId: Number(source.userId ?? source.user_id ?? payload?.userId ?? payload?.user_id ?? data?.userId ?? data?.user_id ?? 0),
    type: String(source.type ?? payload?.type ?? data?.type ?? 'INFO'),
    title: String(source.title ?? payload?.title ?? data?.title ?? ''),
    message: String(source.message ?? source.body ?? payload?.message ?? payload?.body ?? data?.message ?? data?.body ?? ''),
    data: data ? { ...data, ...(payload ?? {}) } : undefined,
    read: Boolean(source.read ?? source.isRead ?? source.is_read ?? false),
    createdAt: String(source.createdAt ?? source.created_at ?? new Date().toISOString()),
  };
}

export const notificationApi = {
  /** Get notifications (raw array — no ApiResponse wrapper from reactive service) */
  getNotifications: async (params?: { page?: number; size?: number }): Promise<Notification[]> => {
    const res = await apiClient.get<unknown[]>('/notifications', { params });
    return Array.isArray(res.data) ? res.data.map(normalizeNotification) : [];
  },

  /** Mark single notification as read */
  markAsRead: (notifId: string) =>
    apiClient.patch<Notification>(`/notifications/${notifId}/read`),

  /** Mark all notifications as read */
  markAllAsRead: () =>
    apiClient.patch<{ success: boolean; updated_count: number; user_id: number }>('/notifications/read-all'),

  /** Get unread notification count */
  getUnreadCount: async (): Promise<number> => {
    const res = await apiClient.get<{ user_id: number; unread_count: number }>('/notifications/unread-count');
    return res.data.unread_count ?? 0;
  },
};
