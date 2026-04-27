import { DataOptions } from "@/components/DataOptionsSelector";

const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface ReportInputs {
  uprn: string;
  postcode: string;
  paon?: string;
  latitude?: number;
  longitude?: number;
  includeEpc: boolean;
  includePriceHistory: boolean;
  includeFloodRisk: boolean;
  includeCrimeStats: boolean;
}

export interface ReportParams {
  uprn: string;
  postcode: string;
  paon?: string;
  latitude?: number;
  longitude?: number;
  options: DataOptions;
}

export interface ReportPreviewResponse {
  requested: {
    uprn: string;
    postcode: string;
    paon?: string;
    includeEpc: boolean;
    includePriceHistory: boolean;
    includeFloodRisk: boolean;
    includeCrimeStats: boolean;
    latitude?: number;
    longitude?: number;
  };
  epcAvailable: boolean;
  priceHistoryAvailable: boolean;
  floodRiskAvailable: boolean;
  crimeStatsAvailable: boolean;
  availableSectionCount: number;
  summary: string;
}

export interface ReportPrepareResponse {
  downloadToken: string;
  downloadUrl: string;
}

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "The report generator had a temporary issue. Please try again shortly.";
};

const toParams = (params: ReportParams, path = "/api/report/preview") => {
  const options: DataOptions = params.options;
  const url = new URL(`${BACKEND_URL}${path}`);
  url.searchParams.set("uprn", params.uprn);
  url.searchParams.set("postcode", params.postcode);
  if (params.paon) {
    url.searchParams.set("paon", params.paon);
  }
  url.searchParams.set("includeEpc", String(options.epc));
  url.searchParams.set("includePriceHistory", String(options.priceHistory));
  url.searchParams.set("includeFloodRisk", String(options.floodRisk));
  url.searchParams.set("includeCrimeStats", String(options.crimeStats));
  if (typeof params.latitude === "number") {
    url.searchParams.set("latitude", String(params.latitude));
  }
  if (typeof params.longitude === "number") {
    url.searchParams.set("longitude", String(params.longitude));
  }
  return url;
};

export async function previewReport(params: ReportParams): Promise<ReportPreviewResponse> {
  const response = await fetch(toParams(params).toString(), {
    credentials: "include",
  });

  if (response.status === 404) {
    throw new Error("This property has no accessible report data yet.");
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.json();
}

export async function prepareReportDownload(params: ReportParams): Promise<ReportPrepareResponse> {
  const form = new URLSearchParams();
  form.set("uprn", params.uprn);
  form.set("postcode", params.postcode);
  if (params.paon) {
    form.set("paon", params.paon);
  }
  form.set("includeEpc", String(params.options.epc));
  form.set("includePriceHistory", String(params.options.priceHistory));
  form.set("includeFloodRisk", String(params.options.floodRisk));
  form.set("includeCrimeStats", String(params.options.crimeStats));
  if (typeof params.latitude === "number") {
    form.set("latitude", String(params.latitude));
  }
  if (typeof params.longitude === "number") {
    form.set("longitude", String(params.longitude));
  }

  const response = await fetch(`${BACKEND_URL}/api/report/prepare`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: form.toString(),
  });

  if (response.status === 401) {
    throw new Error("Sign in required before downloading the report.");
  }
  if (response.status === 402) {
    throw new Error("You need more credits. Buy a pack to download this report.");
  }
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.json();
}

export async function downloadReportByToken(token: string): Promise<Blob> {
  const response = await fetch(`${BACKEND_URL}/api/report/pdf?token=${encodeURIComponent(token)}`, {
    credentials: "include",
  });

  if (response.status === 404) {
    throw new Error("No data found for this property.");
  }
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.blob();
}

export async function downloadPropertyReportLegacy(params: ReportParams): Promise<Blob> {
  const url = toParams(params);
  url.pathname = `${url.pathname.replace(/\/preview$/, "/pdf")}`;

  const response = await fetch(url.toString(), {
    credentials: "include",
  });

  if (response.status === 404) {
    throw new Error("This property appears to be keeping its secrets. No data found, we're afraid.");
  }
  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }
  return response.blob();
}
