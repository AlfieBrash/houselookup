import { expect, test } from "@playwright/test";
import { epcAuthorization, requestJson, requireApiKey, services } from "./support";

const endpoint = "https://example.invalid/api";
const keyedServices = [
  [services.osPlaces, "APP_OS_PLACES_API_KEY", "https://osdatahub.os.uk/projects"],
  [services.epc, "APP_EPC_API_KEY", "https://get-energy-performance-data.communities.gov.uk/guidance/energy-certificate-data-apis"],
  [services.stripe, "STRIPE_SECRET_KEY", "https://dashboard.stripe.com/apikeys"],
] as const;
const publicServices = [services.postcodes, services.flood, services.police, services.landRegistry];

function respond(status: number, body = "{}"): typeof fetch {
  return async () => new Response(body, { status });
}

async function failureMessage(result: Promise<unknown>): Promise<string> {
  return result.then(
    () => { throw new Error("Expected the request to fail"); },
    (error: Error) => error.message,
  );
}

for (const [service, credential, helpUrl] of keyedServices) {
  for (const status of [401, 403]) {
    test(`${service.name}: HTTP ${status} identifies credentials and renewal URL`, async () => {
      const message = await failureMessage(requestJson(service, endpoint, {}, respond(status)));
      expect(message).toContain(`${service.name}: authorization failed (HTTP ${status})`);
      expect(message).toContain(credential);
      expect(message).toContain(helpUrl);
      if (service === services.epc) expect(message).toContain("bearer token from My account");
    });
  }

  for (const [label, value] of [["missing", undefined], ["whitespace", " \t\n "]] as const) {
    test(`${service.name}: ${label} credentials give actionable setup instructions`, () => {
      const readKey = () => requireApiKey(service, { [credential]: value });
      expect(readKey).toThrow(`missing ${credential}`);
      expect(readKey).toThrow(helpUrl);
    });
  }
}

for (const service of publicServices) {
  for (const status of [401, 403]) {
    test(`${service.name}: HTTP ${status} explains that no API key is needed`, async () => {
      const message = await failureMessage(requestJson(service, endpoint, {}, respond(status)));
      expect(message).toContain(`authorization failed (HTTP ${status})`);
      expect(message).toContain("no API key is required");
      expect(message).toContain(service.helpUrl);
      expect(message).not.toContain("replacement credentials");
    });
  }
}

test("EPC uses the raw bearer token and trims surrounding whitespace", () => {
  expect(epcAuthorization("test-token")).toBe("Bearer test-token");
  expect(epcAuthorization(" \t test-token\r\n ")).toBe("Bearer test-token");
});

test("EPC ignores legacy username configuration", () => {
  const previous = process.env.APP_EPC_USERNAME;
  process.env.APP_EPC_USERNAME = "legacy@example.com";
  try {
    expect(epcAuthorization("test-token")).toBe("Bearer test-token");
  } finally {
    if (previous === undefined) delete process.env.APP_EPC_USERNAME;
    else process.env.APP_EPC_USERNAME = previous;
  }
});

for (const status of [429, 500]) {
  test(`HTTP ${status} is not misdiagnosed as an authorization failure`, async () => {
    const message = await failureMessage(requestJson(services.stripe, endpoint, {}, respond(status)));
    expect(message).toContain(`returned HTTP ${status}`);
    expect(message).toContain(services.stripe.helpUrl);
    expect(message).not.toMatch(/authorization failed|replacement credentials|rotate/i);
  });
}

for (const status of [301, 302, 307, 308]) {
  test(`EPC HTTP ${status} fails with endpoint and account guidance`, async () => {
    const message = await failureMessage(requestJson(services.epc, endpoint, {}, respond(status)));
    expect(message).toContain(`API redirected (HTTP ${status})`);
    expect(message).toContain("Check whether the endpoint has moved or requires sign-in");
    expect(message).toContain(services.epc.helpUrl);
  });
}

for (const [label, body] of [["HTML sign-in page", "<html>Sign in</html>"], ["invalid JSON", "{broken"], ["empty body", ""]]) {
  test(`${label} is rejected as an invalid API response`, async () => {
    await expect(requestJson(services.epc, endpoint, {}, respond(200, body)))
      .rejects.toThrow("expected a JSON API response (HTTP 200)");
  });
}

test("JSON requests preserve authentication, refuse redirects and have a timeout signal", async () => {
  let calls = 0;
  const fakeFetch: typeof fetch = async (url, init) => {
    calls++;
    expect(url).toBe(endpoint);
    expect(init?.redirect).toBe("manual");
    expect(init?.signal).toBeInstanceOf(AbortSignal);
    expect(init?.signal?.aborted).toBe(false);
    expect(new Headers(init?.headers).get("Accept")).toBe("application/json");
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer test-token");
    expect(new Headers(init?.headers).get("Connection")).toBe("close");
    return Response.json({ results: [{ id: "example" }] });
  };
  const result = await requestJson(services.stripe, endpoint, {
    headers: { Authorization: "Bearer test-token" },
  }, fakeFetch);
  expect(result).toEqual({ results: [{ id: "example" }] });
  expect(calls).toBe(1);
});

test("network failures omit the original error, request URL, credentials and body", async () => {
  const secrets = ["query-secret", "header-secret", "body-secret", "underlying-error-secret"];
  const url = `${endpoint}?key=${secrets[0]}`;
  const fakeFetch: typeof fetch = async () => {
    throw new Error(`${url}; Authorization: ${secrets[1]}; body: ${secrets[2]}; ${secrets[3]}`);
  };
  const message = await failureMessage(requestJson(services.osPlaces, url, {
    headers: { Authorization: secrets[1] }, body: secrets[2], method: "POST",
  }, fakeFetch));
  expect(message).toContain("Ordnance Survey Places: network/TLS request failed");
  expect(message).toContain(services.osPlaces.helpUrl);
  expect(message).not.toContain(endpoint);
  for (const secret of secrets) expect(message).not.toContain(secret);
});

test("authorization errors discard provider response bodies that may contain credentials", async () => {
  const body = "provider echoed query-secret and header-secret";
  const message = await failureMessage(requestJson(services.osPlaces, endpoint, {}, respond(401, body)));
  expect(message).toContain("authorization failed");
  expect(message).not.toContain(body);
});

test("HTTP status diagnostics survive response stream cleanup failures", async () => {
  const fakeFetch: typeof fetch = async () => new Response(new ReadableStream({
    cancel() { throw new Error("stream-error-secret"); },
  }), { status: 403 });
  const message = await failureMessage(requestJson(services.osPlaces, endpoint, {}, fakeFetch));
  expect(message).toContain("authorization failed (HTTP 403)");
  expect(message).toContain(services.osPlaces.helpUrl);
  expect(message).not.toContain("stream-error-secret");
});
