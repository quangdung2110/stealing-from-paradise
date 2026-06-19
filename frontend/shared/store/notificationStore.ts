import { create } from 'zustand';
import { notificationApi, normalizeNotification, type Notification } from '../api/notification.api';

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  isLoading: boolean;
  error: string | null;

  fetchNotifications: (params?: { page?: number; size?: number }) => Promise<void>;
  fetchNotificationsPage: (params?: { page?: number; size?: number }) => Promise<Notification[]>;
  markAsRead: (notifId: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  fetchUnreadCount: () => Promise<void>;
  addNotification: (notif: Notification) => void;
  setUnreadCount: (count: number) => void;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  isLoading: false,
  error: null,

  fetchNotifications: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const notifications = await notificationApi.getNotifications(params);
      set({ notifications: notifications || [], isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch notifications',
        isLoading: false,
      });
    }
  },

  fetchNotificationsPage: async (params) => {
    try {
      const notifications = await notificationApi.getNotifications(params);
      return notifications || [];
    } catch {
      return [];
    }
  },

  markAsRead: async (notifId) => {
    try {
      await notificationApi.markAsRead(notifId);
      set((state) => {
        const wasUnread = state.notifications.find((n) => n.id === notifId)?.read === false;
        return {
          notifications: state.notifications.map((n) =>
            n.id === notifId ? { ...n, read: true } : n
          ),
          unreadCount: wasUnread ? Math.max(0, state.unreadCount - 1) : state.unreadCount,
        };
      });
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Failed to mark notification as read' });
    }
  },

  markAllAsRead: async () => {
    try {
      await notificationApi.markAllAsRead();
      set((state) => ({
        notifications: state.notifications.map((n) => ({ ...n, read: true })),
        unreadCount: 0,
      }));
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Failed to mark all notifications as read' });
    }
  },

  fetchUnreadCount: async () => {
    try {
      const count = await notificationApi.getUnreadCount();
      set({ unreadCount: count ?? 0 });
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Failed to fetch unread count' });
    }
  },

  addNotification: (notif) => {
    const normalized = normalizeNotification(notif);
    set((state) => {
      if (state.notifications.some((n) => n.id === normalized.id)) {
        return state;
      }
      return {
        notifications: [normalized, ...state.notifications],
        unreadCount: state.unreadCount + (normalized.read ? 0 : 1),
      };
    });
  },

  setUnreadCount: (count) => set({ unreadCount: count }),
}));
