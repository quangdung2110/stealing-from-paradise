import { cn } from './cn';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  hoverable?: boolean;
  padded?: boolean;
}

/** Standard surface: white, rounded-2xl, soft `shadow-card` elevation. */
export default function Card({ hoverable, padded = true, className, children, ...rest }: CardProps) {
  return (
    <div
      className={cn(
        'bg-white rounded-2xl border border-gray-100 shadow-card',
        hoverable && 'transition-shadow hover:shadow-card-hover',
        padded && 'p-5',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  );
}
