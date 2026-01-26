import { DataOptions } from "@/components/DataOptionsSelector";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface ReportParams {
  uprn: string;
  postcode: string;
  paon?: string;
  options: DataOptions;
}

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "Failed to generate report. Please try again.";
};

export async function downloadPropertyReport(params: ReportParams): Promise<Blob> {
  const { uprn, postcode, paon, options } = params;

  const url = new URL(`${BACKEND_URL}/api/report/pdf`);
  url.searchParams.set("uprn", uprn);
  url.searchParams.set("postcode", postcode);
  if (paon) {
    url.searchParams.set("paon", paon);
  }
  url.searchParams.set("includeEpc", String(options.epc));
  url.searchParams.set("includePriceHistory", String(options.priceHistory));

  let response: Response;
  try {
    response = await fetch(url.toString());
  } catch (error) {
    console.error("Report download network error.", { params, error });
    throw error;
  }

  if (response.status === 404) {
    throw new Error("No data found for this property");
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.blob();
}
