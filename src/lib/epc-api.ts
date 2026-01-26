const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface EpcCertificate {
  address: string;
  address1: string;
  address2: string;
  address3: string;
  postcode: string;
  uprn: string;
  "building-reference-number": string;
  "current-energy-rating": string;
  "potential-energy-rating": string;
  "current-energy-efficiency": number;
  "potential-energy-efficiency": number;
  "property-type": string;
  "built-form": string;
  "total-floor-area": number;
  "main-fuel": string;
  "lodgement-date": string;
  "inspection-date": string;
  "transaction-type": string;
  "environment-impact-current": number;
  "environment-impact-potential": number;
  "energy-consumption-current": number;
  "energy-consumption-potential": number;
  "co2-emissions-current": number;
  "co2-emissions-potential": number;
  "co2-emiss-curr-per-floor-area": number;
  "lighting-cost-current": number;
  "lighting-cost-potential": number;
  "heating-cost-current": number;
  "heating-cost-potential": number;
  "hot-water-cost-current": number;
  "hot-water-cost-potential": number;
  "windows-description": string;
  "walls-description": string;
  "roof-description": string;
  "floor-description": string;
  "main-heating-controls": string;
  "main-heat-description": string;
  "hot-water-description": string;
  "lighting-description": string;
  "tenure": string;
  "construction-age-band": string;
}

const parseErrorMessage = async (response: Response) => {
  const text = await response.text();
  return text || "Failed to fetch EPC data. Please try again.";
};

export async function fetchEpcByUprn(uprn: string): Promise<EpcCertificate | null> {
  let response: Response;
  try {
    response = await fetch(`${BACKEND_URL}/api/epc?uprn=${encodeURIComponent(uprn)}`);
  } catch (error) {
    console.error("EPC lookup network error.", { uprn, error });
    throw error;
  }

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.json();
}

export async function downloadEpcPdf(uprn: string): Promise<Blob> {
  let response: Response;
  try {
    response = await fetch(`${BACKEND_URL}/api/epc/pdf?uprn=${encodeURIComponent(uprn)}`);
  } catch (error) {
    console.error("EPC PDF download network error.", { uprn, error });
    throw error;
  }

  if (response.status === 404) {
    throw new Error("No EPC found for this property");
  }

  if (!response.ok) {
    throw new Error(await parseErrorMessage(response));
  }

  return response.blob();
}
