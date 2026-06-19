import type { InternalAxiosRequestConfig } from 'axios';
import { sleep } from './utils';
import { MOCK_ADDRESSES } from './data/addresses';
import { MOCK_CART } from './data/cart';
import { MOCK_PRODUCTS } from './data/products';
import { MOCK_ORDERS } from './data/orders';
import { MOCK_PARENT_ORDERS } from './data/parent-orders';
import { MOCK_PAYMENTS } from './data/payments';
import { MOCK_REFUNDS } from './data/refunds';
import { MOCK_SELLER_ORDERS } from './data/seller-orders';
import { MOCK_FLASH_SESSIONS, MOCK_FLASH_ITEMS } from './data/flash-sale';
import { MOCK_CATEGORIES } from './data/categories';
import { MOCK_NOTIFICATIONS } from './data/notifications';
import { MOCK_CHAT_SESSIONS, MOCK_CHAT_SUGGESTIONS, MOCK_CHAT_HISTORY } from './data/chat';
import { MOCK_WISHLIST } from './data/wishlist';
import { MOCK_BANNERS } from './data/banners';
import { checkoutOrderData } from './checkout';

type MockHandler = (config: InternalAxiosRequestConfig) => Promise<any>;

export const mockHandlers: MockHandler[] = [
  // ─── Wishlist ─────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/wishlist' && method === 'get') {
      await sleep(250 + Math.random() * 150);
      const content = MOCK_PRODUCTS.filter((p) => MOCK_WISHLIST.has(p.productId)).map((p) => ({
        productId: p.productId,
        productName: p.productName,
        thumbnailUrl: p.images?.[0] ?? null,
        minPrice: p.price,
        productStatus: 'ACTIVE',
        available: true,
        addedAt: new Date().toISOString(),
      }));
      return { success: true, data: { content, totalElements: content.length, totalPages: 1, last: true }, timestamp: Date.now() };
    }
    if (url === '/wishlist/items' && method === 'post') {
      await sleep(150 + Math.random() * 100);
      const body = JSON.parse(data || '{}');
      if (body.productId) MOCK_WISHLIST.add(String(body.productId));
      return { success: true, data: null, timestamp: Date.now() };
    }
    const wishMatch = url?.match(/^\/wishlist\/items\/([\w-]+)$/);
    if (wishMatch && method === 'delete') {
      await sleep(150 + Math.random() * 100);
      MOCK_WISHLIST.delete(wishMatch[1]);
      return { success: true, data: null, timestamp: Date.now() };
    }
    if (wishMatch && method === 'get') {
      await sleep(100 + Math.random() * 100);
      return { success: true, data: MOCK_WISHLIST.has(wishMatch[1]), timestamp: Date.now() };
    }
    return null;
  },

  // ─── Cart ─────────────────────────────────────────────────────────────────
  async ({ method, url }) => {
    if (url === '/cart' && method === 'get') {
      await sleep(300 + Math.random() * 200);
      return { success: true, data: MOCK_CART, timestamp: Date.now() };
    }
    if (url === '/cart' && method === 'delete') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    if (url?.startsWith('/cart/items') && method === 'post') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: { cartItemId: Date.now(), skuCode: 'MOCK-SKU', productName: 'Sản phẩm mới', variantName: 'Mặc định', unitPrice: 999_000, quantity: 1, stockAvailable: 50, isFlash: false }, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/\d+$/) && method === 'put') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: { cartItemId: parseInt(url!.split('/').pop()!), skuCode: 'MOCK-SKU', productName: 'Sản phẩm', variantName: 'Mặc định', unitPrice: 999_000, quantity: 1, stockAvailable: 50, isFlash: false }, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/\d+$/) && method === 'delete') {
      await sleep(200 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Orders ────────────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    if (url === '/orders/checkout' && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const body = JSON.parse(data || '{}');
      const poId = Date.now();
      const now = new Date().toISOString();
      const orderId = poId - 1;
      const orderData = {
        parentOrderId: poId,
        orderCode: `PO-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(poId).slice(-4)}`,
        orders: [
          {
            orderId: orderId,
            orderCode: `FS-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(orderId).slice(-4)}`,
            sellerId: 1,
            sellerName: 'Shop Sony',
            totalAmt: 6_490_000,
            finalAmt: 6_490_000,
            status: 'PENDING',
            itemCount: body.itemIds?.length || 1,
            createdAt: now,
          },
        ],
        totalAmount: 6_490_000,
        finalAmount: 6_490_000,
        itemsCount: body.itemIds?.length || 1,
        timeoutAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        createdAt: now,
      };
      checkoutOrderData[poId] = orderData;
      return { success: true, data: orderData, timestamp: Date.now() };
    }

    if (url === '/orders' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const page = params?.page ?? 0;
      const size = params?.size ?? 10;
      const filtered = status && status !== 'ALL'
        ? MOCK_ORDERS.filter(o => o.status === status)
        : MOCK_ORDERS;
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    const getOrderByIdMatch = url?.match(/^\/orders\/(\d+)$/);
    if (getOrderByIdMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const orderId = parseInt(getOrderByIdMatch[1]);
      const order = MOCK_ORDERS.find(o => o.orderId === orderId);
      if (!order) throw { response: { status: 404, data: { message: 'Order not found' } } };
      return { success: true, data: order, timestamp: Date.now() };
    }

    const getParentOrderMatch = url?.match(/^\/orders\/parent\/(\d+)$/);
    if (getParentOrderMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const parentId = parseInt(getParentOrderMatch[1]);
      // If there's a pending checkout, return it
      if (checkoutOrderData[parentId]) {
        const cd = checkoutOrderData[parentId];
        return {
          success: true,
          data: {
            parentOrderId: parentId,
            orderCode: cd.orderCode,
            status: 'PENDING',
            totalAmt: cd.totalAmount,
            finalAmt: cd.finalAmount,
            shippingAddress: MOCK_ADDRESSES[0],
            orders: cd.orders.map(o => ({
              ...o,
              sellerId: 1,
              sellerName: 'Shop Sony',
              buyerId: 1,
              buyerName: 'Nguyễn Văn A',
              shippingAddress: MOCK_ADDRESSES[0],
              createdAt: cd.createdAt,
              items: [{
                orderItemId: 1,
                skuCode: 'KEY-K2-WHITE',
                productName: 'Bàn phím cơ Keychron K2',
                variantName: 'Trắng / Hot-swap',
                priceSnapshot: 2_190_000,
                quantity: 1,
                refundedQuantity: 0,
              }],
            })),
            createdAt: cd.createdAt,
            updatedAt: cd.createdAt,
          },
          timestamp: Date.now(),
        };
      }
      const po = MOCK_PARENT_ORDERS.find(p => p.parentOrderId === parentId);
      if (!po) throw { response: { status: 404, data: { message: 'Parent order not found' } } };
      return { success: true, data: po, timestamp: Date.now() };
    }

    const cancelMatch = url?.match(/^\/orders\/(\d+)\/cancel$/);
    if (cancelMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(cancelMatch[1]);
      return {
        success: true,
        data: { orderId: orderId, orderCode: `FS-MOCK-${orderId}`, status: 'CANCELLED', cancelledBy: 'BUYER', cancelledAt: new Date().toISOString() },
        timestamp: Date.now(),
      };
    }

    const trackingMatch = url?.match(/^\/orders\/(\d+)\/tracking$/);
    if (trackingMatch && method === 'put') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(trackingMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          orderId: orderId,
          orderCode: `FS-MOCK-${orderId}`,
          status: 'SHIPPING',
          trackingNumber: body.trackingNumber,
          carrier: body.carrier || 'ViettelPost',
          shippingDeadline: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    const confirmMatch = url?.match(/^\/orders\/(\d+)\/confirm-received$/);
    if (confirmMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const orderId = parseInt(confirmMatch[1]);
      return { success: true, data: { orderId: orderId, status: 'DELIVERED' }, timestamp: Date.now() };
    }

    const rtsMatch = url?.match(/^\/orders\/(\d+)\/return-to-sender$/);
    if (rtsMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const orderId = parseInt(rtsMatch[1]);
      return {
        success: true,
        data: {
          orderId: orderId,
          orderCode: `FS-MOCK-${orderId}`,
          orderStatus: 'RETURNED',
          refundId: Date.now(),
          refundStatus: 'PENDING',
          refundAmount: 1_000_000,
        },
        timestamp: Date.now(),
      };
    }

    // ─── Seller Orders ────────────────────────────────────────────────────────
    if (url === '/sellers/me/orders' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      const filtered = status && status !== 'ALL'
        ? MOCK_SELLER_ORDERS.filter(o => o.status === status)
        : MOCK_SELLER_ORDERS;
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    // ─── Order Refunds (buyer) ────────────────────────────────────────────────
    const refundPresignedMatch = url?.match(/^\/orders\/(\d+)\/refunds\/presigned-url$/);
    if (refundPresignedMatch && method === 'get') {
      await sleep(200 + Math.random() * 100);
      const fileName = params?.file_name || `refund-evidence-${Date.now()}.jpg`;
      const contentType = params?.content_type || 'image/jpeg';
      return {
        success: true,
        data: {
          url: `mock://refund-evidence/${refundPresignedMatch[1]}/${Date.now()}-${encodeURIComponent(fileName)}`,
          fileName,
          contentType,
          expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    const orderRefundMatch = url?.match(/^\/orders\/(\d+)\/refunds$/);
    if (orderRefundMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const orderId = parseInt(orderRefundMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          refundId: Date.now(),
          orderId: orderId,
          groupRef: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(Date.now()).slice(-4)}`,
          type: 'PARTIAL',
          status: 'PENDING',
          amount: body.items?.reduce((s: number, it: any) => s + (it.quantity * 2_190_000), 0) || 299_000,
          reason: body.reason || 'Sản phẩm lỗi',
          evidenceImages: body.evidenceImages || [],
          initiatedBy: 'BUYER',
          items: body.items?.map((it: any) => ({
            orderItemId: it.orderItemId,
            quantity: it.quantity,
            refundAmount: it.quantity * 2_190_000,
            itemReason: it.itemReason,
            status: 'PENDING',
          })),
          createdAt: new Date().toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    if (orderRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const orderId = parseInt(orderRefundMatch[1]);
      const orderRefunds = MOCK_REFUNDS.filter(r => r.orderId === orderId);
      return { success: true, data: orderRefunds, timestamp: Date.now() };
    }

    const parentRefundMatch = url?.match(/^\/orders\/parent\/(\d+)\/refund$/);
    if (parentRefundMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const parentId = parseInt(parentRefundMatch[1]);
      return {
        success: true,
        data: {
          groupRef: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-${String(Date.now()).slice(-4)}`,
          type: 'FULL',
          totalAmount: 2_190_000,
          status: 'PENDING',
          refunds: [{ refundId: Date.now(), orderId: parentId * 10, sellerId: 1, amount: 2_190_000, itemCount: 1 }],
          estimatedDays: 3,
        },
        timestamp: Date.now(),
      };
    }

    if (parentRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      return {
        success: true,
        data: {
          groupRef: `GRP-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-0001`,
          type: 'FULL',
          overallStatus: 'SUCCESS',
          totalAmount: 2_190_000,
          refunds: [{ refundId: 1, orderId: 3, status: 'SUCCESS', refundRef: 're_mock_full_1' }],
        },
        timestamp: Date.now(),
      };
    }

    if (url === '/orders/refunds' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const page = params?.page ?? 0;
      const size = params?.size ?? 10;
      const start = page * size;
      const content = MOCK_REFUNDS.slice(start, start + size);
      return {
        success: true,
        data: { content, totalElements: MOCK_REFUNDS.length, totalPages: Math.ceil(MOCK_REFUNDS.length / size) },
        timestamp: Date.now(),
      };
    }

    return null;
  },

  // ─── Payments ──────────────────────────────────────────────────────────────
  async ({ method, url }) => {
    const parentOrderMatch = url?.match(/^\/payments\/parent-order\/(\d+)$/);
    if (parentOrderMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const parentId = parseInt(parentOrderMatch[1]);
      // Return pending checkout data if exists
      if (checkoutOrderData[parentId]) {
        return {
          success: true,
          data: {
            transactionId: parentId,
            parentOrderId: parentId,
            amount: checkoutOrderData[parentId].finalAmount,
            method: 'vnpay',
            status: 'PENDING',
            vnpayTxnRef: `VNP-TXN-MOCK-${parentId}`,
            applicationFee: Math.round(checkoutOrderData[parentId].finalAmount * 0.03),
            transRef: `TXN-MOCK-${parentId}`,
            paidAt: null,
            remainingSeconds: 542,
          },
          timestamp: Date.now(),
        };
      }
      const payment = MOCK_PAYMENTS.find(p => p.parentOrderId === parentId);
      if (!payment) throw { response: { status: 404, data: { message: 'Payment not found' } } };
      return { success: true, data: payment, timestamp: Date.now() };
    }

    const clientSecretMatch =
      url?.match(/^\/payments\/client-secret\/(\d+)$/) ??
      url?.match(/^\/payments\/parent-order\/(\d+)\/client-secret$/);
    if (clientSecretMatch && method === 'get') {
      await sleep(500 + Math.random() * 200);
      const parentId = parseInt(clientSecretMatch[1]);
      return {
        success: true,
        data: {
          parentOrderId: parentId,
          transactionId: parentId,
          clientSecret: `VNP-secret-${parentId}-${Date.now()}`,
          status: 'requires_payment_method',
        },
        timestamp: Date.now(),
      };
    }

    return null;
  },

  // ─── Addresses ──────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/users/me/addresses' && method === 'get') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: MOCK_ADDRESSES, timestamp: Date.now() };
    }
    if (url === '/users/me/addresses' && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: { addressId: Date.now(), ...body, isDefault: body.isDefault ?? false },
        timestamp: Date.now(),
      };
    }
    const addrUpdateMatch = url?.match(/^\/users\/me\/addresses\/(\d+)$/);
    if (addrUpdateMatch && method === 'put') {
      await sleep(300 + Math.random() * 100);
      const addressId = parseInt(addrUpdateMatch[1]);
      const body = JSON.parse(data || '{}');
      return { success: true, data: { ...MOCK_ADDRESSES[0], addressId: addressId, ...body }, timestamp: Date.now() };
    }
    if (addrUpdateMatch && method === 'delete') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Products ───────────────────────────────────────────────────────────────
  async ({ method, url, params }) => {
    if (url === '/search/products' && method === 'get') {
      await sleep(300 + Math.random() * 200);
      const search = params?.q || params?.search || '';
      const category = params?.category_id || params?.category;
      const priceMin = params?.price_min != null ? Number(params.price_min) : undefined;
      const priceMax = params?.price_max != null ? Number(params.price_max) : undefined;
      const inStock = params?.in_stock === true || params?.in_stock === 'true';
      const isFlash = params?.is_flash === true || params?.is_flash === 'true';
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      const sort = params?.sort || 'relevance';

      let filtered = MOCK_PRODUCTS;
      if (search) {
        filtered = filtered.filter(p =>
          p.productName.toLowerCase().includes(search.toLowerCase()) ||
          p.description?.toLowerCase().includes(search.toLowerCase())
        );
      }
      if (category) {
        filtered = filtered.filter(p => p.category?.toLowerCase().includes(String(category).toLowerCase()));
      }
      if (priceMin != null && !Number.isNaN(priceMin)) {
        filtered = filtered.filter(p => (p.price ?? 0) >= priceMin);
      }
      if (priceMax != null && !Number.isNaN(priceMax)) {
        filtered = filtered.filter(p => (p.price ?? 0) <= priceMax);
      }
      if (inStock) {
        filtered = filtered.filter(p => (p.stock ?? 0) > 0);
      }
      if (isFlash) {
        filtered = filtered.filter(p => Boolean((p as any).isFlash));
      }
      if (sort === 'price_asc') {
        filtered = [...filtered].sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
      } else if (sort === 'price_desc') {
        filtered = [...filtered].sort((a, b) => (b.price ?? 0) - (a.price ?? 0));
      } else if (sort === 'newest') {
        filtered = [...filtered].sort((a, b) => Date.parse(b.createdAt ?? '') - Date.parse(a.createdAt ?? ''));
      } else if (sort === 'popular') {
        filtered = [...filtered].sort((a, b) => (b.reviewsCount ?? 0) - (a.reviewsCount ?? 0));
      }

      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          totalResults: filtered.length,
          page,
          size,
          totalPages: Math.ceil(filtered.length / size),
          products: content.map(p => ({
            productId: p.productId,
            name: p.productName,
            sellerId: p.sellerId,
            sellerName: p.sellerName,
            categoryId: p.category,
            categoryName: p.category,
            priceMin: p.price ?? null,
            priceMax: p.originalPrice ?? p.price ?? null,
            images: p.images ?? [],
            stockAvailable: p.stock ?? 0,
            isFlash: Boolean((p as any).isFlash),
            thumbnailUrl: p.images?.[0] ?? '',
          })),
        },
        timestamp: Date.now(),
      };
    }

    if (url === '/search/products/suggest' && method === 'get') {
      await sleep(100 + Math.random() * 80);
      const query = (params?.q || '').toLowerCase();
      const size = Math.min(Number(params?.size) || 5, 10);
      const names = query
        ? MOCK_PRODUCTS
            .map(p => p.productName)
            .filter(name => name.toLowerCase().includes(query))
            .slice(0, size)
        : MOCK_PRODUCTS.slice(0, size).map(p => p.productName);
      return {
        success: true,
        data: { suggestions: names },
        timestamp: Date.now(),
      };
    }

    if ((url === '/products' || url === '/search') && method === 'get') {
      await sleep(300 + Math.random() * 200);
      const search = params?.q || params?.search || '';
      const category = params?.category;
      let filtered = MOCK_PRODUCTS;
      if (search) filtered = filtered.filter(p => p.productName.toLowerCase().includes(search.toLowerCase()) || p.description?.toLowerCase().includes(search.toLowerCase()));
      if (category) filtered = filtered.filter(p => p.category === category);
      return {
        success: true,
        data: { content: filtered, totalElements: filtered.length, totalPages: 1, last: true },
        timestamp: Date.now(),
      };
    }
    const productMatch = url?.match(/^\/products\/([^/]+)$/);
    if (productMatch && method === 'get') {
      await sleep(200 + Math.random() * 100);
      const product = MOCK_PRODUCTS.find(p => p.productId === productMatch[1]);
      if (!product) throw { response: { status: 404, data: { message: 'Product not found' } } };
      return { success: true, data: product, timestamp: Date.now() };
    }
    return null;
  },

  // ─── Auth ───────────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/auth/login' && method === 'post') {
      await sleep(500 + Math.random() * 200);
      return {
        success: true,
        data: {
          accessToken: 'mock_access_token_' + Date.now(),
          refreshToken: 'mock_refresh_token_' + Date.now(),
          tokenType: 'Bearer',
          expiresIn: 3600,
          refreshExpiresIn: 604800,
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          phone: '0901234567',
          fullName: 'Nguyễn Văn A',
          role: 'BUYER',
          roles: ['BUYER'],
          status: 'ACTIVE',
          avatarUrl: undefined,
        },
        timestamp: Date.now(),
      };
    }
    if (url === '/auth/register' && method === 'post') {
      await sleep(600 + Math.random() * 300);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          accessToken: 'mock_access_token_' + Date.now(),
          refreshToken: 'mock_refresh_token_' + Date.now(),
          tokenType: 'Bearer',
          expiresIn: 3600,
          refreshExpiresIn: 604800,
          userId: Date.now(),
          username: body.username || 'newuser',
          email: body.email || 'new@example.com',
          phone: body.phone,
          fullName: body.fullName,
          role: 'BUYER',
          roles: ['BUYER'],
          status: 'ACTIVE',
          avatarUrl: undefined,
        },
        timestamp: Date.now(),
      };
    }
    if (url === '/auth/refresh' && method === 'post') {
      await sleep(200);
      return {
        success: true,
        data: {
          accessToken: 'mock_access_token_' + Date.now(),
          refreshToken: 'mock_refresh_token_' + Date.now(),
          tokenType: 'Bearer',
          expiresIn: 3600,
          refreshExpiresIn: 604800,
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          role: 'BUYER',
          roles: ['BUYER'],
          status: 'ACTIVE',
        },
        timestamp: Date.now(),
      };
    }
    return null;
  },

  // ─── User Identity ───────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    // GET /users/me
    if (url === '/users/me' && method === 'get') {
      await sleep(200 + Math.random() * 100);
      return {
        success: true,
        data: {
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          phone: '0901234567',
          fullName: 'Nguyễn Văn A',
          avatarUrl: undefined,
          roles: ['BUYER'],
          status: 'ACTIVE',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-15T12:00:00Z',
        },
        timestamp: Date.now(),
      };
    }

    // PUT /users/me
    if (url === '/users/me' && method === 'put') {
      await sleep(300 + Math.random() * 100);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          userId: 1,
          username: 'nguyenvana',
          email: 'nguyenvana@example.com',
          phone: body.phone ?? '0901234567',
          fullName: body.fullName ?? 'Nguyễn Văn A',
          avatarUrl: body.avatarUrl,
          roles: ['BUYER'],
          status: 'ACTIVE',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: new Date().toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    // POST /users/me/change-password
    if (url === '/users/me/change-password' && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const body = JSON.parse(data || '{}');
      if (!body.currentPassword) {
        throw { response: { status: 400, data: { message: 'Mật khẩu hiện tại không được để trống' } } };
      }
      if (body.newPassword && body.newPassword.length < 6) {
        throw { response: { status: 400, data: { message: 'Mật khẩu mới phải có ít nhất 6 ký tự' } } };
      }
      return { success: true, data: null, timestamp: Date.now() };
    }

    // GET /users/me/avatar/presigned-url
    const avatarPresignedMatch = url?.match(/^\/users\/me\/avatar\/presigned-url/);
    if (avatarPresignedMatch && method === 'get') {
      await sleep(200 + Math.random() * 100);
      const objectKey = `user-avatars/user_${1}_${Date.now()}.jpg`;
      return {
        success: true,
        data: {
          uploadUrl: `https://minio.internal/${objectKey}`,
          objectKey,
          cdnUrl: `https://cdn.flashsale.com/${objectKey}`,
          expiresIn: 300,
        },
        timestamp: Date.now(),
      };
    }

    // GET /users/me/addresses
    if (url === '/users/me/addresses' && method === 'get') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: MOCK_ADDRESSES, timestamp: Date.now() };
    }

    // POST /users/me/addresses
    if (url === '/users/me/addresses' && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: { addressId: Date.now(), ...body, isDefault: body.isDefault ?? false },
        timestamp: Date.now(),
      };
    }

    const addrMatch = url?.match(/^\/users\/me\/addresses\/(\d+)$/);
    if (addrMatch && method === 'put') {
      await sleep(300 + Math.random() * 100);
      const body = JSON.parse(data || '{}');
      return { success: true, data: { addressId: parseInt(addrMatch[1]), ...body }, timestamp: Date.now() };
    }
    if (addrMatch && method === 'delete') {
      await sleep(300 + Math.random() * 100);
      return { success: true, data: null, timestamp: Date.now() };
    }

    // POST /users/me/roles/seller
    if (url === '/users/me/roles/seller' && method === 'post') {
      await sleep(500 + Math.random() * 200);
      return { success: true, data: null, timestamp: Date.now() };
    }

    return null;
  },

  // ─── Flash Sales ────────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    // GET /flash-sales — list sessions (public)
    if (url === '/flash-sales' && method === 'get') {
      await sleep(300 + Math.random() * 200);
      const status = params?.status;
      const filtered = status
        ? MOCK_FLASH_SESSIONS.filter(s => s.status === status)
        : MOCK_FLASH_SESSIONS;
      return {
        success: true,
        data: filtered,
        timestamp: Date.now(),
      };
    }

    // GET /flash-sales/{sessionId} — session detail with items
    const sessionDetailMatch = url?.match(/^\/flash-sales\/(\d+)$/);
    if (sessionDetailMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const sessionId = parseInt(sessionDetailMatch[1]);
      const session = MOCK_FLASH_SESSIONS.find(s => s.sessionId === sessionId);
      if (!session) throw { response: { status: 404, data: { message: 'Session not found' } } };
      const items = MOCK_FLASH_ITEMS.filter(i => i.sessionId === sessionId);
      return {
        success: true,
        data: { session, items },
        timestamp: Date.now(),
      };
    }

    // POST /flash-sales — create session (admin)
    if (url === '/flash-sales' && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const body = JSON.parse(data || '{}');
      const newSession = {
        sessionId: Date.now(),
        name: body.name,
        status: 'UPCOMING',
        startTime: body.startTime,
        endTime: body.endTime,
        secondsRemaining: Math.floor((new Date(body.endTime).getTime() - new Date(body.startTime).getTime()) / 1000),
        isEnded: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      MOCK_FLASH_SESSIONS.push(newSession);
      return { success: true, data: newSession, timestamp: Date.now() };
    }

    // PUT /flash-sales/{sessionId} — update session (admin)
    const updateSessionMatch = url?.match(/^\/flash-sales\/(\d+)$/);
    if (updateSessionMatch && method === 'put') {
      await sleep(400 + Math.random() * 200);
      const sessionId = parseInt(updateSessionMatch[1]);
      const body = JSON.parse(data || '{}');
      const session = MOCK_FLASH_SESSIONS.find(s => s.sessionId === sessionId);
      if (!session) throw { response: { status: 404, data: { message: 'Session not found' } } };
      if (body.name) session.name = body.name;
      if (body.startTime) session.startTime = body.startTime;
      if (body.endTime) session.endTime = body.endTime;
      session.updatedAt = new Date().toISOString();
      return { success: true, data: session, timestamp: Date.now() };
    }

    // DELETE /flash-sales/{sessionId} — delete session (admin)
    if (updateSessionMatch && method === 'delete') {
      await sleep(300 + Math.random() * 100);
      const sessionId = parseInt(updateSessionMatch[1]);
      const idx = MOCK_FLASH_SESSIONS.findIndex(s => s.sessionId === sessionId);
      if (idx === -1) throw { response: { status: 404, data: { message: 'Session not found' } } };
      MOCK_FLASH_SESSIONS.splice(idx, 1);
      return { success: true, data: null, timestamp: Date.now() };
    }

    return null;
  },

  // ─── Admin Refunds ──────────────────────────────────────────────────────────
  async ({ method, url, params, data }) => {
    if (url === '/admin/refunds' && method === 'get') {
      await sleep(400 + Math.random() * 200);
      const status = params?.status;
      const type = params?.type;
      const page = params?.page ?? 0;
      const size = params?.size ?? 20;
      let filtered = [...MOCK_REFUNDS];
      if (status) filtered = filtered.filter(r => r.status === status);
      if (type) filtered = filtered.filter(r => r.type === type);
      const start = page * size;
      const content = filtered.slice(start, start + size);
      return {
        success: true,
        data: {
          content,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
          last: start + size >= filtered.length,
        },
        timestamp: Date.now(),
      };
    }

    const adminRefundMatch = url?.match(/^\/admin\/refunds\/(\d+)$/);
    if (adminRefundMatch && method === 'get') {
      await sleep(300 + Math.random() * 100);
      const refundId = parseInt(adminRefundMatch[1]);
      const refund = MOCK_REFUNDS.find(r => r.refundId === refundId);
      if (!refund) throw { response: { status: 404, data: { message: 'Refund not found' } } };
      return { success: true, data: refund, timestamp: Date.now() };
    }

    const approveMatch = url?.match(/^\/admin\/refunds\/(\d+)\/approve$/);
    if (approveMatch && method === 'post') {
      await sleep(500 + Math.random() * 300);
      const refundId = parseInt(approveMatch[1]);
      const body = JSON.parse(data || '{}');
      return {
        success: true,
        data: {
          refundId: refundId,
          status: 'SUCCESS',
          amount: 299_000,
          trackingNumber: body.trackingNumber,
          reviewedBy: 'Admin Mock',
          reviewedAt: new Date().toISOString(),
        },
        timestamp: Date.now(),
      };
    }

    const rejectMatch = url?.match(/^\/admin\/refunds\/(\d+)\/reject$/);
    if (rejectMatch && method === 'post') {
      await sleep(400 + Math.random() * 200);
      const refundId = parseInt(rejectMatch[1]);
      return {
        success: true,
        data: { refundId: refundId, status: 'REJECTED' },
        timestamp: Date.now(),
      };
    }

    // ─── Categories ─────────────────────────────────────────────────────────
    if (url === '/categories' && method === 'get') {
      await sleep(200);
      return { success: true, data: MOCK_CATEGORIES, timestamp: Date.now() };
    }
    if (url === '/admin/categories' && method === 'post') {
      await sleep(300);
      const body = JSON.parse(data || '{}');
      const newCategory = {
        id: 'cat_' + Date.now(),
        name: body.name,
        slug: body.slug || body.name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, ''),
        description: body.description,
        parentId: body.parentId || null,
        productCount: 0,
      };
      MOCK_CATEGORIES.push(newCategory);
      return { success: true, data: newCategory, timestamp: Date.now() };
    }
    const editCategoryMatch = url?.match(/^\/admin\/categories\/(.+)$/);
    if (editCategoryMatch && method === 'put') {
      await sleep(300);
      const categoryId = editCategoryMatch[1];
      const body = JSON.parse(data || '{}');
      const category = MOCK_CATEGORIES.find(c => c.id === categoryId);
      if (category) {
        category.name = body.name;
        category.slug = body.slug || body.name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
        category.description = body.description;
        category.parentId = body.parentId || null;
      }
      return { success: true, data: category, timestamp: Date.now() };
    }
    if (editCategoryMatch && method === 'delete') {
      await sleep(300);
      const categoryId = editCategoryMatch[1];
      const index = MOCK_CATEGORIES.findIndex(c => c.id === categoryId);
      if (index !== -1) {
        MOCK_CATEGORIES.splice(index, 1);
      }
      return { success: true, data: null, timestamp: Date.now() };
    }

    // ─── Notifications ────────────────────────────────────────────────────────
    if (url === '/notifications' && method === 'get') {
      await sleep(200);
      return MOCK_NOTIFICATIONS;
    }
    const readNotificationMatch = url?.match(/^\/notifications\/(.+)\/read$/);
    if (readNotificationMatch && method === 'patch') {
      const notifId = readNotificationMatch[1];
      const notif = MOCK_NOTIFICATIONS.find(n => n.id === notifId);
      if (notif) {
        notif.read = true;
      }
      return notif;
    }
    if (url === '/notifications/read-all' && method === 'patch') {
      MOCK_NOTIFICATIONS.forEach(n => n.read = true);
      return { success: true, updated_count: MOCK_NOTIFICATIONS.length, user_id: 1 };
    }
    if (url === '/notifications/unread-count' && method === 'get') {
      const unreadCount = MOCK_NOTIFICATIONS.filter(n => !n.read).length;
      return { user_id: 1, unread_count: unreadCount };
    }

    // ─── AI Chat ─────────────────────────────────────────────────────────────
    if (url === '/ai/sessions' && method === 'post') {
      await sleep(300);
      const newSession = {
        id: 'sess_' + Date.now(),
        status: 'ACTIVE',
        createdAt: new Date().toISOString(),
      };
      MOCK_CHAT_SESSIONS.push(newSession);
      return { success: true, data: newSession, message: 'Session created', timestamp: Date.now() };
    }
    const closeSessionMatch = url?.match(/^\/ai\/sessions\/(.+)$/);
    if (closeSessionMatch && method === 'delete') {
      await sleep(300);
      const sessId = closeSessionMatch[1];
      const session = MOCK_CHAT_SESSIONS.find(s => s.id === sessId);
      if (session) {
        session.status = 'CLOSED';
      }
      return { success: true, data: null, message: 'Session closed', timestamp: Date.now() };
    }
    if (url === '/ai/chat/history' && method === 'get') {
      await sleep(200);
      return { success: true, data: MOCK_CHAT_HISTORY, timestamp: Date.now() };
    }
    if (url === '/ai/suggest' && method === 'get') {
      return { success: true, data: MOCK_CHAT_SUGGESTIONS, timestamp: Date.now() };
    }
    if (url === '/ai/confirm' && method === 'post') {
      await sleep(500);
      return { success: true, data: null, message: 'Action confirmed', timestamp: Date.now() };
    }

    // ─── Banners (admin) ───────────────────────────────────────────────────────
    if (url === '/admin/banners' && method === 'get') {
      await sleep(200);
      return { success: true, data: [...MOCK_BANNERS], timestamp: Date.now() };
    }
    if (url === '/admin/banners' && method === 'post') {
      await sleep(250);
      const body = JSON.parse(data || '{}');
      const created = { ...body, id: `bn-${Date.now()}`, active: body.active ?? true };
      MOCK_BANNERS.push(created);
      return { success: true, data: created, message: 'Đã tạo banner', timestamp: Date.now() };
    }
    if (url === '/admin/banners/presigned-url' && method === 'get') {
      await sleep(150);
      return { success: true, data: { uploadUrl: 'https://placehold.co/1200x400/16a34a/FFF?text=Anh+Vua+Upload' }, timestamp: Date.now() };
    }
    const bannerMatch = url?.match(/^\/admin\/banners\/([\w-]+)$/);
    if (bannerMatch && method === 'put') {
      await sleep(200);
      const idx = MOCK_BANNERS.findIndex((b) => b.id === bannerMatch[1]);
      const body = JSON.parse(data || '{}');
      if (idx >= 0) MOCK_BANNERS[idx] = { ...MOCK_BANNERS[idx], ...body };
      return { success: true, data: MOCK_BANNERS[idx], message: 'Đã cập nhật banner', timestamp: Date.now() };
    }
    if (bannerMatch && method === 'delete') {
      await sleep(200);
      const idx = MOCK_BANNERS.findIndex((b) => b.id === bannerMatch[1]);
      if (idx >= 0) MOCK_BANNERS.splice(idx, 1);
      return { success: true, data: null, message: 'Đã xoá banner', timestamp: Date.now() };
    }

    return null;
  },
];
