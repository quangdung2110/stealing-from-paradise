import { type OrderStatus } from '@shared/api/order.api';
import { getStatusMeta } from '@/lib/orderStatus';

const STATUS_FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: getStatusMeta('PENDING').label },
  { value: 'PAID', label: getStatusMeta('PAID').label },
  { value: 'SHIPPING', label: getStatusMeta('SHIPPING').label },
  { value: 'DELIVERED', label: getStatusMeta('DELIVERED').label },
  { value: 'CANCELLED', label: getStatusMeta('CANCELLED').label },
  { value: 'PARTIALLY_REFUNDED', label: getStatusMeta('PARTIALLY_REFUNDED').label },
  { value: 'REFUNDED', label: getStatusMeta('REFUNDED').label },
];

interface OrderFiltersProps {
  filter: OrderStatus | 'ALL';
  onFilterChange: (f: OrderStatus | 'ALL') => void;
}

export default function OrderFilters({ filter, onFilterChange }: OrderFiltersProps) {
  return (
    <div className="flex flex-wrap gap-2 mb-5">
      {STATUS_FILTERS.map((f) => (
        <button
          key={f.value}
          onClick={() => onFilterChange(f.value)}
          className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            filter === f.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}
        >
          {f.label}
        </button>
      ))}
    </div>
  );
}
