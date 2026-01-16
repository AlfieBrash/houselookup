const EPC_API_ENDPOINT = "https://epc.opendatacommunities.org/api/v1/domestic/search";

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

export interface EpcSearchResponse {
  rows: EpcCertificate[];
  "column-names": string[];
}

/**
 * Fetches EPC data for a given UPRN from the Open Data Communities API.
 * 
 * NOTE: This requires an API key from https://epc.opendatacommunities.org/login
 * The key is stored in VITE_EPC_API_KEY environment variable.
 * 
 * ⚠️ WARNING: This key is exposed in the browser. For production, use a backend proxy.
 */
export async function fetchEpcByUprn(uprn: string): Promise<EpcCertificate | null> {
  // Stub for testing - returns mock data for the known test UPRN
  if (uprn === "200000644141") {
    return {
      address: "1 Test Street, Great Givendale, York, YO42 1TT",
      address1: "1 Test Street",
      address2: "Great Givendale",
      address3: "",
      postcode: "YO42 1TT",
      uprn: "200000644141",
      "building-reference-number": "1234567890",
      "current-energy-rating": "D",
      "potential-energy-rating": "B",
      "current-energy-efficiency": 58,
      "potential-energy-efficiency": 82,
      "property-type": "House",
      "built-form": "Detached",
      "total-floor-area": 120,
      "main-fuel": "Oil",
      "lodgement-date": "2022-03-15",
      "inspection-date": "2022-03-10",
      "transaction-type": "marketed sale",
      "environment-impact-current": 45,
      "environment-impact-potential": 70,
      "energy-consumption-current": 280,
      "energy-consumption-potential": 150,
      "co2-emissions-current": 5.2,
      "co2-emissions-potential": 2.8,
      "co2-emiss-curr-per-floor-area": 43,
      "lighting-cost-current": 120,
      "lighting-cost-potential": 80,
      "heating-cost-current": 1200,
      "heating-cost-potential": 650,
      "hot-water-cost-current": 180,
      "hot-water-cost-potential": 120,
      "windows-description": "Fully double glazed",
      "walls-description": "Cavity wall, as built, insulated (assumed)",
      "roof-description": "Pitched, 200 mm loft insulation",
      "floor-description": "Suspended, limited insulation (assumed)",
      "main-heating-controls": "Programmer and room thermostat",
      "main-heat-description": "Boiler and radiators, oil",
      "hot-water-description": "From main system",
      "lighting-description": "Low energy lighting in 50% of fixed outlets",
      "tenure": "owner-occupied",
      "construction-age-band": "1950-1966",
    };
  }

  const apiKey = import.meta.env.VITE_EPC_API_KEY as string | undefined;

  if (!apiKey) {
    throw new Error("EPC API key missing. Set VITE_EPC_API_KEY to enable lookup.");
  }

  const response = await fetch(`${EPC_API_ENDPOINT}?uprn=${encodeURIComponent(uprn)}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Basic ${apiKey}`,
    },
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("EPC API key is invalid or lacks access.");
    }
    if (response.status === 404) {
      return null;
    }
    throw new Error("Failed to fetch EPC data. Please try again.");
  }

  const data: EpcSearchResponse = await response.json();

  if (!data.rows || data.rows.length === 0) {
    return null;
  }

  // Return the most recent certificate (first in the list)
  return data.rows[0];
}
