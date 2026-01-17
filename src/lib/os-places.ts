import type { Address } from "@/components/AddressSelector";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "Failed to lookup addresses. Please try again.";
};

export async function lookupAddressesByPostcode(postcode: string): Promise<Address[]> {
  const trimmed = postcode.trim();
  const response = await fetch(
    `${BACKEND_URL}/api/addresses?postcode=${encodeURIComponent(trimmed)}`
  );

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.json();
}
