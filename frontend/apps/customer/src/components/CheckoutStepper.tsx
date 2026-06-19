import { Icon, type IconName } from '@shared/components/icons';
import { cn } from '@shared/components/ui/cn';

export type CheckoutStepId = 'cart' | 'review' | 'payment' | 'complete';

const CHECKOUT_STEPS: Array<{
  id: CheckoutStepId;
  label: string;
  icon: IconName;
}> = [
  { id: 'cart', label: 'Giỏ hàng', icon: 'cart' },
  { id: 'review', label: 'Xem lại', icon: 'receipt' },
  { id: 'payment', label: 'Thanh toán', icon: 'card' },
  { id: 'complete', label: 'Hoàn tất', icon: 'checkBadge' },
];

interface CheckoutStepperProps {
  currentStep: CheckoutStepId;
  className?: string;
}

export default function CheckoutStepper({ currentStep, className }: CheckoutStepperProps) {
  const currentIndex = CHECKOUT_STEPS.findIndex(step => step.id === currentStep);

  return (
    <nav
      aria-label="Tiến trình thanh toán"
      className={cn('rounded-2xl border border-gray-100 bg-white px-3 py-4 shadow-sm sm:px-5', className)}
    >
      <ol className="grid grid-cols-4 gap-2 sm:gap-3">
        {CHECKOUT_STEPS.map((step, index) => {
          const isComplete = index < currentIndex;
          const isCurrent = index === currentIndex;

          return (
            <li key={step.id} className="relative flex min-w-0 flex-col items-center text-center">
              {index > 0 && (
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute left-[-50%] top-5 h-0.5 w-full sm:top-6',
                    isComplete || isCurrent ? 'bg-blue-500' : 'bg-gray-200',
                  )}
                />
              )}
              <span
                aria-current={isCurrent ? 'step' : undefined}
                className={cn(
                  'relative z-10 flex h-10 w-10 items-center justify-center rounded-full border-2 bg-white transition-colors sm:h-12 sm:w-12',
                  isComplete && 'border-blue-500 bg-blue-500 text-white',
                  isCurrent && 'border-blue-600 bg-blue-50 text-blue-700',
                  !isComplete && !isCurrent && 'border-gray-200 text-gray-400',
                )}
              >
                {isComplete ? (
                  <Icon name="checkBadge" className="h-5 w-5" />
                ) : (
                  <Icon name={step.icon} className="h-5 w-5" />
                )}
              </span>
              <span
                className={cn(
                  'mt-2 block max-w-full truncate text-[11px] font-semibold sm:text-sm',
                  isComplete || isCurrent ? 'text-gray-900' : 'text-gray-400',
                )}
              >
                {step.label}
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
