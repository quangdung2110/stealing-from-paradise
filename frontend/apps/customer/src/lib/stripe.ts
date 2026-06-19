import { loadStripe } from '@stripe/stripe-js/pure';
import type { Stripe } from '@stripe/stripe-js';

let stripePromise: Promise<Stripe | null>;
const advancedFraudSignals = import.meta.env.VITE_STRIPE_ADVANCED_FRAUD_SIGNALS;

if (advancedFraudSignals === 'false' || (advancedFraudSignals === undefined && import.meta.env.DEV)) {
  loadStripe.setLoadParameters({ advancedFraudSignals: false });
}

export function getStripe(): Promise<Stripe | null> {
  if (!stripePromise) {
    stripePromise = loadStripe(
      import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string
    );
  }
  return stripePromise;
}

