import { cn } from './cn';

/** Indeterminate loading spinner. Inherits color via `currentColor`. */
export default function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={cn('inline-block border-2 border-current border-t-transparent rounded-full animate-spin', className ?? 'w-5 h-5')}
      aria-hidden="true"
    />
  );
}
