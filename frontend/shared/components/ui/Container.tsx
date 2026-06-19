import { cn } from './cn';

/** Centered max-width page container with standard horizontal padding. */
export default function Container({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn('max-w-7xl mx-auto px-4 sm:px-6 lg:px-8', className)}>{children}</div>;
}
