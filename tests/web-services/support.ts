export interface WebService {
  name: string;
  helpUrl: string;
  credential?: string;
  authHelp?: string;
}

export const services = {
  osPlaces: {
    name: "Ordnance Survey Places",
    helpUrl: "https://osdatahub.os.uk/projects",
    credential: "APP_OS_PLACES_API_KEY",
    authHelp: "Create or regenerate a project API key and enable the OS Places API on that project.",
  },
  epc: {
    name: "Energy Performance Certificate data",
    helpUrl: "https://get-energy-performance-data.communities.gov.uk/guidance/energy-certificate-data-apis",
    credential: "APP_EPC_API_KEY",
    authHelp: "Get a bearer token from My account on the EPC service and save the token alone in APP_EPC_API_KEY.",
  },
  stripe: {
    name: "Stripe",
    helpUrl: "https://dashboard.stripe.com/apikeys",
    credential: "STRIPE_SECRET_KEY",
    authHelp: "Create or rotate a secret API key for the correct account and mode, with permission to read Checkout Sessions.",
  },
  postcodes: { name: "Postcodes.io", helpUrl: "https://postcodes.io/docs/" },
  flood: {
    name: "Environment Agency flood monitoring",
    helpUrl: "https://environment.data.gov.uk/flood-monitoring/doc/reference",
  },
  police: { name: "Police UK crime data", helpUrl: "https://data.police.uk/docs/" },
  landRegistry: { name: "HM Land Registry", helpUrl: "https://landregistry.data.gov.uk/" },
} satisfies Record<string, WebService>;

function accessHelp(service: WebService): string {
  return service.credential
    ? `Get replacement credentials at ${service.helpUrl} and update ${service.credential}. ${service.authHelp ?? ""}`
    : `This is a public API; no API key is required. Check provider access/status at ${service.helpUrl}.`;
}

export function requireApiKey(service: WebService, env: NodeJS.ProcessEnv = process.env): string {
  const value = service.credential ? env[service.credential] : undefined;
  if (!value?.trim()) {
    throw new Error(`${service.name}: missing ${service.credential}. ${accessHelp(service)}`);
  }
  return value;
}

// Match the backend: APP_EPC_API_KEY contains the raw bearer token.
export function epcAuthorization(apiKey: string): string {
  return `Bearer ${apiKey.trim()}`;
}

export async function requestJson(
  service: WebService,
  url: string | URL,
  init: RequestInit = {},
  fetcher: typeof fetch = fetch,
): Promise<unknown> {
  const signal = AbortSignal.timeout(20_000);
  const headers = new Headers(init.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  // Workers restart after a failed test; don't leave pooled sockets open at exit.
  headers.set("Connection", "close");
  let response: Response;
  try {
    response = await fetcher(url, {
      ...init,
      headers,
      redirect: "manual",
      signal,
    });
  } catch {
    // Fetch errors can contain URLs, query-string keys or authorization headers.
    throw new Error(`${service.name}: ${signal.aborted ? "request timed out after 20 seconds" : "network/TLS request failed"}. Check connectivity and provider availability at ${service.helpUrl}.`);
  }

  if (!response.ok) {
    // A broken response stream must not hide the HTTP status or expose raw errors.
    await response.body?.cancel().catch(() => undefined);
    if (response.status === 401 || response.status === 403) {
      throw new Error(`${service.name}: authorization failed (HTTP ${response.status}). ${accessHelp(service)}`);
    }
    if (response.status >= 300 && response.status < 400) {
      throw new Error(`${service.name}: API redirected (HTTP ${response.status}) instead of returning data. Check whether the endpoint has moved or requires sign-in. ${accessHelp(service)}`);
    }
    throw new Error(`${service.name}: returned HTTP ${response.status}. Check the request, rate limits and provider availability at ${service.helpUrl}.`);
  }

  try {
    return await response.json();
  } catch {
    throw new Error(`${service.name}: ${signal.aborted ? "response timed out after 20 seconds" : "expected a JSON API response"} (HTTP ${response.status}). Check the API endpoint at ${service.helpUrl}.`);
  }
}
