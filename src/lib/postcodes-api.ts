// postcodes.io API integration - free, no API key required
// Documentation: https://postcodes.io/

export interface PostcodesApiResult {
  postcode: string;
  admin_district: string | null;
  region: string | null;
  country: string;
  parliamentary_constituency: string | null;
  latitude: number;
  longitude: number;
}

export interface PostcodeResponse {
  status: number;
  result: PostcodesApiResult | null;
  error?: string;
}

export async function lookupPostcode(postcode: string): Promise<PostcodesApiResult> {
  const sanitised = encodeURIComponent(postcode.trim());
  const response = await fetch(`https://api.postcodes.io/postcodes/${sanitised}`);
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error("Invalid postcode. Please check and try again.");
    }
    throw new Error("Failed to lookup postcode. Please try again.");
  }

  const data: PostcodeResponse = await response.json();
  
  if (data.status !== 200 || !data.result) {
    throw new Error(data.error || "Postcode not found");
  }

  return data.result;
}

export async function autocompletePostcode(partial: string): Promise<string[]> {
  if (partial.length < 2) return [];
  
  const sanitised = encodeURIComponent(partial.trim());
  const response = await fetch(`https://api.postcodes.io/postcodes/${sanitised}/autocomplete`);
  
  if (!response.ok) return [];
  
  const data = await response.json();
  return data.result || [];
}

export async function validatePostcode(postcode: string): Promise<boolean> {
  const sanitised = encodeURIComponent(postcode.trim());
  const response = await fetch(`https://api.postcodes.io/postcodes/${sanitised}/validate`);
  
  if (!response.ok) return false;
  
  const data = await response.json();
  return data.result === true;
}
