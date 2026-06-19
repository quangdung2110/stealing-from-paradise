import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { orderApi, type OrderSummary, type OrderStatus } from '@shared/api/order.api';
import { Badge, Button, Card, Container, EmptyState, PageHeader, Skeleton, cn, type BadgeProps } from '@shared/components/ui';
import { Icon } from '@shared/components/icons';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PAID', label: 'Đã thanh toán' },
  { value: 'SHIPPING', label: 'Đang giao' },
  { value: 'DELIVERED', label: 'Đã nhận' },
  { value: 'CANCELLED', label: 'Đã huỷ' },
  { value: 'PARTIALLY_REFUNDED', label: 'Hoàn một phần' },
  { value: 'REFUNDED', label: 'Đã hoàn' },
  { value: 'RETURNED', label: 'Hoàn hàng' },
];

const STATUS_META: Record<string, { label: string; tone: BadgeProps['tone']; helper: string }> = {
  PENDING: { label: 'Đang xử lý', tone: 'neutral', helper: 'Đơn hàng đang được xử lý' },
  PAID: { label: 'Đã thanh toán', tone: 'info', helper: 'Đang chuẩn bị giao hàng' },
  SHIPPING: { label: 'Đang giao', tone: 'brand', helper: 'Đơn đang trên đường giao' },
  DELIVERED: { label: 'Đã nhận', tone: 'success', helper: 'Đã giao thành công' },
  CANCELLED: { label: 'Đã huỷ', tone: 'danger', helper: 'Đơn đã bị huỷ' },
  RETURNED: { label: 'Hoàn hàng', tone: 'warning', helper: 'Đang xử lý hoàn hàng' },
  PARTIALLY_REFUNDED: { label: 'Hoàn một phần', tone: 'info', helper: 'Một phần đơn đã được hoàn tiền' },
  REFUNDED: { label: 'Đã hoàn', tone: 'neutral', helper: 'Đơn đã hoàn tiền' },
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function getStatusMeta(status: OrderStatus) {
  return STATUS_META[status] ?? { label: status, tone: 'neutral' as const, helper: status };
}

function OrderHistorySkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 4 }).map((_, index) => (
        <Card key={index} className="flex gap-4">
          <Skeleton className="h-20 w-20 shrink-0 rounded-xl" />
          <div className="min-w-0 flex-1 space-y-3">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-56" />
            <Skeleton className="h-3 w-32" />
          </div>
          <Skeleton className="h-9 w-28 shrink-0" />
        </Card>
      ))}
    </div>
  );
}

function OrderThumb({ order }: { order: OrderSummary }) {
  const firstItem = order.items?.[0];

  if (firstItem?.imageSnapshot) {
    return (
      <div className="relative h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-gray-100">
        <img src={firstItem.imageSnapshot} alt={firstItem.productName || order.orderCode} className="h-full w-full object-cover" />
        {order.itemCount > 1 && (
          <span className="absolute bottom-1 right-1 rounded-full bg-black/65 px-2 py-0.5 text-[11px] font-semibold text-white">
            +{order.itemCount - 1}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className={cn(
      'flex h-20 w-20 shrink-0 items-center justify-center rounded-xl',
      order.isFlashSale ? 'bg-red-50 text-red-600' : 'bg-blue-50 text-blue-600',
    )}>
      <Icon name={order.isFlashSale ? 'bolt' : 'bag'} className="h-8 w-8" />
    </div>
  );
}

function OrderCard({
  order,
  onOpen,
}: {
  order: OrderSummary;
  onOpen: () => void;
}) {
  const meta = STATUS_META[order.status] ?? getStatusMeta(order.status);
  const firstItem = order.items?.[0];
  const sellerLabel = order.sellerName || `Shop #${order.sellerId}`;

  return (
    <Card
      hoverable
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={event => {
        if (event.key === 'Enter' || event.key === ' ') onOpen();
      }}
      className="cursor-pointer"
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 gap-4">
          <OrderThumb order={order} />
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="font-bold text-gray-900">{order.orderCode}</span>
              <Badge tone={meta.tone} dot>{meta.label}</Badge>
              {order.isFlashSale && <Badge tone="danger">Flash Sale</Badge>}
            </div>
            <p className="truncate text-sm font-medium text-gray-700">
              {firstItem?.productName || `${order.itemCount} sản phẩm trong đơn`}
            </p>
            <p className="mt-1 text-sm text-gray-500">
              {sellerLabel} · {order.itemCount} sản phẩm · {formatDate(order.createdAt)}
            </p>
            <p className="mt-2 text-xs font-medium text-blue-600">{meta.helper}</p>
          </div>
        </div>
        <div className="flex items-center justify-between gap-3 sm:block sm:text-right">
          <div>
            <p className="text-xs text-gray-500">Tổng thanh toán</p>
            <p className="font-bold text-gray-900">{fmt(order.finalAmt)}</p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={event => {
              event.stopPropagation();
              onOpen();
            }}
          >
            Chi tiết
          </Button>
        </div>
      </div>
    </Card>
  );
}

export default function OrderHistoryPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['buyer-orders', filter, page],
    queryFn: () =>
      orderApi.getOrders({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: 10,
      }).then(r => r.data.data),
    retry: 1,
    initialData: undefined,
    refetchInterval: (query) => {
      const pageData = query.state.data;
      if (!pageData) return false;
      const hasActiveOrders = pageData.content?.some(o =>
        ['PAID', 'SHIPPING'].includes(o.status)
      );
      return hasActiveOrders ? 10000 : false;
    },
  });

  const orders: OrderSummary[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const handleFilterChange = (newFilter: OrderStatus | 'ALL') => {
    setFilter(newFilter);
    setPage(0);
  };

  return (
    <Container className="max-w-5xl py-8 pb-24 sm:pb-8">
      <PageHeader
        title="Đơn hàng của tôi"
        subtitle={totalElements > 0 ? `${totalElements} đơn hàng` : 'Theo dõi trạng thái và lịch sử mua hàng của bạn'}
        actions={
          <Button type="button" variant="outline" size="sm" loading={isFetching && !isLoading} onClick={() => refetch()}>
            Làm mới
          </Button>
        }
      />

      <div className="mb-6 flex flex-wrap gap-2">
        {STATUS_FILTERS.map(item => (
          <Button
            key={item.value}
            type="button"
            variant={filter === item.value ? 'primary' : 'outline'}
            size="sm"
            className="rounded-full"
            onClick={() => handleFilterChange(item.value)}
          >
            {item.label}
          </Button>
        ))}
      </div>

      {isLoading && <OrderHistorySkeleton />}

      {error && (
        <Card className="border-red-100 bg-red-50">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm font-medium text-red-700">Không thể tải đơn hàng. Vui lòng thử lại.</p>
            <Button type="button" variant="danger" size="sm" onClick={() => refetch()}>
              Tải lại
            </Button>
          </div>
        </Card>
      )}

      {!isLoading && !error && orders.length === 0 && (
        <Card>
          <EmptyState
            iconKey="cube"
            title="Chưa có đơn hàng nào"
            description="Các đơn hàng của bạn sẽ xuất hiện ở đây sau khi đặt mua."
            action={
              <Link to="/products">
                <Button type="button">Khám phá sản phẩm</Button>
              </Link>
            }
          />
        </Card>
      )}

      {!isLoading && !error && orders.length > 0 && (
        <>
          <div className="space-y-3">
            {orders.map(order => (
              <OrderCard
                key={order.orderId}
                order={order}
                onOpen={() => navigate(`/orders/${order.parentOrderId}`)}
              />
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(p => Math.max(0, p - 1))}
              >
                <Icon name="chevronLeft" className="h-4 w-4" />
                Trước
              </Button>
              <span className="text-sm font-medium text-gray-600">
                Trang {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              >
                Sau
                <Icon name="chevronLeft" className="h-4 w-4 rotate-180" />
              </Button>
            </div>
          )}
        </>
      )}
    </Container>
  );
}
