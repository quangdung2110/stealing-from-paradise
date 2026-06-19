/** Format number to VND currency string */
export const fmtVnd = (n: number): string => n.toLocaleString('vi-VN') + '₫';

/** Format date ISO string to Vietnamese locale */
export const fmtDate = (iso?: string, withTime = false): string => {
  if (!iso) return '—';
  const opts: Intl.DateTimeFormatOptions = {
    day: '2-digit', month: '2-digit', year: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  };
  return new Date(iso).toLocaleDateString('vi-VN', opts);
};

/** Format date-time ISO string to Vietnamese locale with time */
export const fmtDateTime = (iso?: string): string => fmtDate(iso, true);

/** Convert ISO string to datetime-local input value */
export const toLocalDatetime = (iso?: string): string => {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
