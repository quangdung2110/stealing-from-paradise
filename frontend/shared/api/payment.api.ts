import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Payment Endpoints (payment-service) ───────────────────────────────────

export interface PaymentDetail {
  transactionId: number;
  parentOrderId: number;
  amount: number;
  method: string;
  status: string;
  stripePiId: string;
  applicationFee: number;
  transRef: string;
  paidAt: string | null;
  remainingSeconds: number | null;
}

export interface ClientSecretResponse {
  clientSecret: string;
  parentOrderId: number;
  transactionId: number;
  amount: number;
  currency: string;
}

export const paymentApi = {
  /** Get payment/transaction details for a parent order */
  getPayment: (parentOrderId: number) =>
    apiClient.get<ApiResponse<PaymentDetail>>(`/payments/parent-order/${parentOrderId}`),

  /** Get Stripe client secret for PaymentElement (backend fetches from existing PI) */
  getClientSecret: (parentOrderId: number) =>
    apiClient.get<ApiResponse<ClientSecretResponse | null>>(`/payments/client-secret/${parentOrderId}`),
};
