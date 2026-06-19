import { Toaster, toast, type ExternalToast } from 'sonner';
import type { ReactNode } from 'react';

/**
 * Lớp bọc toast dùng chung cho cả 3 app (customer/seller/admin).
 * App chỉ import `notify` / `<AppToaster />` từ đây, không import trực tiếp
 * `sonner` — giữ coupling thấp, đổi thư viện sau này chỉ sửa 1 chỗ.
 */
export const notify = {
  success: (message: ReactNode, opts?: ExternalToast) => toast.success(message, opts),
  error: (message: ReactNode, opts?: ExternalToast) => toast.error(message, opts),
  info: (message: ReactNode, opts?: ExternalToast) => toast(message, opts),
  warning: (message: ReactNode, opts?: ExternalToast) => toast.warning(message, opts),
  loading: (message: ReactNode, opts?: ExternalToast) => toast.loading(message, opts),
  promise: toast.promise.bind(toast),
  dismiss: toast.dismiss.bind(toast),
};

/** Mount một lần ở root mỗi app (main.tsx). */
export function AppToaster() {
  return (
    <Toaster
      position="top-right"
      richColors
      closeButton
      expand
      toastOptions={{ duration: 3500 }}
    />
  );
}
