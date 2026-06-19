import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

// ─── Mock API implementations ────────────────────────────────────────────────

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

// ─── Mock data ─────────────────────────────────────────────────────────────

const MOCK_ADDRESSES = [
  {
    addressId: 1,
    provinceId: 1,
    districtId: 1,
    fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
    isDefault: true,
  },
  {
    addressId: 2,
    provinceId: 2,
    districtId: 5,
    fullAddress: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
    isDefault: false,
  },
];

const MOCK_CART = {
  customerId: 1,
  items: [
    {
      variantId: 'v1',
      variantCode: 'SONY-WH-1000XM5-BK',
      variantName: 'Đen / Chính hãng',
      productName: 'Tai nghe Sony WH-1000XM5',
      priceSnapshot: 6_490_000,
      currentPrice: 6_490_000,
      priceChanged: false,
      quantity: 1,
      stockAvailable: 15,
      variantImageSnapshot: 'https://placehold.co/400x400/1a1a2e/FFF?text=Sony+XM5',
      subtotal: 6_490_000,
      outOfStock: false,
      unavailable: false,
      insufficientStock: false,
      sellerId: 1,
    },
    {
      variantId: 'v3',
      variantCode: 'KEY-K2-WHITE',
      variantName: 'Trắng / Hot-swap',
      productName: 'Bàn phím cơ Keychron K2',
      priceSnapshot: 2_190_000,
      currentPrice: 2_190_000,
      priceChanged: false,
      quantity: 2,
      stockAvailable: 8,
      variantImageSnapshot: 'https://placehold.co/400x400/2d2d44/FFF?text=Keychron+K2',
      subtotal: 4_380_000,
      outOfStock: false,
      unavailable: false,
      insufficientStock: false,
      sellerId: 1,
    },
    {
      variantId: 'v5',
      variantCode: 'UQ-TSHIRT-M',
      variantName: 'Xanh dương / Size M',
      productName: 'Áo thun Uniqlo DRY-EX',
      priceSnapshot: 299_000,
      currentPrice: 299_000,
      priceChanged: false,
      quantity: 1,
      stockAvailable: 20,
      variantImageSnapshot: 'https://placehold.co/400x400/e63946/FFF?text=Uniqlo',
      subtotal: 299_000,
      outOfStock: false,
      unavailable: false,
      insufficientStock: false,
      sellerId: 2,
    },
  ],
  totalItems: 4,
  subtotal: 11_169_000,
  hasPriceChanges: false,
  groupedBySeller: {
    "1": [
      {
        variantId: 'v1',
        variantCode: 'SONY-WH-1000XM5-BK',
        variantName: 'Đen / Chính hãng',
        productName: 'Tai nghe Sony WH-1000XM5',
        priceSnapshot: 6_490_000,
        currentPrice: 6_490_000,
        priceChanged: false,
        quantity: 1,
        stockAvailable: 15,
        variantImageSnapshot: 'https://placehold.co/400x400/1a1a2e/FFF?text=Sony+XM5',
        subtotal: 6_490_000,
        outOfStock: false,
        unavailable: false,
        insufficientStock: false,
        sellerId: 1,
      },
      {
        variantId: 'v3',
        variantCode: 'KEY-K2-WHITE',
        variantName: 'Trắng / Hot-swap',
        productName: 'Bàn phím cơ Keychron K2',
        priceSnapshot: 2_190_000,
        currentPrice: 2_190_000,
        priceChanged: false,
        quantity: 2,
        stockAvailable: 8,
        variantImageSnapshot: 'https://placehold.co/400x400/2d2d44/FFF?text=Keychron+K2',
        subtotal: 4_380_000,
        outOfStock: false,
        unavailable: false,
        insufficientStock: false,
        sellerId: 1,
      },
    ],
    "2": [
      {
        variantId: 'v5',
        variantCode: 'UQ-TSHIRT-M',
        variantName: 'Xanh dương / Size M',
        productName: 'Áo thun Uniqlo DRY-EX',
        priceSnapshot: 299_000,
        currentPrice: 299_000,
        priceChanged: false,
        quantity: 1,
        stockAvailable: 20,
        variantImageSnapshot: 'https://placehold.co/400x400/e63946/FFF?text=Uniqlo',
        subtotal: 299_000,
        outOfStock: false,
        unavailable: false,
        insufficientStock: false,
        sellerId: 2,
      },
    ],
  },
};

const MOCK_PRODUCTS = [
  {
    id: '1',
    name: 'Tai nghe Sony WH-1000XM5',
    description: 'Tai nghe chống ồn tốt nhất thế giới, pin 30h.',
    price: 6_490_000,
    originalPrice: 7_990_000,
    category: 'Thiết bị âm thanh',
    categoryId: 'cat_4',
    categoryName: 'Thiết bị âm thanh',
    sellerId: 1,
    sellerName: 'Shop Sony',
    variants: [
      { id: 'v1', productId: '1', variantCode: 'SONY-WH-1000XM5-BK', variantName: 'Đen / Chính hãng', price: 6_490_000, originalPrice: 7_990_000, stockQuantity: 15, status: 'ACTIVE' },
      { id: 'v2', productId: '1', variantCode: 'SONY-WH-1000XM5-WH', variantName: 'Trắng / Chính hãng', price: 6_490_000, originalPrice: 7_990_000, stockQuantity: 8, status: 'ACTIVE' },
    ],
    images: ['https://placehold.co/400x400/1a1a2e/FFF?text=Sony+XM5'],
    stock: 23,
    rating: 4.9,
    reviewsCount: 2341,
    status: 'ACTIVE',
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: '2',
    name: 'Bàn phím cơ Keychron K2',
    description: 'Bàn phím cơ không dây 75%, switch Gateron.',
    price: 2_190_000,
    originalPrice: 2_490_000,
    category: 'Bàn phím',
    categoryId: 'cat_1',
    categoryName: 'Bàn phím',
    sellerId: 1,
    sellerName: 'Shop Sony',
    variants: [
      { id: 'v3', productId: '2', variantCode: 'KEY-K2-WHITE', variantName: 'Trắng / Hot-swap', price: 2_190_000, originalPrice: 2_490_000, stockQuantity: 8, status: 'ACTIVE' },
      { id: 'v4', productId: '2', variantCode: 'KEY-K2-BLACK', variantName: 'Đen / Hot-swap', price: 2_190_000, originalPrice: 2_490_000, stockQuantity: 12, status: 'ACTIVE' },
    ],
    images: ['https://placehold.co/400x400/2d2d44/FFF?text=Keychron+K2'],
    stock: 20,
    rating: 4.7,
    reviewsCount: 876,
    status: 'ACTIVE',
    createdAt: '2024-01-05T00:00:00Z',
  },
  {
    id: '3',
    name: 'Áo thun Uniqlo DRY-EX',
    description: 'Áo thun thể thao nam, vải nhanh khô.',
    price: 299_000,
    originalPrice: 399_000,
    category: 'Thời trang nam',
    categoryId: 'cat_2',
    categoryName: 'Thời trang nam',
    sellerId: 2,
    sellerName: 'Uniqlo Vietnam',
    variants: [
      { id: 'v5', productId: '3', variantCode: 'UQ-TSHIRT-M', variantName: 'Xanh dương / Size M', price: 299_000, originalPrice: 399_000, stockQuantity: 20, status: 'ACTIVE' },
      { id: 'v6', productId: '3', variantCode: 'UQ-TSHIRT-L', variantName: 'Đỏ / Size L', price: 299_000, originalPrice: 399_000, stockQuantity: 15, status: 'ACTIVE' },
    ],
    images: ['https://placehold.co/400x400/e63946/FFF?text=Uniqlo'],
    stock: 35,
    rating: 4.5,
    reviewsCount: 421,
    status: 'ACTIVE',
    createdAt: '2024-01-10T00:00:00Z',
  },
  {
    id: '4',
    name: 'AirPods Pro 2',
    description: 'Tai nghe không dây chống ồn chủ động.',
    price: 5_990_000,
    originalPrice: 6_990_000,
    category: 'Thiết bị âm thanh',
    categoryId: 'cat_4',
    categoryName: 'Thiết bị âm thanh',
    sellerId: 3,
    sellerName: 'Apple Store VN',
    variants: [
      { id: 'v7', productId: '4', variantCode: 'APP-AIRPODS-PRO2', variantName: 'Trắng / Chính hãng', price: 5_990_000, originalPrice: 6_990_000, stockQuantity: 30, status: 'ACTIVE' },
    ],
    images: ['https://placehold.co/400x400/f8f9fa/333?text=AirPods'],
    stock: 30,
    rating: 4.8,
    reviewsCount: 3421,
    status: 'ACTIVE',
    createdAt: '2024-01-15T00:00:00Z',
  },
];

const MOCK_ORDERS = [
  {
    orderId: 1,
    parentOrderId: 1,
    orderCode: 'FS-20240115-0001',
    sellerId: 1,
    sellerName: 'Shop Sony',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'DELIVERED',
    totalAmt: 6_490_000,
    finalAmt: 6_490_000,
    itemCount: 1,
    isFlashSale: false,
    createdAt: '2024-01-15T10:30:00Z',
    updatedAt: '2024-01-17T14:00:00Z',
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    trackingNumber: 'VT123456789',
    carrier: 'ViettelPost',
    items: [
      {
        orderItemId: 101,
        skuCode: 'SONY-WH-1000XM5-BK',
        productName: 'Tai nghe Sony WH-1000XM5',
        variantName: 'Đen / Chính hãng',
        imageSnapshot: 'https://placehold.co/80x80/1a1a2e/FFF?text=Sony',
        priceSnapshot: 6_490_000,
        quantity: 1,
        refundedQuantity: 0,
      },
    ],
  },
  {
    orderId: 2,
    parentOrderId: 2,
    orderCode: 'FS-20240120-0002',
    sellerId: 2,
    sellerName: 'Uniqlo Vietnam',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'SHIPPING',
    totalAmt: 299_000,
    finalAmt: 299_000,
    itemCount: 1,
    isFlashSale: false,
    createdAt: '2024-01-20T09:15:00Z',
    updatedAt: '2024-01-21T08:00:00Z',
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    trackingNumber: 'GHN987654321',
    carrier: 'GHN',
    items: [
      {
        orderItemId: 201,
        skuCode: 'UQ-TSHIRT-M',
        productName: 'Áo thun Uniqlo DRY-EX',
        variantName: 'Xanh dương / Size M',
        imageSnapshot: 'https://placehold.co/80x80/e63946/FFF?text=Uniqlo',
        priceSnapshot: 299_000,
        quantity: 1,
        refundedQuantity: 0,
      },
    ],
  },
  {
    orderId: 3,
    parentOrderId: 3,
    orderCode: 'FS-20240122-0003',
    sellerId: 1,
    sellerName: 'Shop Sony',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'PENDING',
    totalAmt: 2_190_000,
    finalAmt: 2_190_000,
    isFlashSale: true,
    itemCount: 1,
    createdAt: '2024-01-22T16:45:00Z',
    updatedAt: '2024-01-22T16:45:00Z',
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    items: [
      {
        orderItemId: 301,
        skuCode: 'KEY-K2-WHITE',
        productName: 'Bàn phím cơ Keychron K2',
        variantName: 'Trắng / Hot-swap',
        imageSnapshot: 'https://placehold.co/80x80/2d2d44/FFF?text=Keychron',
        priceSnapshot: 2_190_000,
        quantity: 1,
        refundedQuantity: 0,
      },
    ],
  },
  {
    orderId: 4,
    parentOrderId: 4,
    orderCode: 'FS-20240123-0004',
    sellerId: 3,
    sellerName: 'Apple Store VN',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'PAID',
    totalAmt: 5_990_000,
    finalAmt: 5_990_000,
    itemCount: 1,
    isFlashSale: false,
    createdAt: '2024-01-23T11:00:00Z',
    updatedAt: '2024-01-23T11:05:00Z',
    shippingAddress: {
      fullAddress: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
      provinceId: 2,
      districtId: 5,
    },
    items: [
      {
        orderItemId: 401,
        skuCode: 'APP-AIRPODS-PRO2',
        productName: 'AirPods Pro 2',
        variantName: 'Trắng / Chính hãng',
        imageSnapshot: 'https://placehold.co/80x80/f8f9fa/333?text=AirPods',
        priceSnapshot: 5_990_000,
        quantity: 1,
        refundedQuantity: 0,
      },
    ],
  },
  {
    orderId: 5,
    parentOrderId: 5,
    orderCode: 'FS-20240110-0005',
    sellerId: 1,
    sellerName: 'Shop Sony',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'CANCELLED',
    totalAmt: 3_000_000,
    finalAmt: 3_000_000,
    itemCount: 1,
    cancelledBy: 'BUYER',
    cancelReason: 'Thay đổi ý định',
    createdAt: '2024-01-10T08:00:00Z',
    updatedAt: '2024-01-10T09:00:00Z',
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    items: [],
  },
  {
    orderId: 6,
    parentOrderId: 6,
    orderCode: 'FS-20240108-0006',
    sellerId: 2,
    sellerName: 'Uniqlo Vietnam',
    buyerId: 1,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'PARTIALLY_REFUNDED',
    totalAmt: 598_000,
    finalAmt: 299_000,
    itemCount: 2,
    createdAt: '2024-01-08T14:00:00Z',
    updatedAt: '2024-01-12T10:00:00Z',
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    items: [
      {
        orderItemId: 601,
        skuCode: 'UQ-TSHIRT-M',
        productName: 'Áo thun Uniqlo DRY-EX',
        variantName: 'Xanh dương / Size M',
        priceSnapshot: 299_000,
        quantity: 2,
        refundedQuantity: 1,
      },
    ],
  },
];

const MOCK_PARENT_ORDERS = [
  {
    parentOrderId: 1,
    orderCode: 'PO-20240115-0001',
    status: 'DELIVERED',
    totalAmt: 6_490_000,
    finalAmt: 6_490_000,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    orders: [MOCK_ORDERS[0]],
    createdAt: '2024-01-15T10:30:00Z',
    updatedAt: '2024-01-17T14:00:00Z',
  },
  {
    parentOrderId: 2,
    orderCode: 'PO-20240120-0002',
    status: 'SHIPPING',
    totalAmt: 299_000,
    finalAmt: 299_000,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    orders: [MOCK_ORDERS[1]],
    createdAt: '2024-01-20T09:15:00Z',
    updatedAt: '2024-01-21T08:00:00Z',
  },
  {
    parentOrderId: 3,
    orderCode: 'PO-20240122-0003',
    status: 'PENDING',
    totalAmt: 2_190_000,
    finalAmt: 2_190_000,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    orders: [MOCK_ORDERS[2]],
    createdAt: '2024-01-22T16:45:00Z',
    updatedAt: '2024-01-22T16:45:00Z',
  },
  {
    parentOrderId: 4,
    orderCode: 'PO-20240123-0004',
    status: 'PAID',
    totalAmt: 5_990_000,
    finalAmt: 5_990_000,
    shippingAddress: {
      fullAddress: '45 Đường Lê Lợi, Quận Hải Châu, TP. Đà Nẵng',
      provinceId: 2,
      districtId: 5,
    },
    orders: [MOCK_ORDERS[3]],
    createdAt: '2024-01-23T11:00:00Z',
    updatedAt: '2024-01-23T11:05:00Z',
  },
  {
    parentOrderId: 5,
    orderCode: 'PO-20240110-0005',
    status: 'CANCELLED',
    totalAmt: 3_000_000,
    finalAmt: 3_000_000,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    orders: [MOCK_ORDERS[4]],
    createdAt: '2024-01-10T08:00:00Z',
    updatedAt: '2024-01-10T09:00:00Z',
  },
  {
    parentOrderId: 6,
    orderCode: 'PO-20240108-0006',
    status: 'PARTIALLY_REFUNDED',
    totalAmt: 598_000,
    finalAmt: 299_000,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    orders: [MOCK_ORDERS[5]],
    createdAt: '2024-01-08T14:00:00Z',
    updatedAt: '2024-01-12T10:00:00Z',
  },
];

const MOCK_PAYMENTS = [
  {
    transactionId: 1,
    parentOrderId: 1,
    amount: 6_490_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripePiId: 'pi_mock_1_aabbcc',
    applicationFee: 194_700,
    transRef: 'TXN-20240115-001',
    paidAt: '2024-01-15T10:32:00Z',
    remainingSeconds: null,
  },
  {
    transactionId: 2,
    parentOrderId: 2,
    amount: 299_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripePiId: 'pi_mock_2_ddeeff',
    applicationFee: 8_970,
    transRef: 'TXN-20240120-002',
    paidAt: '2024-01-20T09:18:00Z',
    remainingSeconds: null,
  },
  {
    transactionId: 3,
    parentOrderId: 3,
    amount: 2_190_000,
    method: 'stripe',
    status: 'PENDING',
    stripePiId: 'pi_mock_3_112233',
    applicationFee: 65_700,
    transRef: 'TXN-20240122-003',
    paidAt: null,
    remainingSeconds: 542,
  },
  {
    transactionId: 4,
    parentOrderId: 4,
    amount: 5_990_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripePiId: 'pi_mock_4_556677',
    applicationFee: 179_700,
    transRef: 'TXN-20240123-004',
    paidAt: '2024-01-23T11:05:00Z',
    remainingSeconds: null,
  },
  {
    transactionId: 5,
    parentOrderId: 5,
    amount: 3_000_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripePiId: 'pi_mock_5_889900',
    applicationFee: 90_000,
    transRef: 'TXN-20240110-005',
    paidAt: '2024-01-10T08:05:00Z',
    remainingSeconds: null,
  },
  {
    transactionId: 6,
    parentOrderId: 6,
    amount: 598_000,
    method: 'stripe',
    status: 'SUCCESS',
    stripePiId: 'pi_mock_6_aabbdd',
    applicationFee: 17_940,
    transRef: 'TXN-20240108-006',
    paidAt: '2024-01-08T14:05:00Z',
    remainingSeconds: null,
  },
];

const MOCK_SELLER_STRIPE_ACCOUNTS = [
  {
    sellerId: 1,
    stripeAccountId: 'acct_mock_seller_1',
    accountStatus: 'ACTIVE',
    detailsSubmitted: true,
    chargesEnabled: true,
    payoutsEnabled: true,
    onboardingStatus: 'COMPLETE',
    expressDashboardUrl: 'https://dashboard.stripe.com/test/connect/accounts/acct_mock_seller_1',
    createdAt: '2026-01-02T09:00:00Z',
    updatedAt: '2026-01-05T10:30:00Z',
  },
  {
    sellerId: 2,
    stripeAccountId: 'acct_mock_seller_2',
    accountStatus: 'PENDING',
    detailsSubmitted: false,
    chargesEnabled: false,
    payoutsEnabled: false,
    onboardingStatus: 'IN_PROGRESS',
    expressDashboardUrl: 'https://dashboard.stripe.com/test/connect/accounts/acct_mock_seller_2',
    createdAt: '2026-01-04T08:00:00Z',
    updatedAt: '2026-01-04T08:45:00Z',
  },
  {
    sellerId: 3,
    stripeAccountId: 'acct_mock_seller_3',
    accountStatus: 'SUSPENDED',
    detailsSubmitted: true,
    chargesEnabled: false,
    payoutsEnabled: false,
    onboardingStatus: 'SUSPENDED',
    expressDashboardUrl: 'https://dashboard.stripe.com/test/connect/accounts/acct_mock_seller_3',
    createdAt: '2026-01-06T11:00:00Z',
    updatedAt: '2026-01-08T14:15:00Z',
  },
];

const MOCK_REFUNDS = [
  {
    refundId: 1,
    orderId: 6,
    groupRef: 'GRP-20240112-001',
    type: 'PARTIAL',
    status: 'SUCCESS',
    amount: 299_000,
    reason: 'Sản phẩm lỗi',
    initiatedBy: 'BUYER',
    adminNote: 'Đã duyệt hoàn tiền do lỗi vải.',
    adjustAmount: undefined,
    reviewedBy: 100,
    reviewedAt: '2024-01-12T10:30:00Z',
    stripe_refundId: 're_mock_1_abc123',
    items: [
      {
        orderItemId: 601,
        quantity: 1,
        refundAmount: 299_000,
        itemReason: 'Vải bị rách',
        status: 'SUCCESS',
      },
    ],
    createdAt: '2024-01-11T10:00:00Z',
    updatedAt: '2024-01-12T10:30:00Z',
  },
  {
    refundId: 2,
    orderId: 6,
    groupRef: 'GRP-20240118-002',
    type: 'PARTIAL',
    status: 'PENDING',
    amount: 150_000,
    reason: 'Giao thiếu sản phẩm',
    initiatedBy: 'BUYER',
    adminNote: undefined,
    adjustAmount: undefined,
    reviewedBy: undefined,
    reviewedAt: undefined,
    stripe_refundId: undefined,
    items: [
      {
        orderItemId: 601,
        quantity: 1,
        refundAmount: 150_000,
        itemReason: 'Thiếu 1 sản phẩm',
        status: 'PENDING',
      },
    ],
    createdAt: '2024-01-18T14:00:00Z',
    updatedAt: '2024-01-18T14:00:00Z',
  },
  {
    refundId: 3,
    orderId: 5,
    groupRef: 'GRP-20240110-003',
    type: 'FULL',
    status: 'REJECTED',
    amount: 3_000_000,
    reason: 'Đã quá thời hạn hoàn tiền',
    initiatedBy: 'BUYER',
    adminNote: undefined,
    rejectReason: 'Đã quá thời hạn hoàn tiền 7 ngày',
    fraudEvidence: false,
    adjustAmount: undefined,
    reviewedBy: 100,
    reviewedAt: '2024-01-11T09:00:00Z',
    stripe_refundId: undefined,
    items: undefined,
    createdAt: '2024-01-10T12:00:00Z',
    updatedAt: '2024-01-11T09:00:00Z',
  },
  {
    refundId: 4,
    orderId: 1,
    groupRef: 'GRP-20240117-004',
    type: 'PARTIAL',
    status: 'PENDING',
    amount: 500_000,
    reason: 'Sản phẩm hư hỏng trong vận chuyển',
    initiatedBy: 'BUYER',
    adminNote: undefined,
    adjustAmount: undefined,
    reviewedBy: undefined,
    reviewedAt: undefined,
    stripe_refundId: undefined,
    items: [
      {
        orderItemId: 101,
        quantity: 1,
        refundAmount: 500_000,
        itemReason: 'Hộp bị móp, sản phẩm trầy',
        status: 'PENDING',
      },
    ],
    createdAt: '2024-01-17T16:00:00Z',
    updatedAt: '2024-01-17T16:00:00Z',
  },
  {
    refundId: 5,
    orderId: 4,
    groupRef: 'GRP-20240125-005',
    type: 'FULL',
    status: 'PENDING',
    amount: 5_990_000,
    reason: 'Thay đổi ý định',
    initiatedBy: 'BUYER',
    adminNote: undefined,
    adjustAmount: undefined,
    reviewedBy: undefined,
    reviewedAt: undefined,
    stripe_refundId: undefined,
    items: undefined,
    createdAt: '2024-01-25T10:00:00Z',
    updatedAt: '2024-01-25T10:00:00Z',
  },
];

const MOCK_SELLER_ORDERS = [
  {
    orderId: 1,
    parentOrderId: 1,
    orderCode: 'FS-20240115-0001',
    buyerId: 10,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'DELIVERED',
    totalAmt: 6_490_000,
    finalAmt: 6_490_000,
    isFlashSale: false,
    itemCount: 1,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    trackingNumber: 'VT123456789',
    carrier: 'ViettelPost',
    createdAt: '2024-01-15T10:30:00Z',
    updatedAt: '2024-01-17T14:00:00Z',
  },
  {
    orderId: 3,
    parentOrderId: 3,
    orderCode: 'FS-20240122-0003',
    buyerId: 10,
    buyerName: 'Nguyễn Văn A',
    buyerUsername: 'nguyenvana',
    status: 'PENDING',
    totalAmt: 2_190_000,
    finalAmt: 2_190_000,
    isFlashSale: true,
    itemCount: 1,
    shippingAddress: {
      fullAddress: '123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 1,
    },
    trackingNumber: null,
    carrier: null,
    createdAt: '2024-01-22T16:45:00Z',
    updatedAt: '2024-01-22T16:45:00Z',
  },
  {
    orderId: 7,
    parentOrderId: 7,
    orderCode: 'FS-20240124-0007',
    buyerId: 11,
    buyerName: 'Trần Thị B',
    buyerUsername: 'tranthib',
    status: 'PAID',
    totalAmt: 12_980_000,
    finalAmt: 12_980_000,
    isFlashSale: false,
    itemCount: 2,
    shippingAddress: {
      fullAddress: '78 Đường Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 3,
    },
    trackingNumber: null,
    carrier: null,
    createdAt: '2024-01-24T08:00:00Z',
    updatedAt: '2024-01-24T08:05:00Z',
  },
  {
    orderId: 8,
    parentOrderId: 8,
    orderCode: 'FS-20240121-0008',
    buyerId: 12,
    buyerName: 'Lê Minh C',
    buyerUsername: 'leminhc',
    status: 'SHIPPING',
    totalAmt: 3_200_000,
    finalAmt: 3_200_000,
    isFlashSale: false,
    itemCount: 1,
    shippingAddress: {
      fullAddress: '15 Đường Cái Khế, Quận Ninh Kiều, TP. Cần Thơ',
      provinceId: 3,
      districtId: 10,
    },
    trackingNumber: 'GHTK555666777',
    carrier: 'GHTK',
    createdAt: '2024-01-21T13:00:00Z',
    updatedAt: '2024-01-22T10:00:00Z',
  },
  {
    orderId: 9,
    parentOrderId: 9,
    orderCode: 'FS-20240118-0009',
    buyerId: 13,
    buyerName: 'Phạm Hoàng D',
    buyerUsername: 'phamhoangd',
    status: 'CANCELLED',
    totalAmt: 1_500_000,
    finalAmt: 1_500_000,
    itemCount: 1,
    shippingAddress: {
      fullAddress: '88 Đường Lý Thường Kiệt, Quận 5, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 5,
    },
    trackingNumber: null,
    carrier: null,
    createdAt: '2024-01-18T09:00:00Z',
    updatedAt: '2024-01-18T10:30:00Z',
  },
  {
    orderId: 10,
    parentOrderId: 10,
    orderCode: 'FS-20240105-0010',
    buyerId: 14,
    buyerName: 'Võ Đình E',
    buyerUsername: 'vodinhe',
    status: 'REFUNDED',
    totalAmt: 4_000_000,
    finalAmt: 4_000_000,
    itemCount: 1,
    shippingAddress: {
      fullAddress: '200 Đường 3/2, Quận 10, TP. Hồ Chí Minh',
      provinceId: 1,
      districtId: 10,
    },
    trackingNumber: null,
    carrier: null,
    createdAt: '2024-01-05T11:00:00Z',
    updatedAt: '2024-01-12T15:00:00Z',
  },
];

// ─── Checkout state ──────────────────────────────────────────────────────────

let checkoutOrderData: Record<number, {
  parentOrderId: number;
  orderCode: string;
  orders: any[];
  totalAmount: number;
  finalAmount: number;
  itemsCount: number;
  timeoutAt: string;
  createdAt: string;
}> = {};

// Track mock payment "started" time to simulate success after a delay
let mockPaymentStartedAt: Record<number, number> = {};

// ─── Categories Mock Data ──────────────────────────────────────────────────
export const MOCK_CATEGORIES = [
  { id: 'cat_1', name: 'Thiết bị điện tử', slug: 'thiet-bi-dien-tu', description: 'Điện thoại, Laptop, Máy tính bảng', parentId: null, productCount: 15 },
  { id: 'cat_2', name: 'Thời trang', slug: 'thoi-trang', description: 'Quần áo, giày dép, phụ kiện', parentId: null, productCount: 20 },
  { id: 'cat_3', name: 'Gia dụng', slug: 'gia-dung', description: 'Đồ dùng nhà bếp, phòng khách', parentId: null, productCount: 8 },
  { id: 'cat_4', name: 'Điện thoại', slug: 'dien-thoai', description: 'Điện thoại thông minh các hãng', parentId: 'cat_1', productCount: 10 },
  { id: 'cat_5', name: 'Laptop', slug: 'laptop', description: 'Laptop văn phòng, gaming', parentId: 'cat_1', productCount: 5 }
];

// ─── Notifications Mock Data ────────────────────────────────────────────────
export const MOCK_NOTIFICATIONS = [
  {
    id: 'notif_1',
    userId: 1,
    type: 'ORDER_STATUS',
    title: 'Đơn hàng đã được xác nhận',
    message: 'Đơn hàng #PO-20240101-1002 của bạn đã được người bán xác nhận và đang chuẩn bị giao.',
    data: { orderId: 1002 },
    read: false,
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString()
  },
  {
    id: 'notif_2',
    userId: 1,
    type: 'PROMOTION',
    title: 'Khuyến mãi Flash Sale 50%',
    message: 'Sự kiện Flash Sale cực lớn sắp bắt đầu vào lúc 12:00 hôm nay. Đừng bỏ lỡ!',
    data: { sessionId: 1 },
    read: true,
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString()
  },
  {
    id: 'notif_3',
    userId: 1,
    type: 'REFUND_STATUS',
    title: 'Yêu cầu trả hàng/hoàn tiền được chấp nhận',
    message: 'Yêu cầu hoàn tiền cho đơn hàng #PO-20240101-1001 đã được duyệt. Số tiền sẽ hoàn lại vào tài khoản của bạn trong 2-3 ngày làm việc.',
    data: { refundId: 1 },
    read: false,
    createdAt: new Date(Date.now() - 600000).toISOString()
  }
];

// ─── AI Chat Mock Data ──────────────────────────────────────────────────────
export const MOCK_CHAT_SESSIONS = [
  { id: 'sess_1', status: 'ACTIVE', createdAt: new Date().toISOString() }
];

export const MOCK_CHAT_SUGGESTIONS = [
  { text: 'Kiểm tra tình trạng đơn hàng mới nhất', icon: '📦' },
  { text: 'Tôi muốn yêu cầu hoàn tiền', icon: '💰' },
  { text: 'Tìm sản phẩm tai nghe chống ồn', icon: '🎧' }
];

export const MOCK_CHAT_HISTORY = [
  { id: 'msg_1', sessionId: 'sess_1', role: 'SYSTEM', content: 'Bạn là một trợ lý AI hữu ích phục vụ cho sàn thương mại điện tử Stealing From Paradise.', sequenceNo: 1, createdAt: new Date(Date.now() - 100000).toISOString() },
  { id: 'msg_2', sessionId: 'sess_1', role: 'ASSISTANT', content: 'Xin chào! Tôi có thể giúp gì cho bạn hôm nay?', sequenceNo: 2, createdAt: new Date(Date.now() - 90000).toISOString() }
];

// ─── Flash Sale Mock Data ───────────────────────────────────────────────────

const MOCK_FLASH_SESSIONS = [
  {
    sessionId: 1,
    name: 'Flash Sale 12:00 Trưa Nay',
    status: 'ACTIVE',
    startTime: new Date(Date.now() - 3600000).toISOString(),
    endTime: new Date(Date.now() + 3600000).toISOString(),
    secondsRemaining: 3600,
    isEnded: false,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    sessionId: 2,
    name: 'Flash Sale 20:00 Tối Mai',
    status: 'UPCOMING',
    startTime: new Date(Date.now() + 86400000).toISOString(),
    endTime: new Date(Date.now() + 90000000).toISOString(),
    secondsRemaining: 90000,
    isEnded: false,
    createdAt: new Date(Date.now() - 172800000).toISOString(),
    updatedAt: new Date(Date.now() - 172800000).toISOString(),
  },
  {
    sessionId: 3,
    name: 'Flash Sale Tuần Trước',
    status: 'ENDED',
    startTime: new Date(Date.now() - 604800000).toISOString(),
    endTime: new Date(Date.now() - 518400000).toISOString(),
    secondsRemaining: 0,
    isEnded: true,
    createdAt: new Date(Date.now() - 691200000).toISOString(),
    updatedAt: new Date(Date.now() - 518400000).toISOString(),
  },
];

const MOCK_FLASH_ITEMS = [
  {
    id: 101,
    sessionId: 1,
    skuCode: 'SONY-WH-1000XM5-BK',
    flashPrice: 4_990_000,
    flashStock: 50,
    limitPerUser: 2,
    soldQty: 23,
    status: 'ACTIVE',
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    id: 102,
    sessionId: 1,
    skuCode: 'KEY-K2-WHITE',
    flashPrice: 1_590_000,
    flashStock: 30,
    limitPerUser: 1,
    soldQty: 30,
    status: 'SOLD_OUT',
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 103,
    sessionId: 1,
    skuCode: 'APP-AIRPODS-PRO2',
    flashPrice: 4_490_000,
    flashStock: 20,
    limitPerUser: 1,
    soldQty: 5,
    status: 'ACTIVE',
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    id: 201,
    sessionId: 2,
    skuCode: 'UQ-TSHIRT-M',
    flashPrice: 149_000,
    flashStock: 100,
    limitPerUser: 3,
    soldQty: 0,
    status: 'ACTIVE',
    createdAt: new Date(Date.now() - 172800000).toISOString(),
    updatedAt: new Date(Date.now() - 172800000).toISOString(),
  },
];

// ─── Mock handlers ───────────────────────────────────────────────────────────

type MockHandler = (config: InternalAxiosRequestConfig) => Promise<any>;

// Wishlist mock state — seed sẵn 1 sản phẩm để demo có dữ liệu.
const MOCK_WISHLIST = new Set<string>(['1']);

const mockHandlers: MockHandler[] = [
  // ─── Wishlist ─────────────────────────────────────────────────────────────
  async ({ method, url, data }) => {
    if (url === '/wishlist' && method === 'get') {
      await sleep(250 + Math.random() * 150);
      const content = MOCK_PRODUCTS.filter((p) => MOCK_WISHLIST.has(p.id)).map((p) => ({
        productId: p.id,
        productName: p.name,
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
  async ({ method, url, data }) => {
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
      const body = JSON.parse(data || '{}');
      const variantId = body.variantId || body.skuCode || 'MOCK-SKU';
      const qty = body.quantity || 1;
      const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(variantId);

      // Look up the product/variant from mock products
      const product = MOCK_PRODUCTS.find(p =>
        p.variants?.some(v => v.variantCode === variantId || v.id === variantId)
      );
      const variant = product?.variants?.find(
        v => v.variantCode === variantId || v.id === variantId
      );
      const price = variant?.price ?? 999_000;
      const sellerId = product?.sellerId ?? 0;
      const variantCode = variant?.variantCode ?? variantId;
      const variantName = variant?.variantName ?? 'Mặc định';
      const productName = product?.name ?? 'Sản phẩm mới';
      const image = product?.images?.[0] ?? null;

      // Check if item already exists in MOCK_CART.items → increment quantity
      const existingItem = MOCK_CART.items.find(
        i => i.variantCode === variantId || i.variantId === variantId
      );
      if (existingItem) {
        existingItem.quantity += qty;
        existingItem.subtotal = existingItem.priceSnapshot * existingItem.quantity;
      } else {
        MOCK_CART.items.push({
          variantId: variant?.id ?? String(Date.now()),
          variantCode,
          variantName,
          productName,
          priceSnapshot: price,
          currentPrice: price,
          priceChanged: false,
          quantity: qty,
          stockAvailable: variant?.stockQuantity ?? 50,
          variantImageSnapshot: image,
          subtotal: price * qty,
          outOfStock: false,
          unavailable: false,
          insufficientStock: false,
          sellerId,
        });
      }

      // Rebuild groupedBySeller
      const grouped: Record<string, typeof MOCK_CART.items> = {};
      for (const item of MOCK_CART.items) {
        const key = String(item.sellerId ?? 0);
        if (!grouped[key]) grouped[key] = [];
        grouped[key].push(item);
      }
      MOCK_CART.groupedBySeller = grouped;

      // Recalculate totals
      MOCK_CART.totalItems = MOCK_CART.items.reduce((sum, i) => sum + i.quantity, 0);
      MOCK_CART.subtotal = MOCK_CART.items.reduce((sum, i) => sum + (i.subtotal ?? 0), 0);

      // Return the full updated cart (mirrors real backend behaviour)
      return { success: true, data: MOCK_CART, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/[\w-]+$/) && method === 'put') {
      await sleep(200 + Math.random() * 100);
      const variantId = url!.split('/').pop()!;
      const body = JSON.parse(data || '{}');
      const newQty = body.quantity;

      const item = MOCK_CART.items.find(i => i.variantId === variantId || i.variantCode === variantId);
      if (item) {
        item.quantity = newQty;
        item.subtotal = item.priceSnapshot * newQty;
        // Rebuild groupedBySeller and totals
        const grouped: Record<string, typeof MOCK_CART.items> = {};
        for (const it of MOCK_CART.items) {
          const key = String(it.sellerId ?? 0);
          if (!grouped[key]) grouped[key] = [];
          grouped[key].push(it);
        }
        MOCK_CART.groupedBySeller = grouped;
        MOCK_CART.totalItems = MOCK_CART.items.reduce((sum, i) => sum + i.quantity, 0);
        MOCK_CART.subtotal = MOCK_CART.items.reduce((sum, i) => sum + (i.subtotal ?? 0), 0);
      }
      return { success: true, data: MOCK_CART, timestamp: Date.now() };
    }
    if (url?.match(/^\/cart\/items\/[\w-]+$/) && method === 'delete') {
      await sleep(200 + Math.random() * 100);
      const variantId = url!.split('/').pop()!;
      const idx = MOCK_CART.items.findIndex(i => i.variantId === variantId || i.variantCode === variantId);
      if (idx >= 0) MOCK_CART.items.splice(idx, 1);

      // Rebuild groupedBySeller and totals
      const grouped: Record<string, typeof MOCK_CART.items> = {};
      for (const it of MOCK_CART.items) {
        const key = String(it.sellerId ?? 0);
        if (!grouped[key]) grouped[key] = [];
        grouped[key].push(it);
      }
      MOCK_CART.groupedBySeller = grouped;
      MOCK_CART.totalItems = MOCK_CART.items.reduce((sum, i) => sum + i.quantity, 0);
      MOCK_CART.subtotal = MOCK_CART.items.reduce((sum, i) => sum + (i.subtotal ?? 0), 0);

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
      mockPaymentStartedAt[poId] = Date.now(); // start mock payment timer
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
        const startedAt = mockPaymentStartedAt[parentId] || 0;
        const isPaid = Date.now() - startedAt > 3000;
        const orderStatus = isPaid ? 'PAID' : 'PENDING';
        return {
          success: true,
          data: {
            parentOrderId: parentId,
            orderCode: cd.orderCode,
            status: orderStatus,
            totalAmt: cd.totalAmount,
            finalAmt: cd.finalAmount,
            shippingAddress: MOCK_ADDRESSES[0],
            orders: cd.orders.map(o => ({
              ...o,
              sellerId: 1,
              sellerName: 'Shop Sony',
              status: orderStatus,
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
      // Return pending checkout data if exists — simulate transition to SUCCESS after delay
      if (checkoutOrderData[parentId]) {
        const startedAt = mockPaymentStartedAt[parentId] || 0;
        const elapsed = Date.now() - startedAt;
        // After 3 seconds, simulate payment success
        const isSuccess = elapsed > 3000;
        const remainingSeconds = isSuccess ? null : Math.max(1, Math.ceil((3000 - elapsed) / 1000));
        return {
          success: true,
          data: {
            transactionId: parentId,
            parentOrderId: parentId,
            amount: checkoutOrderData[parentId].finalAmount,
            method: 'stripe',
            status: isSuccess ? 'SUCCESS' : 'PENDING',
            stripePiId: `pi_mock_${parentId}`,
            applicationFee: Math.round(checkoutOrderData[parentId].finalAmount * 0.03),
            transRef: `TXN-MOCK-${parentId}`,
            paidAt: isSuccess ? new Date(Date.now() - 2000).toISOString() : null,
            remainingSeconds: remainingSeconds,
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
          clientSecret: `pi_mock_secret_${parentId}_${Date.now()}_test_mock_secret`,
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
          p.name.toLowerCase().includes(search.toLowerCase()) ||
          p.description?.toLowerCase().includes(search.toLowerCase())
        );
      }
      if (category) {
        filtered = filtered.filter(p => p.categoryName?.toLowerCase().includes(String(category).toLowerCase()));
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
        filtered = filtered.filter(p => (p.originalPrice ?? 0) > (p.price ?? 0));
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
            productId: p.id,
            name: p.name,
            sellerId: p.sellerId,
            sellerName: p.sellerName,
            categoryId: p.categoryName,
            categoryName: p.categoryName,
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

    if ((url === '/products' || url === '/search') && method === 'get') {
      await sleep(300 + Math.random() * 200);
      const search = params?.q || params?.search || '';
      const category = params?.category;
      let filtered = MOCK_PRODUCTS;
      if (search) filtered = filtered.filter(p => p.name.toLowerCase().includes(search.toLowerCase()) || p.description?.toLowerCase().includes(search.toLowerCase()));
      if (category) filtered = filtered.filter(p => p.categoryName === category);
      return {
        success: true,
        data: { content: filtered, totalElements: filtered.length, totalPages: 1, last: true },
        timestamp: Date.now(),
      };
    }
    const productMatch = url?.match(/^\/products\/([^/]+)$/);
    if (productMatch && method === 'get') {
      await sleep(200 + Math.random() * 100);
      const product = MOCK_PRODUCTS.find(p => p.id === productMatch[1]);
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
    if (url === '/stripe/onboarding/admin/sellers' && method === 'get') {
      await sleep(250 + Math.random() * 150);
      const accounts = [...MOCK_SELLER_STRIPE_ACCOUNTS];
      const summary = {
        totalSellers: accounts.length,
        completedSellers: accounts.filter(a => a.onboardingStatus === 'COMPLETE').length,
        pendingSellers: accounts.filter(a => a.onboardingStatus === 'PENDING').length,
        inProgressSellers: accounts.filter(a => a.onboardingStatus === 'IN_PROGRESS').length,
        suspendedSellers: accounts.filter(a => a.onboardingStatus === 'SUSPENDED').length,
      };
      return { success: true, data: { summary, accounts }, timestamp: Date.now() };
    }

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
        slug: body.slug || body.name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, ''),
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
        category.slug = body.slug || body.name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
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

    return null;
  },
];

// ─── isMockMode ──────────────────────────────────────────────────────────────

export function isMockMode(): boolean {
  return import.meta.env.VITE_BACKEND_MODE === 'mock' || !import.meta.env.VITE_API_URL;
}

export function isNetworkError(error: unknown): boolean {
  if (error instanceof Error) {
    const msg = error.message.toLowerCase();
    return (
      error.name === 'NetworkError' ||
      msg.includes('network') ||
      msg.includes('failed to fetch') ||
      msg.includes('econnrefused') ||
      msg.includes('err_connection') ||
      msg.includes('net::err') ||
      msg.includes('timeout') ||
      (error as any).code === 'ECONNREFUSED' ||
      (error as any).code === 'ERR_NETWORK' ||
      (error as any).code === 'ETIMEDOUT' ||
      (error as any).code === 'ERR_CANCELED'
    );
  }
  return false;
}

export function shouldUseMock(error: unknown): boolean {
  if (isMockMode()) return true;
  return isNetworkError(error);
}

// ─── Install mock interceptor ─────────────────────────────────────────────────

export function installMockInterceptor(apiClient: AxiosInstance) {
  if (!isMockMode()) return;

  apiClient.interceptors.request.use(async (config) => {
    if (!isMockMode()) return config;

    const normalizedConfig = {
      method: config.method?.toLowerCase() || 'get',
      url: config.url?.replace(config.baseURL || '', '') || '',
      params: config.params,
      data: typeof config.data === 'object' && config.data !== null ? JSON.stringify(config.data) : config.data,
    };

    for (const handler of mockHandlers) {
      try {
        const result = await handler(normalizedConfig as InternalAxiosRequestConfig);
        if (result !== null) {
          // Create a mock response that axios can understand
          const mockResponse = {
            data: result,
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          };
          // Reject with a special object that the response interceptor can handle
          throw mockResponse;
        }
      } catch (err: any) {
        // If it's our mock response, re-throw it as a handled mock
        if (err?.data?.timestamp) {
          const mockError = new Error('Mock response') as any;
          mockError.response = err;
          mockError.isMockResponse = true;
          mockError.config = config;
          return Promise.reject(mockError);
        }
        // Re-throw unknown errors
        if (!err?.isMockResponse) throw err;
      }
    }

    return config;
  });
}




