import { DataOptions } from "@/components/DataOptionsSelector";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface ReportParams {
  uprn: string;
  postcode: string;
  paon?: string;
  latitude?: number;
  longitude?: number;
  options: DataOptions;
}

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "The report generator has gone on a tea break. Please try again shortly.";
};

export async function downloadPropertyReport(params: ReportParams): Promise<Blob> {
  const { uprn, postcode, paon, latitude, longitude, options } = params;

  const url = new URL(`${BACKEND_URL}/api/report/pdf`);
  url.searchParams.set("uprn", uprn);
  url.searchParams.set("postcode", postcode);
  if (paon) {
    url.searchParams.set("paon", paon);
  }
  url.searchParams.set("includeEpc", String(options.epc));
  url.searchParams.set("includePriceHistory", String(options.priceHistory));
  url.searchParams.set("includeFloodRisk", String(options.floodRisk));
  if (typeof latitude === "number") {
    url.searchParams.set("latitude", String(latitude));
  }
  if (typeof longitude === "number") {
    url.searchParams.set("longitude", String(longitude));
  }

  let response: Response;
  try {
    response = await fetch(url.toString());
  } catch (error) {
    console.error("Report download network error.", { params, error });
    throw error;
  }

  if (response.status === 404) {
    throw new Error("This property appears to be keeping its secrets. No data found, we're afraid.");
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.blob();
}
