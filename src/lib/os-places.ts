import type { Address } from "@/components/AddressSelector";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export const OS_PLACES_STUB_POSTCODE = "YO42 1TT";

const STUB_ADDRESSES: Address[] = [
  {
    uprn: "200000618356",
    line1: "1 New Cottages",
    line2: "Great Givendale",
    town: "York",
    postcode: OS_PLACES_STUB_POSTCODE,
  },
  {
    uprn: "200000618357",
    line1: "2 New Cottages",
    line2: "Great Givendale",
    town: "York",
    postcode: OS_PLACES_STUB_POSTCODE,
  },
  {
    uprn: "200000644141",
    line1: "Givendale House",
    line2: "Givendale Lane",
    town: "York",
    postcode: OS_PLACES_STUB_POSTCODE,
  },
];

interface AddressLookupOptions {
  useStub?: boolean;
}

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "The address service seems to have wandered off. Terribly sorry - do give it another go.";
};

const normalisePostcode = (postcode: string) => postcode.replace(/\s+/g, "").toUpperCase();

export async function lookupAddressesByPostcode(
  postcode: string,
  options: AddressLookupOptions = {}
): Promise<Address[]> {
  const trimmed = postcode.trim();

  if (options.useStub) {
    if (normalisePostcode(trimmed) !== normalisePostcode(OS_PLACES_STUB_POSTCODE)) {
      throw new Error(
        `OS Places stub mode is enabled. Search for ${OS_PLACES_STUB_POSTCODE} to use the local address list.`
      );
    }

    return STUB_ADDRESSES.map((address) => ({ ...address }));
  }

  const response = await fetch(
    `${BACKEND_URL}/api/addresses?postcode=${encodeURIComponent(trimmed)}`
  );

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.json();
}
