import { parseApiError } from "@/lib/api-error";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface CreditPack {
  credits: number;
  amountCents: number;
  currency: string;
}

export interface CheckoutResponse {
  sessionId: string;
  checkoutUrl: string;
  credits: number;
  amountCents: number;
}

export interface CreditBalanceResponse {
  credits: number;
}

const parseError = async (response: Response) => {
  return parseApiError(response, "Could not start checkout.");
};

export async function getPricing(): Promise<CreditPack[]> {
  const response = await fetch(`${BACKEND_URL}/api/payments/pricing`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

export async function createCheckout(credits: number): Promise<CheckoutResponse> {
  const response = await fetch(`${BACKEND_URL}/api/payments/checkout`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ credits }),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

export async function createDevTopup(credits: number): Promise<CreditBalanceResponse> {
  const response = await fetch(`${BACKEND_URL}/api/payments/dev-topup`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ credits }),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}
