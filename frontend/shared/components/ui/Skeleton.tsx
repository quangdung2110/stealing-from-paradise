import { cn } from './cn';

/** Placeholder block shown while content loads. Sized via className. */
export default function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse bg-gray-200 rounded-lg', className)} />;
}
