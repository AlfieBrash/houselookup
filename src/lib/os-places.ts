import type { Address } from "@/components/AddressSelector";

const OS_PLACES_ENDPOINT = "https://api.os.uk/search/places/v1/postcode";

interface OsPlacesDpa {
  UPRN?: string;
  POST_TOWN?: string;
  POSTCODE?: string;
  ORGANISATION_NAME?: string;
  SUB_BUILDING_NAME?: string;
  BUILDING_NAME?: string;
  BUILDING_NUMBER?: string;
  DEPENDENT_THOROUGHFARE_NAME?: string;
  THOROUGHFARE_NAME?: string;
  DOUBLE_DEPENDENT_LOCALITY?: string;
  DEPENDENT_LOCALITY?: string;
  ADDRESS?: string;
}

interface OsPlacesResult {
  DPA?: OsPlacesDpa;
}

interface OsPlacesResponse {
  results?: OsPlacesResult[];
  error?: string;
}

const normaliseParts = (parts: Array<string | undefined>) =>
  parts
    .map((part) => (part || "").trim())
    .filter(Boolean)
    .join(" ")
    .replace(/\s+/g, " ");

const buildAddress = (dpa: OsPlacesDpa, fallbackPostcode: string): Address | null => {
  const street = normaliseParts([dpa.DEPENDENT_THOROUGHFARE_NAME, dpa.THOROUGHFARE_NAME]);
  const line1 = normaliseParts([
    dpa.ORGANISATION_NAME,
    dpa.SUB_BUILDING_NAME,
    dpa.BUILDING_NAME,
    dpa.BUILDING_NUMBER,
    street || undefined,
  ]);

  const line2 = [dpa.DOUBLE_DEPENDENT_LOCALITY, dpa.DEPENDENT_LOCALITY]
    .map((part) => (part || "").trim())
    .filter(Boolean)
    .join(", ");

  const fallbackLine1 = dpa.ADDRESS?.split(",")[0]?.trim();
  const resolvedLine1 = line1 || fallbackLine1 || "";
  const town = dpa.POST_TOWN?.trim() || "";
  const postcode = dpa.POSTCODE?.trim() || fallbackPostcode;
  const uprn = dpa.UPRN?.trim() || "";

  if (!resolvedLine1 || !town || !postcode || !uprn) return null;

  return {
    uprn,
    line1: resolvedLine1,
    line2: line2 || undefined,
    town,
    postcode,
  };
};

export async function lookupAddressesByPostcode(postcode: string): Promise<Address[]> {
  const apiKey = import.meta.env.VITE_OS_PLACES_API_KEY as string | undefined;

  if (!apiKey) {
    throw new Error("OS Places API key missing. Set VITE_OS_PLACES_API_KEY to enable lookup.");
  }

  const sanitised = encodeURIComponent(postcode.trim());
  const response = await fetch(`${OS_PLACES_ENDPOINT}?postcode=${sanitised}&key=${apiKey}`);

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("OS Places API key is invalid or lacks access.");
    }
    throw new Error("Failed to lookup addresses. Please try again.");
  }

  const data: OsPlacesResponse = await response.json();

  if (!data.results || data.results.length === 0) {
    return [];
  }

  return data.results
    .map((result) => (result?.DPA ? buildAddress(result.DPA, postcode) : null))
    .filter((address): address is Address => Boolean(address));
}
