// ─── Mock Mode ─────────────────────────────────────────────────────────────────
//
// Single entry point for the mock module. All consumers that need mock-mode
// detection or network-error classification should import from here.
//
// Directory structure:
//   mock/
//   ├── index.ts          ← You are here (public barrel)
//   ├── utils.ts          isMockMode, isNetworkError, shouldUseMock, sleep
//   ├── handlers.ts       All mock API route handlers (mockHandlers array)
//   ├── checkout.ts       Mutable checkout state (checkoutOrderData)
//   └── data/             Mock datasets (products, orders, cart, payments, etc.)
//
// The Axios mock interceptor is installed inline in lib/axios.ts — it imports
// mockHandlers from ./handlers and wires up the request interceptor at module
// init time.

export { isMockMode, isNetworkError, shouldUseMock } from './utils';
