import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi, type SellerVariant, type InventoryLogEntry } from '@shared/api/seller.api';
import { Skeleton } from '@shared/components/ui';
import { fmtVnd as fmt } from '@shared/utils/format';

export default function InventoryPanel({ productId, variants }: { productId: string; variants: SellerVariant[] }) {
  const queryClient = useQueryClient();
  const [adjustSku, setAdjustSku] = useState<string | null>(null);
  const [adjustDelta, setAdjustDelta] = useState(0);
  const [adjustReason, setAdjustReason] = useState('');
  const [adjustError, setAdjustError] = useState('');
  const [restockQty, setRestockQty] = useState<number>(10);
  const [logSku, setLogSku] = useState<string | null>(null);
  const selectedLogVariant = variants.find(v => v.skuCode === logSku);

  const adjustMut = useMutation({
    mutationFn: (data: { skuCode: string; delta: number; reason: string }) =>
      sellerApi.adjustInventory(data),
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      queryClient.invalidateQueries({ queryKey: ['seller-variants', productId] });
      queryClient.invalidateQueries({ queryKey: ['inventory-logs', variables.skuCode] });
      setLogSku(variables.skuCode);
      setAdjustSku(null);
      setAdjustDelta(0);
      setAdjustReason('');
      setRestockQty(10);
    },
    onError: (err: any) => setAdjustError(err?.response?.data?.message || 'Điều chỉnh thất bại'),
  });

  const restockMut = useMutation({
    mutationFn: (data: { skuCode: string; quantity: number; reason: string }) =>
      sellerApi.restockInventory(data.skuCode, { quantity: data.quantity, reason: data.reason }),
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      queryClient.invalidateQueries({ queryKey: ['seller-variants', productId] });
      queryClient.invalidateQueries({ queryKey: ['inventory-logs', variables.skuCode] });
      setLogSku(variables.skuCode);
      setAdjustSku(null);
      setAdjustDelta(0);
      setAdjustReason('');
      setRestockQty(10);
    },
    onError: (err: any) => setAdjustError(err?.response?.data?.message || 'Nhập hàng thất bại'),
  });

  const { data: logs = [], isLoading: logsLoading, isFetching: logsFetching, error: logsQueryError, refetch: refetchLogs } = useQuery({
    queryKey: ['inventory-logs', logSku],
    queryFn: () => sellerApi.getInventoryLogs(logSku!).then(r => r.data.data ?? []),
    enabled: !!logSku,
    retry: 1,
  });
  const logsError = !!logsQueryError;

  if (variants.length === 0) {
    return (
      <div className="text-center py-8 text-gray-400 text-sm">
        Chưa có biến thể nào. Tạo biến thể trước để quản lý tồn kho.
      </div>
    );
  }

  const totalStock = variants.reduce((sum, v) => sum + v.stock, 0);
  const lowStockVariants = variants.filter(v => v.stock > 0 && v.stock < 10);

  return (
    <div className="space-y-3">
      {/* Stock summary */}
      <div className="flex items-center gap-3 bg-gray-50 rounded-xl p-3 text-sm">
        <span className="font-semibold text-gray-900">Tổng tồn kho: {totalStock}</span>
        {lowStockVariants.length > 0 && (
          <span className="text-orange-600">
            · {lowStockVariants.length} biến thể sắp hết
          </span>
        )}
      </div>

      {adjustError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-2">
          {adjustError}
          <button onClick={() => setAdjustError('')} className="ml-2 underline">Đóng</button>
        </div>
      )}

      <div className="space-y-2 max-h-64 overflow-y-auto">
        {variants.map(v => (
          <div key={v.skuCode} className="flex items-center gap-3 p-3 border border-gray-100 rounded-xl bg-white">
            <div className={`w-2 h-10 rounded-full shrink-0 ${v.stock <= 0 ? 'bg-red-400' : v.stock < 10 ? 'bg-orange-400' : 'bg-green-400'}`} />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">{v.variantName}</p>
              <p className="text-xs text-gray-400">
                SKU: {v.skuCode} · Kho: <span className={`font-semibold ${v.stock > 0 ? 'text-green-700' : 'text-red-600'}`}>{v.stock}</span>
                {v.stock <= 0 && <span className="ml-1 text-red-500">(hết)</span>}
                · {fmt(v.price)}
              </p>
            </div>

            <div className="flex gap-1 shrink-0">
              <button onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(-1); setAdjustReason(''); }}
                className="px-2.5 py-1.5 text-xs bg-red-50 text-red-600 rounded-lg hover:bg-red-100 font-medium">-1</button>
              <button onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(1); setAdjustReason(''); }}
                className="px-2.5 py-1.5 text-xs bg-green-50 text-green-600 rounded-lg hover:bg-green-100 font-medium">+1</button>
              <button onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(0); setAdjustReason(''); }}
                className="px-2.5 py-1.5 text-xs bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100 font-medium">±</button>
              <button onClick={() => setLogSku(logSku === v.skuCode ? null : v.skuCode)}
                className={`px-2.5 py-1.5 text-xs rounded-lg font-medium ${logSku === v.skuCode ? 'bg-gray-200 text-gray-700' : 'bg-gray-50 text-gray-500 hover:bg-gray-100'}`}>
                Log
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Adjust modal */}
      {adjustSku && (
        <div className="border border-blue-200 bg-blue-50/30 rounded-xl p-4 mt-3">
          <h4 className="font-semibold text-gray-900 text-sm mb-3">
            Điều chỉnh tồn kho: <span className="font-mono text-blue-700">{adjustSku}</span>
          </h4>
          <div className="space-y-3">
            {/* Restock */}
            <div className="flex items-center gap-2">
              <input type="number" value={restockQty} onChange={e => setRestockQty(Number(e.target.value))}
                placeholder="SL nhập" min="1"
                className="w-24 px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
              <button onClick={() => {
                if (restockQty > 0) restockMut.mutate({ skuCode: adjustSku, quantity: restockQty, reason: adjustReason || 'Nhập hàng' });
              }} disabled={restockMut.isPending || restockQty <= 0}
                className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-xl hover:bg-green-700 disabled:opacity-50">
                {restockMut.isPending ? '...' : `Nhập ${restockQty} sản phẩm`}
              </button>
            </div>

            <div className="flex items-center gap-2">
              <input type="number" value={adjustDelta} onChange={e => setAdjustDelta(Number(e.target.value))}
                placeholder="SL (+/-)" className="w-24 px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <button onClick={() => {
                if (adjustDelta === 0) { setAdjustError('Vui lòng nhập số lượng.'); return; }
                adjustMut.mutate({ skuCode: adjustSku, delta: adjustDelta, reason: adjustReason || 'Điều chỉnh' });
              }} disabled={adjustMut.isPending}
                className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-xl hover:bg-blue-700 disabled:opacity-50">
                {adjustMut.isPending ? '...' : 'Điều chỉnh'}
              </button>
            </div>

            <input type="text" value={adjustReason} onChange={e => setAdjustReason(e.target.value)}
              placeholder="Lý do (vd: Hàng hỏng, kiểm kê...)"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />

            <button onClick={() => { setAdjustSku(null); setAdjustError(''); setRestockQty(10); }}
              className="text-xs text-gray-500 hover:text-gray-700 underline">Đóng</button>
          </div>
        </div>
      )}

      {/* Logs panel */}
      {logSku && (
        <div className="border border-gray-200 rounded-xl p-4 mt-3">
          <div className="flex items-start justify-between gap-3 mb-3">
            <h4 className="font-semibold text-gray-900 text-sm">
              Lịch sử tồn kho: <span className="font-mono text-blue-700">{logSku}</span>
            </h4>
            <button onClick={() => setLogSku(null)} className="text-gray-400 hover:text-gray-600">×</button>
          </div>
          <div className="flex items-center justify-between mb-2 rounded-lg bg-gray-50 px-3 py-2">
            <p className="text-xs text-gray-500">
              {selectedLogVariant ? `${selectedLogVariant.variantName} · Stock: ${selectedLogVariant.stock}` : ''}
            </p>
            <button onClick={() => refetchLogs()} disabled={logsFetching}
              className="text-xs font-medium text-blue-600 hover:text-blue-700 disabled:opacity-50">
              {logsFetching ? '...' : 'Refresh'}
            </button>
          </div>
          {logsLoading ? (
            <div className="space-y-2"><Skeleton className="h-4 w-full" /><Skeleton className="h-4 w-5/6" /></div>
          ) : logsError ? (
            <div className="text-center py-4 text-gray-400 text-sm">
              <p>Tính năng nhật ký tồn kho đang được phát triển.</p>
            </div>
          ) : logs.length === 0 ? (
            <p className="text-sm text-gray-400">Chưa có lịch sử điều chỉnh.</p>
          ) : (
            <div className="max-h-48 overflow-y-auto space-y-2">
              {logs.map((log: InventoryLogEntry) => (
                <div key={log.logId} className="flex items-start gap-2 text-sm py-1.5 border-b border-gray-50 last:border-0">
                  <span className={`shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                    log.delta >= 0 ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                  }`}>
                    {log.delta >= 0 ? '+' : ''}{log.delta}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-gray-700 text-xs truncate">{log.reason || '—'}</p>
                    <p className="text-gray-400 text-xs">
                      {log.stockBefore} → {log.stockAfter} · {log.changedBy}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
