import { cn } from './cn';
import { Icon, type IconName } from '../icons';

interface EmptyStateProps {
  iconKey?: IconName;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

/** Friendly placeholder for empty lists / no-results states. */
export default function EmptyState({ iconKey, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center text-center py-12 px-4', className)}>
      {iconKey && (
        <div className="w-14 h-14 rounded-2xl bg-gray-100 flex items-center justify-center text-gray-400 mb-4">
          <Icon name={iconKey} className="w-7 h-7" />
        </div>
      )}
      <h3 className="text-base font-semibold text-gray-900">{title}</h3>
      {description && <p className="text-sm text-gray-500 mt-1 max-w-sm">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
