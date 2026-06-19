import { MOCK_ADDRESSES } from './data/addresses';

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

export { checkoutOrderData };
