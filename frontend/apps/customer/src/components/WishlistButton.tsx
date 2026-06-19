import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@shared/store/authStore';
import { useWishlistStore } from '@shared/store/wishlistStore';

interface WishlistButtonProps {
  productId: string;
  className?: string;
}

/**
 * Nút trái tim toggle yêu thích — optimistic (đổi màu ngay, hoàn tác nếu lỗi).
 * Khách chưa đăng nhập bấm sẽ được đưa về /login.
 */
export default function WishlistButton({ productId, className = '' }: WishlistButtonProps) {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const wished = useWishlistStore((s) => s.ids.has(productId));
  const toggle = useWishlistStore((s) => s.toggle);

  return (
    <button
      type="button"
      aria-label={wished ? 'Bỏ yêu thích' : 'Thêm vào yêu thích'}
      title={wished ? 'Bỏ yêu thích' : 'Thêm vào yêu thích'}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!isAuthenticated) {
          navigate('/login');
          return;
        }
        toggle(productId);
      }}
      className={`w-9 h-9 rounded-full bg-white/90 shadow flex items-center justify-center text-base transition-transform hover:scale-110 ${className}`}
    >
      {wished ? '❤️' : '🤍'}
    </button>
  );
}
