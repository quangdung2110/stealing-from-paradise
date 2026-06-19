import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { authCookies } from '../utils/cookie';
import { useNotificationStore } from '../store/notificationStore';
import { useAuthStore } from '../store/authStore';
import { isMockMode } from '../api/mock';
import { handleAuthFailure } from '../lib/axios';
import type { Notification } from '../api/notification.api';
import { getNotificationTarget } from '../utils/notificationRouting';

export default function NotificationBell() {
  const { user } = useAuthStore();
  const navigate = useNavigate();
  const {
    notifications,
    unreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    fetchUnreadCount,
    addNotification,
  } = useNotificationStore();

  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (!user?.userId) return;

    fetchUnreadCount();
    fetchNotifications({ page: 0, size: 20 });

    if (isMockMode()) {
      const interval = setInterval(() => {
        if (Math.random() < 0.3) {
          const id = 'mock_notif_' + Date.now();
          const newNotif = {
            id,
            userId: user.userId,
            type: 'INFO',
            title: 'Cập nhật hệ thống',
            message: `Hệ thống ghi nhận hoạt động mới trên tài khoản của bạn (${new Date().toLocaleTimeString()}).`,
            read: false,
            createdAt: new Date().toISOString(),
          };
          addNotification(newNotif);
        }
      }, 20000);
      return () => clearInterval(interval);
    }

    let active = true;
    let abortController = new AbortController();

    const connectSSE = async () => {
      const baseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';
      const streamUrl = `${baseUrl}/notifications/stream`;

      try {
        const response = await fetch(streamUrl, {
          headers: {
            'Authorization': `Bearer ${authCookies.get('accessToken') ?? ''}`,
            'X-User-Id': String(user.userId),
            'Accept': 'text/event-stream',
          },
          signal: abortController.signal,
        });

        if (!response.ok) {
          console.warn('Notification SSE connection failed:', response.status);
          if (response.status === 401) {
            handleAuthFailure();
            return;
          }
          if (active) setTimeout(connectSSE, 5000);
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) return;

        const decoder = new TextDecoder();
        let buffer = '';

        while (active) {
          const { value, done } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            const cleaned = line.trim();
            if (cleaned.startsWith('data:')) {
              try {
                const dataStr = cleaned.slice(5).trim();
                if (dataStr) {
                  const notification = JSON.parse(dataStr) as { id: string };
                  if (notification.id) {
                    addNotification(notification as any);
                  }
                }
              } catch {
                // skip malformed SSE lines
              }
            }
          }
        }
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          console.warn('Notification SSE disconnected, retrying in 5s...');
          if (active) setTimeout(connectSSE, 5000);
        }
      }
    };

    connectSSE();

    return () => {
      active = false;
      abortController.abort();
    };
  }, [user?.userId, addNotification, fetchNotifications, fetchUnreadCount]);

  const handleToggle = () => {
    setIsOpen(!isOpen);
  };

  const handleNotificationClick = async (notif: Notification) => {
    if (!notif.read) {
      await markAsRead(notif.id);
    }

    const target = getNotificationTarget(notif, user?.role);
    if (target) {
      setIsOpen(false);
      navigate(target);
    }
  };

  const formatTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMins / 60);

      if (diffMins < 1) return 'Vừa xong';
      if (diffMins < 60) return `${diffMins} phút trước`;
      if (diffHours < 24) return `${diffHours} giờ trước`;
      return date.toLocaleDateString('vi-VN', { day: 'numeric', month: 'numeric' });
    } catch {
      return '';
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Bell Icon Trigger */}
      <button
        onClick={handleToggle}
        className="relative p-2 rounded-xl text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-blue-500/20"
        aria-label="Thông báo"
      >
        <svg
          className="w-6 h-6"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>

        {unreadCount > 0 && (
          <span className="absolute top-1.5 right-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white ring-2 ring-white animate-pulse">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown Panel */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-2xl border border-gray-200 shadow-xl z-50 overflow-hidden transform origin-top-right transition-all duration-200">
          <div className="p-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 text-base">Thông báo</h3>
            {unreadCount > 0 && (
              <button
                onClick={() => markAllAsRead()}
                className="text-xs font-medium text-blue-600 hover:text-blue-700 transition-colors"
              >
                Đánh dấu đã đọc tất cả
              </button>
            )}
          </div>

          <div className="max-h-[350px] overflow-y-auto divide-y divide-gray-50 scrollbar-thin">
            {notifications.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                <svg
                  className="w-10 h-10 mx-auto mb-2 text-gray-300"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M9.143 17.082a24.248 24.248 0 003.844.148m-3.844-.148a23.856 23.856 0 01-5.08-1.503m18.2 1.955a24.298 24.298 0 00-3.844-.148m3.844.148a23.856 23.856 0 005.08-1.503m-18.2 1.955a24.018 24.018 0 013.2-7.753m15 7.753a24.018 24.018 0 00-3.2-7.753M0 13.872c.5 1.432 1.584 2.56 3.002 3.002m0 0a24.284 24.284 0 003.447.451m11.1 0a24.284 24.284 0 003.447-.451m0 0a3.002 3.002 0 003.002-3.002M0 13.872v-1.11c0-2.678 1.488-5.11 3.86-6.353m16.28 0a8.002 8.002 0 013.86 6.353v1.11"
                  />
                </svg>
                <p className="text-sm font-medium">Không có thông báo nào</p>
                <p className="text-xs text-gray-400 mt-1">Chúng tôi sẽ báo cho bạn khi có tin mới.</p>
              </div>
            ) : (
              notifications.map((notif) => (
                <div
                  key={notif.id}
                  onClick={() => handleNotificationClick(notif)}
                  className={`p-4 flex items-start gap-3 hover:bg-gray-50 transition-colors cursor-pointer ${
                    !notif.read ? 'bg-blue-50/40' : ''
                  }`}
                >
                  {/* Indicator Dot */}
                  <div className="mt-1.5 shrink-0">
                    <span
                      className={`block w-2.5 h-2.5 rounded-full ${
                        !notif.read ? 'bg-blue-500' : 'bg-transparent border border-gray-300'
                      }`}
                    />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <p className={`text-sm text-gray-900 truncate ${!notif.read ? 'font-semibold' : 'font-medium'}`}>
                        {notif.title}
                      </p>
                      <span className="text-[10px] text-gray-400 whitespace-nowrap">
                        {formatTime(notif.createdAt)}
                      </span>
                    </div>
                    <p className="text-xs text-gray-600 mt-1 leading-relaxed break-words line-clamp-2">
                      {notif.message}
                    </p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
