import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotificationStore } from '@shared/store/notificationStore';
import { useAuthStore } from '@shared/store/authStore';
import type { Notification } from '@shared/api/notification.api';
import { getNotificationTarget } from '@shared/utils/notificationRouting';

function formatTime(isoString: string) {
  try {
    const date = new Date(isoString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return 'Vừa xong';
    if (diffMins < 60) return `${diffMins} phút trước`;
    if (diffHours < 24) return `${diffHours} giờ trước`;
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return date.toLocaleDateString('vi-VN', { day: 'numeric', month: 'short', year: 'numeric' });
  } catch {
    return '';
  }
}

function getNotificationIcon(type: string) {
  switch (type) {
    case 'ORDER_SHIPPED': return '📦';
    case 'ORDER_DELIVERED': return '✅';
    case 'ORDER_CANCELLED': return '❌';
    case 'REFUND_APPROVED': return '💰';
    case 'REFUND_REJECTED': return '🚫';
    case 'ORDER_CREATED': return '🛒';
    case 'FLASH_SALE': return '⚡';
    case 'PROMO': return '🎁';
    default: return '🔔';
  }
}

function NotificationItem({ notif, onOpen }: { notif: Notification; onOpen: (notif: Notification) => void }) {
  return (
    <div
      onClick={() => onOpen(notif)}
      className={`flex items-start gap-4 p-4 hover:bg-gray-50 transition-colors cursor-pointer border-b border-gray-50 last:border-0 ${
        !notif.read ? 'bg-blue-50/30' : ''
      }`}
    >
      <span className="text-2xl shrink-0 mt-0.5">{getNotificationIcon(notif.type)}</span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <p className={`text-sm ${!notif.read ? 'font-semibold text-gray-900' : 'font-medium text-gray-700'}`}>
            {notif.title}
          </p>
          <span className="text-xs text-gray-400 whitespace-nowrap shrink-0">
            {formatTime(notif.createdAt)}
          </span>
        </div>
        <p className="text-xs text-gray-500 mt-1 leading-relaxed line-clamp-2">
          {notif.message}
        </p>
        {!notif.read && (
          <span className="inline-block mt-2 px-2 py-0.5 bg-blue-100 text-blue-700 text-xs font-medium rounded-full">
            Mới
          </span>
        )}
      </div>
    </div>
  );
}

export default function NotificationsPage() {
  const { user } = useAuthStore();
  const navigate = useNavigate();
  const {
    notifications,
    unreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    fetchUnreadCount,
  } = useNotificationStore();

  useEffect(() => {
    if (user?.userId) {
      fetchUnreadCount();
      fetchNotifications({ page: 0, size: 50 });
    }
  }, [user?.userId]);

  const handleOpenNotification = async (notif: Notification) => {
    if (!notif.read) {
      await markAsRead(notif.id);
    }

    const target = getNotificationTarget(notif, user?.role);
    if (target) {
      navigate(target);
    }
  };

  const handleMarkAll = async () => {
    await markAllAsRead();
  };

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Thông báo</h1>
          <p className="text-gray-500 mt-1 text-sm">
            {unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : 'Tất cả thông báo'}
          </p>
        </div>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAll}
            className="px-4 py-2 text-sm font-medium text-blue-600 hover:text-blue-700 hover:bg-blue-50 rounded-xl transition-colors"
          >
            Đánh dấu đã đọc tất cả
          </button>
        )}
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        {notifications.length === 0 ? (
          <div className="py-20 text-center">
            <span className="text-5xl block mb-4">🔔</span>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Không có thông báo nào</h3>
            <p className="text-gray-500 text-sm">Chúng tôi sẽ báo cho bạn khi có tin mới.</p>
          </div>
        ) : (
          notifications.map((notif) => (
            <NotificationItem key={notif.id} notif={notif} onOpen={handleOpenNotification} />
          ))
        )}
      </div>
    </div>
  );
}
