import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi, type Order, type OrderItem } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';
import { refundApi, type FullRefundCreatedResponse, type RefundPresignedUrlResponse } from '@shared/api/refund.api';
import { type ApiResponse } from '@shared/types/api';
import { Skeleton, Spinner } from '@shared/components/ui';
import { notify } from '@shared/lib/toast';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_STYLE: Record<string, { label: string; bg: string; color: string }> = {
  PENDING:            { label: 'Chờ thanh toán',  bg: 'bg-yellow-100', color: 'text-yellow-700' },
  PAID:               { label: 'Đã thanh toán',    bg: 'bg-blue-100',   color: 'text-blue-700' },
  SHIPPING:           { label: 'Đang giao',         bg: 'bg-purple-100', color: 'text-purple-700' },
  DELIVERED:          { label: 'Đã nhận hàng',     bg: 'bg-green-100',  color: 'text-green-700' },
  CANCELLED:          { label: 'Đã huỷ',            bg: 'bg-red-100',    color: 'text-red-700' },
  RETURNED:           { label: 'Hoàn hàng',         bg: 'bg-orange-100', color: 'text-orange-700' },
  PARTIALLY_REFUNDED: { label: 'Hoàn một phần',    bg: 'bg-indigo-100', color: 'text-indigo-700' },
  REFUNDED:           { label: 'Đã hoàn tiền',     bg: 'bg-gray-100',   color: 'text-gray-600' },
};

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function canCancel(status: string) {
  return status === 'PENDING';
}

function canConfirmReceived(status: string) {
  return status === 'SHIPPING';
}

function canRequestFullRefund(order: Order) {
  return order.status === 'PAID';
}

function canRequestPartialRefund(order: Order) {
  return ['PAID', 'SHIPPING', 'DELIVERED', 'PARTIALLY_REFUNDED'].includes(order.status);
}

type EvidenceImage = {
  previewUrl: string;
  evidenceUrl: string;
  name: string;
};

function normalizeMinioUrl(url: string) {
  return url
    .replace('//minio:9000', '//localhost:9000')
    .replace('//fs-minio:9000', '//localhost:9000');
}

function firstString(...values: Array<string | undefined>) {
  return values.find(value => typeof value === 'string' && value.trim().length > 0);
}

function getPresignedUploadUrl(presigned: RefundPresignedUrlResponse) {
  return firstString(
    presigned.url,
    presigned.presignedUrl,
    presigned.presigned_url,
    presigned.uploadUrl,
    presigned.upload_url,
  );
}

function getEvidenceObjectUrl(presigned: RefundPresignedUrlResponse, uploadUrl: string) {
  return firstString(
    presigned.objectUrl,
    presigned.object_url,
    presigned.cdnUrl,
    presigned.cdn_url,
  ) ?? getPublicEvidenceUrl(uploadUrl);
}

function isApiRouteUrl(url: string) {
  try {
    const parsed = new URL(url, window.location.origin);
    return parsed.pathname.startsWith('/api/v1/orders/') || parsed.pathname.startsWith('/orders/');
  } catch {
    return false;
  }
}

function getPublicEvidenceUrl(uploadUrl: string) {
  return uploadUrl.split('?')[0];
}

async function uploadRefundEvidence(orderId: number, file: File): Promise<EvidenceImage> {
  const { data } = await refundApi.getRefundPresignedUrl(orderId, file.name, file.type || 'image/jpeg');
  const presigned = data.data;
  const rawUploadUrl = presigned ? getPresignedUploadUrl(presigned) : undefined;
  if (!rawUploadUrl) {
    throw new Error('Không thể tạo URL tải ảnh');
  }

  if (rawUploadUrl.startsWith('mock://')) {
    const previewUrl = URL.createObjectURL(file);
    return {
      previewUrl,
      evidenceUrl: previewUrl,
      name: file.name,
    };
  }

  const uploadUrl = normalizeMinioUrl(rawUploadUrl);
  if (isApiRouteUrl(uploadUrl)) {
    throw new Error('URL upload anh khong hop le');
  }

  const uploadRes = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || presigned.contentType || 'image/jpeg' },
    body: file,
  });

  if (!uploadRes.ok) {
    throw new Error('Tải ảnh bằng chứng thất bại');
  }

  return {
    previewUrl: URL.createObjectURL(file),
    evidenceUrl: normalizeMinioUrl(getEvidenceObjectUrl(presigned, rawUploadUrl)),
    name: file.name,
  };
}

function EvidenceUploader({
  orderId,
  images,
  onChange,
  disabled,
}: {
  orderId: number;
  images: EvidenceImage[];
  onChange: (images: EvidenceImage[]) => void;
  disabled?: boolean;
}) {
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  const handleFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    const selected = Array.from(files).filter(file => file.type.startsWith('image/'));
    if (selected.length === 0) {
      setUploadError('Vui lòng chọn file ảnh hợp lệ');
      return;
    }

    setIsUploading(true);
    setUploadError('');
    try {
      const uploaded = [];
      for (const file of selected) {
        uploaded.push(await uploadRefundEvidence(orderId, file));
      }
      onChange([...images, ...uploaded]);
    } catch (err: any) {
      setUploadError(err?.message || 'Tải ảnh bằng chứng thất bại');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="mb-4">
      <div className="flex items-center justify-between gap-3 mb-2">
        <label className="block text-sm font-medium text-gray-700">Ảnh bằng chứng</label>
        {isUploading && (
          <span className="inline-flex items-center gap-1.5 text-xs text-blue-600">
            <Spinner className="w-3 h-3" />
            Đang tải...
          </span>
        )}
      </div>
      <label className={`flex min-h-24 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed px-4 py-4 text-center transition-colors ${
        disabled || isUploading ? 'border-gray-200 bg-gray-50 text-gray-400 cursor-not-allowed' : 'border-blue-200 bg-blue-50/40 text-blue-700 hover:bg-blue-50'
      }`}>
        <input
          type="file"
          accept="image/*"
          multiple
          disabled={disabled || isUploading}
          onChange={event => {
            void handleFiles(event.target.files);
            event.target.value = '';
          }}
          className="hidden"
        />
        <span className="text-sm font-semibold">Chọn ảnh bằng chứng</span>
        <span className="mt-1 text-xs text-gray-500">Có thể chọn nhiều ảnh, định dạng JPG/PNG/WebP.</span>
      </label>
      {uploadError && (
        <p className="mt-2 text-xs text-red-600">{uploadError}</p>
      )}
      {images.length > 0 && (
        <div className="mt-3 grid grid-cols-4 gap-2">
          {images.map((image, index) => (
            <div key={`${image.evidenceUrl}-${index}`} className="relative overflow-hidden rounded-lg border border-gray-100 bg-gray-50">
              <img src={image.previewUrl} alt={image.name} className="h-20 w-full object-cover" />
              <button
                type="button"
                onClick={() => onChange(images.filter((_, i) => i !== index))}
                disabled={disabled || isUploading}
                className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-xs text-white hover:bg-black/75 disabled:opacity-50"
                aria-label="Xóa ảnh bằng chứng"
              >
                x
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Cancel Modal ─────────────────────────────────────────────────────────────
function CancelModal({ order, queryClient, parentOrderId, onClose, onSuccess }: { order: Order; queryClient: ReturnType<typeof useQueryClient>; parentOrderId: number; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => orderApi.cancelOrder(order.orderId, { reason, note }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['parent-order', parentOrderId] });
      onSuccess();
      onClose();
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Hủy đơn thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Hủy đơn hàng</h3>
        <p className="text-sm text-gray-500 mb-4">
          Bạn đang hủy đơn <strong>{order.orderCode}</strong>. Hành động này không thể hoàn tác.
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hủy *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Thay đổi ý định">Thay đổi ý định</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Giá sản phẩm rẻ hơn chỗ khác">Giá sản phẩm rẻ hơn chỗ khác</option>
            <option value="Thời gian giao hàng quá lâu">Thời gian giao hàng quá lâu</option>
            <option value="Đặt nhầm sản phẩm">Đặt nhầm sản phẩm</option>
            <option value="Khác">Khác</option>
          </select>
        </div>
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="Bổ sung thông tin..."
            rows={3}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang hủy...' : 'Xác nhận hủy'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Partial Refund Modal ──────────────────────────────────────────────────────
function PartialRefundModal({ order, onClose, onSuccess }: { order: Order; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [selectedItems, setSelectedItems] = useState<Map<number, { qty: number; itemReason: string }>>(new Map());
  const [evidenceImages, setEvidenceImages] = useState<EvidenceImage[]>([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const mut = useMutation({
    mutationFn: () => {
      const items = Array.from(selectedItems.entries()).map(([itemId, v]) => ({
        orderItemId: itemId,
        quantity: v.qty,
        itemReason: v.itemReason,
      }));
      return refundApi.requestPartialRefund(order.orderId, {
        reason,
        items,
        evidenceImages: evidenceImages.map(image => image.evidenceUrl),
      });
    },
    onSuccess: () => {
      setSuccess(true);
      setTimeout(() => { onSuccess(); onClose(); }, 1500);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Yêu cầu hoàn tiền thất bại');
    },
  });

  const toggleItem = (item: OrderItem) => {
    const next = new Map(selectedItems);
    if (next.has(item.orderItemId)) {
      next.delete(item.orderItemId);
    } else {
      next.set(item.orderItemId, {
        qty: item.quantity - item.refundedQuantity,
        itemReason: '',
      });
    }
    setSelectedItems(next);
  };

  const updateQty = (itemId: number, qty: number) => {
    const next = new Map(selectedItems);
    const existing = next.get(itemId);
    if (existing) next.set(itemId, { ...existing, qty: Math.max(1, qty) });
    setSelectedItems(next);
  };

  const refundTotal = () => {
    if (!order.items) return 0;
    return Array.from(selectedItems.entries()).reduce((sum, [itemId, v]) => {
      const item = order.items?.find(i => i.orderItemId === itemId);
      return sum + (item ? item.priceSnapshot * v.qty : 0);
    }, 0);
  };

  const remaining = (item: OrderItem) => item.quantity - item.refundedQuantity;

  if (success) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Yêu cầu hoàn tiền đã gửi!</h3>
          <p className="text-sm text-gray-500">Admin sẽ xem xét và xử lý trong 1-3 ngày làm việc.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-lg w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Yêu cầu hoàn tiền một phần</h3>
        <p className="text-sm text-gray-500 mb-4">
          Hoàn tiền cho đơn <strong>{order.orderCode}</strong> ({order.sellerName})
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}

        {/* Item selection */}
        <div className="space-y-3 mb-4 max-h-64 overflow-y-auto">
          {order.items?.map(item => {
            const avail = remaining(item);
            const sel = selectedItems.get(item.orderItemId);
            if (avail <= 0) return null;
            return (
              <div key={item.orderItemId} className={`border rounded-xl p-3 cursor-pointer transition-all ${sel ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'}`}
                onClick={() => toggleItem(item)}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                    <p className="text-xs text-gray-500">{item.variantName} · Còn hoàn: {avail}/{item.quantity}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-bold text-gray-900">{fmt(item.priceSnapshot * avail)}</p>
                    <p className="text-xs text-gray-400">{fmt(item.priceSnapshot)}/sp</p>
                  </div>
                </div>
                {sel && avail > 1 && (
                  <div className="mt-2 flex items-center gap-2" onClick={e => e.stopPropagation()}>
                    <span className="text-xs text-gray-500">SL:</span>
                    <button onClick={() => updateQty(item.orderItemId, sel.qty - 1)} className="w-6 h-6 rounded border text-xs font-bold hover:bg-gray-100">−</button>
                    <span className="text-xs font-medium w-4 text-center">{sel.qty}</span>
                    <button onClick={() => updateQty(item.orderItemId, sel.qty + 1)} disabled={sel.qty >= avail} className="w-6 h-6 rounded border text-xs font-bold hover:bg-gray-100 disabled:opacity-30">+</button>
                  </div>
                )}
                {sel && (
                  <div className="mt-2" onClick={e => e.stopPropagation()}>
                    <input
                      placeholder="Lý do cho sản phẩm này..."
                      value={sel.itemReason}
                      onChange={e => {
                        const next = new Map(selectedItems);
                        next.set(item.orderItemId, { ...next.get(item.orderItemId)!, itemReason: e.target.value });
                        setSelectedItems(next);
                      }}
                      className="w-full px-2 py-1 border rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Reason */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hoàn tiền *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Sản phẩm lỗi">Sản phẩm lỗi</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Giao thiếu sản phẩm">Giao thiếu sản phẩm</option>
            <option value="Sản phẩm hư hỏng">Sản phẩm hư hỏng trong vận chuyển</option>
            <option value="Đặt nhầm sản phẩm">Đặt nhầm sản phẩm</option>
            <option value="Khác">Khác</option>
          </select>
        </div>

        <EvidenceUploader
          orderId={order.orderId}
          images={evidenceImages}
          onChange={setEvidenceImages}
          disabled={mut.isPending}
        />

        {/* Summary */}
        {selectedItems.size > 0 && (
          <div className="bg-gray-50 rounded-xl p-3 mb-4">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Tổng hoàn:</span>
              <span className="font-bold text-red-600">{fmt(refundTotal())}</span>
            </div>
          </div>
        )}

        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={selectedItems.size === 0 || !reason || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Full Refund Modal ─────────────────────────────────────────────────────────
function FullRefundModal({ parentOrderId, evidenceOrderId, onClose, onSuccess }: { parentOrderId: number; evidenceOrderId?: number; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [evidenceImages, setEvidenceImages] = useState<EvidenceImage[]>([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [result, setResult] = useState<any>(null);

  const mut = useMutation({
    mutationFn: () =>
      refundApi.requestFullRefund(parentOrderId, {
        reason,
        evidenceImages: evidenceImages.map(image => image.evidenceUrl),
      }) as Promise<{ data: ApiResponse<FullRefundCreatedResponse> }>,
    onSuccess: (res) => {
      setResult(res.data.data);
      setSuccess(true);
      setTimeout(() => { onSuccess(); onClose(); }, 3000);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Yêu cầu hoàn tiền thất bại');
    },
  });

  if (success && result) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Yêu cầu hoàn tiền đã gửi!</h3>
          <p className="text-sm text-gray-500 mb-2">
            Tổng cộng: <strong className="text-red-600">{fmt(result.totalAmount)}</strong>
          </p>
          <p className="text-xs text-gray-400">
            {result.refunds.length} yêu cầu hoàn tiền cho {result.refunds.length} người bán
          </p>
          <p className="text-xs text-gray-400 mt-1">Ước tính hoàn: {result.estimatedDays} ngày</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Yêu cầu hoàn tiền toàn bộ</h3>
        <p className="text-sm text-gray-500 mb-4">
          Bạn sẽ nhận lại toàn bộ số tiền đã thanh toán cho tất cả đơn hàng trong đơn cha này.
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hoàn tiền *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Thay đổi ý định">Thay đổi ý định</option>
            <option value="Đơn hàng không được xử lý kịp thời">Đơn hàng không được xử lý kịp thời</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Khác">Khác</option>
          </select>
        </div>
        {evidenceOrderId ? (
          <EvidenceUploader
            orderId={evidenceOrderId}
            images={evidenceImages}
            onChange={setEvidenceImages}
            disabled={mut.isPending}
          />
        ) : (
          <p className="mb-4 rounded-xl bg-yellow-50 p-3 text-sm text-yellow-700">
            Không thể tải ảnh bằng chứng vì không tìm thấy đơn con.
          </p>
        )}
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Confirm Received Modal ───────────────────────────────────────────────────
function ConfirmReceivedModal({ order, onClose, onSuccess }: { order: Order; onClose: () => void; onSuccess: () => void }) {
  const mut = useMutation({
    mutationFn: () => orderApi.confirmReceived(order.orderId),
    onSuccess: () => { notify.success('Đã xác nhận nhận hàng. Cảm ơn bạn!'); onSuccess(); onClose(); },
    onError: (err: any) => {
      notify.error(err?.response?.data?.message || 'Không thể xác nhận đã nhận hàng. Vui lòng thử lại.');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
        <div className="text-5xl mb-4">📦</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">Xác nhận đã nhận hàng?</h3>
        <p className="text-sm text-gray-500 mb-6">
          Xác nhận bạn đã nhận được đơn hàng <strong>{order.orderCode}</strong> từ {order.sellerName}.
          <br />Bạn sẽ nhận được điểm thưởng từ đơn hàng này.
        </p>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Chưa</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Đã nhận hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── SubOrder Timeline Component ─────────────────────────────────────────────
function SubOrderTimeline({ subOrder, paymentPaidAt }: { subOrder: Order; paymentPaidAt?: string }) {
  const status = subOrder.status;

  if (status === 'CANCELLED') {
    return (
      <div className="px-5 py-6 bg-gray-50/30 border-b border-gray-100">
        <div className="flex items-center gap-4">
          <div className="flex items-center justify-center w-10 h-10 rounded-full bg-red-100 text-red-600 shrink-0">
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <div>
            <h4 className="text-sm font-bold text-red-700">Đơn hàng đã bị hủy</h4>
            <p className="text-xs text-gray-500 mt-0.5">
              Hủy bởi: {subOrder.cancelledBy || 'Hệ thống'} · Lý do: {subOrder.cancelReason || 'Không có lý do'}
            </p>
            <p className="text-xs text-gray-400 mt-1">Vào lúc {formatDate(subOrder.updatedAt)}</p>
          </div>
        </div>
      </div>
    );
  }

  const steps = [
    {
      key: 'PENDING',
      label: 'Chờ thanh toán',
      active: true,
      time: subOrder.createdAt,
    },
    {
      key: 'PAID',
      label: 'Đã thanh toán',
      active: ['PAID', 'SHIPPING', 'DELIVERED', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(status),
      time: paymentPaidAt || (['PAID', 'SHIPPING', 'DELIVERED'].includes(status) ? subOrder.createdAt : undefined),
    },
    {
      key: 'SHIPPING',
      label: 'Đang giao hàng',
      active: ['SHIPPING', 'DELIVERED'].includes(status),
      time: status === 'SHIPPING' ? subOrder.updatedAt : (status === 'DELIVERED' ? subOrder.createdAt : undefined),
      subtext: subOrder.trackingNumber ? `${subOrder.carrier || 'Đơn vị vận chuyển'}: ${subOrder.trackingNumber}` : undefined,
    },
    {
      key: 'DELIVERED',
      label: 'Đã nhận hàng',
      active: status === 'DELIVERED',
      time: status === 'DELIVERED' ? subOrder.updatedAt : undefined,
    },
  ];

  return (
    <div className="px-5 py-6 bg-gray-50/30 border-b border-gray-100">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-6 sm:gap-2">
        {steps.map((step, index) => {
          const isCompleted = step.active;
          const isCurrent = (index === 0 && status === 'PENDING') ||
            (index === 1 && status === 'PAID') ||
            (index === 2 && status === 'SHIPPING') ||
            (index === 3 && status === 'DELIVERED');

          return (
            <div key={step.key} className="flex-1 flex sm:flex-col items-center gap-3 sm:gap-2 w-full relative">
              {index < steps.length - 1 && (
                <div className="hidden sm:block absolute left-[50%] right-[-50%] top-4 h-0.5 bg-gray-200 z-0">
                  <div
                    className={`h-full bg-blue-600 transition-all duration-300 ${
                      steps[index + 1].active ? 'w-full' : 'w-0'
                    }`}
                  />
                </div>
              )}

              <div
                className={`relative z-10 flex items-center justify-center w-8 h-8 rounded-full border-2 transition-all ${
                  isCompleted
                    ? 'bg-blue-600 border-blue-600 text-white'
                    : isCurrent
                    ? 'bg-white border-blue-600 text-blue-600 ring-4 ring-blue-50'
                    : 'bg-white border-gray-200 text-gray-400'
                }`}
              >
                {isCompleted ? (
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                ) : (
                  <span className="text-xs font-semibold">{index + 1}</span>
                )}
              </div>

              <div className="flex-1 sm:text-center">
                <p
                  className={`text-sm font-semibold ${
                    isCurrent ? 'text-blue-600' : isCompleted ? 'text-gray-900' : 'text-gray-400'
                  }`}
                >
                  {step.label}
                </p>
                {step.time && (
                  <p className="text-[10px] text-gray-400 mt-0.5">{formatDate(step.time)}</p>
                )}
                {step.subtext && (
                  <p className="text-[10px] text-blue-600 font-medium mt-0.5">{step.subtext}</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Order Detail Page ────────────────────────────────────────────────────────
export default function OrderDetailPage() {
  const { parentOrderId } = useParams<{ parentOrderId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const id = Number(parentOrderId);

  const { data: orderData, isLoading, error } = useQuery({
    queryKey: ['parent-order', id],
    queryFn: () => orderApi.getParentOrder(id).then(r => r.data.data),
    enabled: !isNaN(id),
    retry: 1,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data) return 5000;
      // Poll while any sub-order is PENDING (payment may still be processing)
      const hasPending = data.orders?.some(o => o.status === 'PENDING') || data.status === 'PENDING';
      return hasPending ? 5000 : false;
    },
  });

  const { data: paymentData, dataUpdatedAt: paymentDataUpdatedAt } = useQuery({
    queryKey: ['payment', id],
    queryFn: () => paymentApi.getPayment(id).then(r => r.data.data),
    enabled: !isNaN(id),
    retry: 1,
    refetchInterval: (query) => {
      const payment = query.state.data;
      if (!payment) return 3000;
      // Poll while payment is PENDING (waiting for Stripe webhook)
      if (payment.status === 'PENDING') return 3000;
      return false;
    },
  });

  const [showCancel, setShowCancel] = useState<Order | null>(null);
  const [showPartialRefund, setShowPartialRefund] = useState<Order | null>(null);
  const [showFullRefund, setShowFullRefund] = useState(false);
  const [showConfirm, setShowConfirm] = useState<Order | null>(null);

  if (isNaN(id)) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500">ID đơn hàng không hợp lệ.</p>
        <Link to="/orders" className="text-blue-600 hover:underline mt-2 inline-block">← Quay lại</Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-8 space-y-6">
        <div className="bg-white rounded-2xl border border-gray-100 p-6">
          <div className="flex items-center justify-between gap-4 mb-4">
            <Skeleton className="h-6 w-44" />
            <Skeleton className="h-7 w-24 rounded-full" />
          </div>
          <Skeleton className="h-4 w-2/3" />
        </div>
        <div className="bg-white rounded-2xl border border-gray-100 p-6">
          <Skeleton className="h-5 w-36 mb-4" />
          <div className="space-y-3">
            <Skeleton className="h-16 w-full rounded-xl" />
            <Skeleton className="h-16 w-full rounded-xl" />
          </div>
        </div>
      </div>
    );
  }

  if (error || !orderData) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy đơn hàng.</p>
        <Link to="/orders" className="text-blue-600 hover:underline">← Quay lại danh sách</Link>
      </div>
    );
  }

  const parent = orderData;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => navigate('/orders')} className="text-gray-400 hover:text-gray-600">
          ←
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500">{parent.orderCode}</p>
        </div>
      </div>

      {/* Payment info */}
      {paymentData ? (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
            💳 Thông tin thanh toán
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500 text-xs">Tổng tiền</p>
              <p className="font-bold text-gray-900">{fmt(paymentData.amount)}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Phương thức</p>
              <p className="font-medium text-gray-700">{paymentData.method}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Trạng thái</p>
              <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                paymentData.status === 'SUCCESS' ? 'bg-green-100 text-green-700' :
                paymentData.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                'bg-red-100 text-red-700'
              }`}>
                {paymentData.status === 'SUCCESS' ? 'Thành công' :
                 paymentData.status === 'PENDING' ? 'Đang chờ' : 'Thất bại'}
              </span>
            </div>
            {paymentData.paidAt && (
              <div>
                <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                <p className="font-medium text-gray-700">{formatDate(paymentData.paidAt)}</p>
              </div>
            )}
          </div>
        </div>
      ) : null}

      {/* Shipping address */}
      {parent.shippingAddress && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-2">📍 Địa chỉ giao hàng</h2>
          <p className="text-sm text-gray-700">{parent.shippingAddress.fullAddress}</p>
        </div>
      )}

      {/* Sub-orders */}
      <div className="space-y-4 mb-6">
        {parent.orders.map(subOrder => {
          const st = STATUS_STYLE[subOrder.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700', label: subOrder.status };
          return (
            <div key={subOrder.orderId} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              {/* Sub-order header */}
              <div className="px-5 py-4 border-b border-gray-50 flex items-start justify-between gap-3 flex-wrap">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-bold text-gray-900">{subOrder.orderCode}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${st.bg} ${st.color}`}>{st.label}</span>
                  </div>
                  <p className="text-sm text-gray-500 mt-0.5">{subOrder.sellerName}</p>
                  {subOrder.trackingNumber && (
                    <p className="text-xs text-gray-400 mt-0.5">
                      Mã vận đơn: {subOrder.trackingNumber}
                      {subOrder.carrier ? ` (${subOrder.carrier})` : ''}
                    </p>
                  )}
                  {subOrder.cancelledBy && (
                    <p className="text-xs text-red-500 mt-0.5">
                      Đã hủy bởi {subOrder.cancelledBy}: {subOrder.cancelReason}
                    </p>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <p className="font-bold text-gray-900">{fmt(subOrder.finalAmt)}</p>
                  <p className="text-xs text-gray-400">{formatDate(subOrder.createdAt)}</p>
                </div>
              </div>

              {/* Status Timeline */}
              <SubOrderTimeline subOrder={subOrder} paymentPaidAt={paymentData?.paidAt} />

              {/* Order items */}
              <div className="px-5 py-4">
                {subOrder.items?.map(item => (
                  <div key={item.orderItemId} className="flex items-center gap-3 mb-3 last:mb-0">
                    <div className="w-14 h-14 rounded-lg bg-gray-100 flex items-center justify-center text-2xl shrink-0">
                      {item.imageSnapshot ? (
                        <img src={item.imageSnapshot} alt="" className="w-full h-full object-cover rounded-lg" />
                      ) : '📦'}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                      <p className="text-xs text-gray-500">{item.variantName}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-gray-400">x{item.quantity}</span>
                        {item.refundedQuantity > 0 && (
                          <span className="text-xs text-blue-600">Đã hoàn: {item.refundedQuantity}</span>
                        )}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-semibold text-gray-900">{fmt(item.priceSnapshot * item.quantity)}</p>
                      <p className="text-xs text-gray-400">{fmt(item.priceSnapshot)}/sp</p>
                    </div>
                  </div>
                ))}
              </div>

              {/* Action buttons */}
              <div className="px-5 py-4 border-t border-gray-50 flex flex-wrap gap-2">
                {canCancel(subOrder.status) && (
                  <button
                    onClick={() => setShowCancel(subOrder)}
                    className="px-4 py-2 text-sm font-medium border border-red-200 text-red-600 rounded-xl hover:bg-red-50 transition-colors"
                  >
                    Hủy đơn
                  </button>
                )}
                {canConfirmReceived(subOrder.status) && (
                  <button
                    onClick={() => setShowConfirm(subOrder)}
                    className="px-4 py-2 text-sm font-medium bg-green-600 text-white rounded-xl hover:bg-green-700 transition-colors"
                  >
                    Xác nhận đã nhận
                  </button>
                )}
                {canRequestPartialRefund(subOrder) && (
                  <button
                    onClick={() => setShowPartialRefund(subOrder)}
                    className="px-4 py-2 text-sm font-medium border border-blue-200 text-blue-600 rounded-xl hover:bg-blue-50 transition-colors"
                  >
                    Hoàn tiền một phần
                  </button>
                )}
                {canRequestFullRefund(subOrder) && (
                  <button
                    onClick={() => setShowFullRefund(true)}
                    className="px-4 py-2 text-sm font-medium border border-blue-200 text-blue-600 rounded-xl hover:bg-blue-50 transition-colors"
                  >
                    Hoàn tiền toàn bộ
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Price summary */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền hàng</span>
            <span>{fmt(parent.totalAmt)}</span>
          </div>
          {orderData.orders[0]?.items && (
            <div className="flex justify-between text-gray-600">
              <span>Phí vận chuyển</span>
              <span className="text-green-600">Miễn phí</span>
            </div>
          )}
          <div className="h-px bg-gray-100" />
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmt(parent.finalAmt)}</span>
          </div>
        </div>
      </div>

      {/* Back */}
      <div className="text-center">
        <Link to="/orders" className="text-blue-600 hover:underline text-sm">
          ← Quay lại danh sách đơn hàng
        </Link>
      </div>

      {/* Modals */}
      {showCancel && (
        <CancelModal
          order={showCancel}
          queryClient={queryClient}
          parentOrderId={id}
          onClose={() => setShowCancel(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showPartialRefund && (
        <PartialRefundModal
          order={showPartialRefund}
          onClose={() => setShowPartialRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showFullRefund && (
        <FullRefundModal
          parentOrderId={id}
          evidenceOrderId={parent.orders[0]?.orderId}
          onClose={() => setShowFullRefund(false)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showConfirm && (
        <ConfirmReceivedModal
          order={showConfirm}
          onClose={() => setShowConfirm(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
    </div>
  );
}
