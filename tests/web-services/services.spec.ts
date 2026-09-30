import { expect, test } from "@playwright/test";
import { epcAuthorization, requestJson, requireApiKey, services } from "./support";

// Independent fixtures: a failed postcode or address lookup cannot block other services.
const postcode = "YO42 1TT";
const uprn = "200000618356";
const latitude = "53.959";
const longitude = "-1.082";

function objectResponse(value: unknown, service: string): Record<string, unknown> {
  expect(
    value !== null && typeof value === "object" && !Array.isArray(value),
    `${service}: expected a JSON object from the API`,
  ).toBe(true);
  return value as Record<string, unknown>;
}

test("Postcodes.io - postcode lookup returns coordinates", async () => {
  const service = services.postcodes;
  const body = objectResponse(
    await requestJson(service, `https://api.postcodes.io/postcodes/${encodeURIComponent(postcode)}`),
    service.name,
  );
  expect(body.status, `${service.name}: API status`).toBe(200);
  const result = objectResponse(body.result, service.name);
  expect(result.postcode, `${service.name}: postcode`).toBe(postcode);
  expect(typeof result.latitude, `${service.name}: latitude`).toBe("number");
  expect(typeof result.longitude, `${service.name}: longitude`).toBe("number");
});

test("OS Places - API key authorizes postcode address lookup", async () => {
  const service = services.osPlaces;
  const key = requireApiKey(service);
  const url = new URL("https://api.os.uk/search/places/v1/postcode");
  url.search = new URLSearchParams({ postcode: postcode.replace(/\s/g, ""), key }).toString();
  const body = objectResponse(await requestJson(service, url), service.name);
  expect(Array.isArray(body.results), `${service.name}: expected results array`).toBe(true);
  const results = body.results as unknown[];
  expect(results.length, `${service.name}: expected addresses for ${postcode}`).toBeGreaterThan(0);
  const address = objectResponse(objectResponse(results[0], service.name).DPA, service.name);
  expect(typeof address.UPRN, `${service.name}: expected address UPRN`).toBe("string");
  expect(typeof address.ADDRESS, `${service.name}: expected address text`).toBe("string");
});

test("EPC - bearer token authorizes domestic search and certificate details", async () => {
  const service = services.epc;
  const key = requireApiKey(service);
  const headers = { Authorization: epcAuthorization(key) };
  const url = new URL("https://api.get-energy-performance-data.communities.gov.uk/api/domestic/search");
  url.search = new URLSearchParams({ uprn, page_size: "1" }).toString();
  const body = objectResponse(
    await requestJson(service, url, { headers }),
    service.name,
  );
  // A property may have no certificates; the response must still use the API envelope.
  expect(Array.isArray(body.data), `${service.name}: expected certificate data array`).toBe(true);
  objectResponse(body.pagination, `${service.name} search pagination`);
  const certificates = body.data as unknown[];
  if (certificates.length === 0) return;

  const summary = objectResponse(certificates[0], `${service.name} search result`);
  expect(typeof summary.certificateNumber, `${service.name}: certificate number`).toBe("string");
  expect((summary.certificateNumber as string).length, `${service.name}: nonempty certificate number`).toBeGreaterThan(0);
  expect(String(summary.uprn), `${service.name}: matching property UPRN`).toBe(uprn);
  expect(typeof summary.registrationDate, `${service.name}: registration date`).toBe("string");
  expect(summary.currentEnergyEfficiencyBand, `${service.name}: current energy band`).toMatch(/^[A-G]$/i);

  const detailUrl = new URL("https://api.get-energy-performance-data.communities.gov.uk/api/certificate");
  detailUrl.search = new URLSearchParams({ certificate_number: summary.certificateNumber as string }).toString();
  const detail = objectResponse(
    objectResponse(await requestJson(service, detailUrl, { headers }), service.name).data,
    `${service.name} certificate details`,
  );
  expect(detail.current_energy_efficiency_band, `${service.name}: certificate current energy band`).toMatch(/^[A-G]$/i);
  expect(detail.potential_energy_efficiency_band, `${service.name}: certificate potential energy band`).toMatch(/^[A-G]$/i);
  for (const field of ["energy_rating_current", "energy_rating_potential", "total_floor_area"]) {
    expect(typeof detail[field], `${service.name}: numeric ${field}`).toBe("number");
    expect(Number.isFinite(detail[field]), `${service.name}: finite ${field}`).toBe(true);
  }
});

test("Environment Agency - flood monitoring returns alerts", async () => {
  const service = services.flood;
  const url = new URL("https://environment.data.gov.uk/flood-monitoring/id/floods");
  url.search = new URLSearchParams({ lat: latitude, long: longitude, dist: "5", "min-severity": "3" }).toString();
  const body = objectResponse(await requestJson(service, url), service.name);
  // No active warnings is a successful response.
  expect(Array.isArray(body.items), `${service.name}: expected flood items array`).toBe(true);
});

test("Police UK - latest reporting month and street crime lookup work", async () => {
  const service = services.police;
  const latest = objectResponse(
    await requestJson(service, "https://data.police.uk/api/crime-last-updated"),
    service.name,
  );
  expect(
    typeof latest.date === "string" && /^\d{4}-(0[1-9]|1[0-2])-\d{2}$/.test(latest.date),
    `${service.name}: expected latest reporting date in YYYY-MM-DD format`,
  ).toBe(true);
  const url = new URL("https://data.police.uk/api/crimes-street/all-crime");
  url.search = new URLSearchParams({ lat: latitude, lng: longitude, date: (latest.date as string).slice(0, 7) }).toString();
  const crimes = await requestJson(service, url);
  expect(Array.isArray(crimes), `${service.name}: expected street crime array (which may be empty)`).toBe(true);
});

test("HM Land Registry - price paid SPARQL query works", async () => {
  const service = services.landRegistry;
  const query = `
    PREFIX lrppi: <http://landregistry.data.gov.uk/def/ppi/>
    PREFIX lrcommon: <http://landregistry.data.gov.uk/def/common/>
    SELECT ?amount ?date WHERE {
      ?transaction lrppi:pricePaid ?amount ;
                   lrppi:transactionDate ?date ;
                   lrppi:propertyAddress ?address .
      ?address lrcommon:postcode "${postcode}" .
    }
    LIMIT 1`;
  const body = objectResponse(
    await requestJson(service, "https://landregistry.data.gov.uk/landregistry/query", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ query }).toString(),
    }),
    service.name,
  );
  const results = objectResponse(body.results, service.name);
  expect(Array.isArray(results.bindings), `${service.name}: expected SPARQL bindings array (which may be empty)`).toBe(true);
});

test("Stripe - secret key authorizes reading Checkout Sessions", async () => {
  const service = services.stripe;
  const key = requireApiKey(service);
  // Read-only: this does not create a checkout, charge a card or change account data.
  const body = objectResponse(
    await requestJson(service, "https://api.stripe.com/v1/checkout/sessions?limit=1", {
      headers: { Authorization: `Bearer ${key}` },
    }),
    service.name,
  );
  expect(body.object === "list", `${service.name}: expected a list response`).toBe(true);
  expect(Array.isArray(body.data), `${service.name}: expected Checkout Sessions array`).toBe(true);
});
