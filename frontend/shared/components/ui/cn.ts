/** Join conditional className parts. Tiny stand-in for clsx (no dependency). */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}
