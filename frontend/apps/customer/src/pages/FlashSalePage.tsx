import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { flashSaleApi, type FlashSaleSession } from '@shared/api/flashSale.api';
import { productApi } from '@shared/api/product.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function Countdown({ targetTime }: { targetTime: Date }) {
  const [seconds, setSeconds] = useState(() => Math.max(0, Math.floor((targetTime.getTime() - Date.now()) / 1000)));

  useEffect(() => {
    const tick = () => setSeconds(s => Math.max(0, s - 1));
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const h = String(Math.floor(seconds / 3600)).padStart(2, '0');
  const m = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0');
  const s = String(seconds % 60).padStart(2, '0');

  return (
    <div className="flex items-center gap-1">
      <span className="bg-white text-gray-900 font-bold text-xl w-10 h-10 flex items-center justify-center rounded-lg shadow-sm tabular-nums">
        {h}
      </span>
      <span className="text-white font-bold text-lg">:</span>
      <span className="bg-white text-gray-900 font-bold text-xl w-10 h-10 flex items-center justify-center rounded-lg shadow-sm tabular-nums">
        {m}
      </span>
      <span className="text-white font-bold text-lg">:</span>
      <span className="bg-white text-gray-900 font-bold text-xl w-10 h-10 flex items-center justify-center rounded-lg shadow-sm tabular-nums">
        {s}
      </span>
    </div>
  );
}

function FlashItemCard({
  item,
  sessionActive,
  onBuy,
  isBuying,
}: {
  item: {
    skuCode: string;
    productName: string;
    imageUrl?: string;
    flashPrice: number;
    originalPrice: number;
    flashStock: number;
    soldQty: number;
    status: string;
    id?: number;
    sessionId?: number;
  };
  sessionActive: boolean;
  onBuy: (item: {
    skuCode: string;
    id?: number;
  }) => void;
  isBuying: boolean;
}) {
  const sold = item.soldQty ?? 0;
  const total = item.flashStock ?? 0;
  const remaining = total - sold;
  const pct = total > 0 ? Math.round((sold / total) * 100) : 0;
  const disc = item.originalPrice > 0
    ? Math.round((1 - item.flashPrice / item.originalPrice) * 100)
    : 0;
  const soldOut = remaining <= 0 || item.status === 'SOLD_OUT';

  return (
    <div className={`bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group flex flex-col ${soldOut ? 'opacity-75' : ''}`}>
      <div className="relative bg-gradient-to-br from-orange-50 to-red-50 aspect-square flex items-center justify-center overflow-hidden">
        {item.imageUrl ? (
          <img src={item.imageUrl} alt={item.productName} className="w-full h-full object-cover" />
        ) : (
          <span className="text-5xl">🔥</span>
        )}
        <span className="absolute top-2 left-2 bg-red-500 text-white font-black text-sm px-2 py-1 rounded-lg">
          -{disc}%
        </span>
        {soldOut && (
          <div className="absolute inset-0 bg-white/70 flex items-center justify-center">
            <span className="bg-gray-900 text-white text-sm font-bold px-4 py-2 rounded-lg">HẾT HÀNG</span>
          </div>
        )}
      </div>
      <div className="p-3 flex flex-col flex-1">
        <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-red-600 transition-colors">
          {item.productName}
        </h3>
        <div className="flex items-baseline gap-1.5 mb-2">
          <span className="text-base font-bold text-red-600">{fmt(item.flashPrice)}</span>
          {disc > 0 && (
            <span className="text-xs text-gray-400 line-through">{fmt(item.originalPrice)}</span>
          )}
        </div>
        {/* Progress bar */}
        <div className="mb-3">
          <div className="flex justify-between text-xs text-gray-500 mb-1">
            <span>Đã bán {pct}%</span>
            <span>Còn {remaining}</span>
          </div>
          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full transition-all ${pct > 80 ? 'bg-red-500' : 'bg-orange-400'}`}
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>
        <button
          onClick={() => !soldOut && !isBuying && sessionActive && onBuy({ skuCode: item.skuCode, id: item.id })}
          disabled={soldOut || isBuying || !sessionActive}
          className={`w-full py-2 text-xs font-semibold rounded-xl transition-all flex items-center justify-center gap-1 mt-auto ${
            soldOut
              ? 'bg-gray-200 text-gray-500 cursor-not-allowed'
              : !sessionActive
                ? 'bg-gray-200 text-gray-400 cursor-not-allowed'
                : 'bg-gradient-to-r from-red-500 to-orange-500 hover:from-red-600 hover:to-orange-600 text-white'
          }`}
        >
          {isBuying ? (
            <>
              <svg className="animate-spin h-3 w-3" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Đang thêm...
            </>
          ) : soldOut ? (
            'Hết hàng'
          ) : !sessionActive ? (
            'Chưa bắt đầu'
          ) : (
            <>
              <span>⚡</span> Mua ngay
            </>
          )}
        </button>
      </div>
    </div>
  );
}

export default function FlashSalePage() {
  const navigate = useNavigate();
  const { addToCart } = useCartStore();
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [buyingSku, setBuyingSku] = useState<string | null>(null);

  const { data: sessionsData, isLoading: sessionsLoading } = useQuery({
    queryKey: ['flash-sale-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data),
    staleTime: 1000 * 60,
  });

  const sessions: FlashSaleSession[] = sessionsData?.content ?? [];

  // Determine which session is active; respect the selected tab when present.
  const defaultSession = sessions.find(s => s.status === 'ACTIVE') ?? sessions.find(s => s.status === 'UPCOMING') ?? sessions[0];
  const activeSession = sessions.find(s => s.id === activeSessionId) ?? defaultSession;

  const { data: sessionDetail, isLoading: detailLoading } = useQuery({
    queryKey: ['flash-sale-session', activeSession?.id],
    queryFn: () => flashSaleApi.getSession(activeSession!.id).then(r => r.data.data),
    enabled: !!activeSession?.id,
    staleTime: 1000 * 30,
  });

  const handleBuyNow = useCallback(async (item: { skuCode: string; id?: number }) => {
    setBuyingSku(item.skuCode);
    setSuccessMsg(null);
    try {
      await addToCart(item.skuCode, 1);
      setSuccessMsg('Đã thêm vào giỏ hàng!');
      setTimeout(() => navigate('/cart'), 1000);
    } catch (err: any) {
      setSuccessMsg(err?.response?.data?.message || 'Không thể thêm vào giỏ hàng.');
      setTimeout(() => setSuccessMsg(null), 3000);
    } finally {
      setBuyingSku(null);
    }
  }, [addToCart, navigate]);

  const targetTime = activeSession?.endTime
    ? new Date(activeSession.endTime)
    : new Date(Date.now() + 2 * 3600 * 1000);
  const isActive = activeSession?.status === 'ACTIVE';
  const visibleItems = (sessionDetail?.items ?? []).filter(item =>
    ['APPROVED', 'ACTIVE', 'SOLD_OUT'].includes(item.status),
  );

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Success notification */}
      {successMsg && (
        <div className="fixed top-20 right-4 bg-green-500 text-white px-6 py-3 rounded-xl shadow-lg z-50">
          ✓ {successMsg}
        </div>
      )}

      {/* Hero */}
      <div className="bg-gradient-to-r from-red-500 via-orange-500 to-yellow-400 text-white py-10 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <div className="flex items-center justify-center gap-2 mb-2">
            <span className="text-3xl">⚡</span>
            <h1 className="text-4xl font-black tracking-tight">FLASH SALE</h1>
            <span className="text-3xl">⚡</span>
          </div>
          <p className="text-orange-100 mb-6 text-lg">Giảm giá cực sốc — chỉ trong hôm nay!</p>
          {activeSession?.status === 'ACTIVE' && activeSession.endTime && (
            <div className="flex flex-col items-center gap-2">
              <p className="text-sm text-orange-100 uppercase tracking-widest font-medium">Kết thúc sau</p>
              <Countdown targetTime={targetTime} />
            </div>
          )}
          {activeSession?.status === 'UPCOMING' && activeSession.startTime && (
            <div className="flex flex-col items-center gap-2">
              <p className="text-sm text-orange-100 uppercase tracking-widest font-medium">Bắt đầu sau</p>
              <Countdown targetTime={new Date(activeSession.startTime)} />
            </div>
          )}
        </div>
      </div>

      {/* Session tabs */}
      {sessions.length > 0 && (
        <div className="bg-white border-b border-gray-200 sticky top-16 z-30">
          <div className="max-w-7xl mx-auto px-4 flex gap-1 overflow-x-auto py-2 scrollbar-none">
            {sessions.map((s, i) => {
              const isActive = s.status === 'ACTIVE';
              const isUpcoming = s.status === 'UPCOMING';
              const startTime = s.startTime ? new Date(s.startTime).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '';
              return (
                <button
                  key={s.id}
                  onClick={() => setActiveSessionId(s.id)}
                  className={`shrink-0 flex flex-col items-center px-5 py-2 rounded-xl text-sm font-medium transition-all ${
                    (activeSessionId ?? activeSession?.id) === s.id
                      ? isActive
                        ? 'bg-red-500 text-white shadow-sm'
                        : 'bg-blue-500 text-white shadow-sm'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  <span>{startTime || `Phiên ${i + 1}`}</span>
                  {isActive && <span className="text-xs opacity-80">Đang diễn ra</span>}
                  {isUpcoming && <span className="text-xs opacity-80">Sắp diễn ra</span>}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Products */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {sessionsLoading || detailLoading ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="bg-white rounded-2xl border animate-pulse">
                <div className="aspect-square bg-gray-200" />
                <div className="p-3 space-y-2">
                  <div className="h-4 bg-gray-200 rounded w-3/4" />
                  <div className="h-6 bg-gray-200 rounded w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : visibleItems.length === 0 ? (
          <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
            <span className="text-5xl block mb-4">⚡</span>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">
              {activeSession?.status === 'ACTIVE' ? 'Chưa có sản phẩm flash sale nào' : 'Phiên flash sale chưa bắt đầu'}
            </h3>
            <p className="text-sm text-gray-500 max-w-sm mx-auto">
              {activeSession?.status === 'ACTIVE'
                ? 'Các sản phẩm sẽ được cập nhật sớm.'
                : ' Quay lại sau khi phiên bắt đầu.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {visibleItems.map((item) => (
              <FlashItemCard
                key={item.skuCode}
                item={item}
                sessionActive={isActive}
                onBuy={handleBuyNow}
                isBuying={buyingSku === item.skuCode}
              />
            ))}
          </div>
        )}

        {/* All sessions overview */}
        {sessions.length > 0 && (
          <div className="mt-12">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Tất cả các phiên</h2>
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['Tên phiên', 'Bắt đầu', 'Kết thúc', 'Trạng thái', 'Sản phẩm'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sessions.map(s => (
                    <tr key={s.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4 font-medium text-gray-900">{s.name}</td>
                      <td className="px-5 py-4 text-gray-500 whitespace-nowrap">
                        {s.startTime ? new Date(s.startTime).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'}
                      </td>
                      <td className="px-5 py-4 text-gray-500 whitespace-nowrap">
                        {s.endTime ? new Date(s.endTime).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'}
                      </td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                          s.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                          s.status === 'UPCOMING' ? 'bg-blue-100 text-blue-700' :
                          s.status === 'CANCELLED' ? 'bg-red-100 text-red-700' :
                          'bg-gray-100 text-gray-600'
                        }`}>
                          {s.status === 'ACTIVE' ? 'Đang diễn ra' :
                           s.status === 'UPCOMING' ? 'Sắp diễn ra' :
                           s.status === 'CANCELLED' ? 'Đã huỷ' : 'Đã kết thúc'}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-gray-500">{s.items?.length ?? 0} sản phẩm</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
