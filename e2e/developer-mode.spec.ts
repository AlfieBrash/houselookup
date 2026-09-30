import { expect, test } from "@playwright/test";

const stubPostcode = "YO42 1TT";
const savedAddress = {
  uprn: "200000618356",
  displayAddress: "1 New Cottages, Great Givendale, York",
};

const reportSections = [
  {
    testId: "report-data-epc",
    label: "Environmental Performance",
    availableKey: "epcAvailable",
  },
  {
    testId: "report-data-priceHistory",
    label: "Historical Prices",
    availableKey: "priceHistoryAvailable",
  },
  {
    testId: "report-data-floodRisk",
    label: "Flood Risk Assessment",
    availableKey: "floodRiskAvailable",
  },
  {
    testId: "report-data-crimeStats",
    label: "Crime Statistics",
    availableKey: "crimeStatsAvailable",
  },
] as const;

function isPreviewRequest(url: string) {
  return new URL(url).pathname === "/api/report/preview";
}

test("developer mode searches a saved address and returns data availability for every report section", async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto("/");
  await page.getByRole("button", { name: "Enable OS Places stub mode" }).click();
  await expect(page.getByRole("button", { name: "Disable OS Places stub mode" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );

  await page.getByLabel("Postcode").fill(stubPostcode);
  await page.getByRole("button", { name: "Search" }).click();

  await expect(page.getByText("Postcode details")).toBeVisible();
  await expect(page.getByText(`Addresses at ${stubPostcode}`)).toBeVisible();
  await expect(page.getByText(savedAddress.displayAddress, { exact: true })).toBeVisible();

  const previewFailurePromise = new Promise<string>((resolve) => {
    page.on("requestfailed", (request) => {
      if (isPreviewRequest(request.url()) && request.method() === "GET") {
        resolve(`${request.url()} failed: ${request.failure()?.errorText ?? "unknown error"}`);
      }
    });
  });
  const previewResponsePromise = page.waitForResponse(
    (response) =>
      isPreviewRequest(response.url()) &&
      response.request().method() === "GET",
    { timeout: 90_000 },
  );

  await page.getByText(savedAddress.displayAddress, { exact: true }).click();
  await page.getByRole("button", { name: "Select address" }).click();
  const previewResult = await Promise.race([
    previewResponsePromise.then((response) => ({ type: "response" as const, response })),
    previewFailurePromise.then((message) => ({ type: "failure" as const, message })),
  ]);

  if (previewResult.type === "failure") {
    const errorText = await page
      .getByTestId("report-preview-error")
      .textContent({ timeout: 5_000 })
      .catch(() => null);
    throw new Error(
      `Expected /api/report/preview to return a response, but the request failed. ${previewResult.message}. UI error: ${
        errorText ?? "none"
      }`,
    );
  }

  const previewResponse = previewResult.response;
  const previewBody = await previewResponse.text();

  expect(
    previewResponse.ok(),
    `Expected report preview to succeed, got ${previewResponse.status()}: ${previewBody}`,
  ).toBe(true);
  const preview = JSON.parse(previewBody);

  await expect(page.getByText("Selected address")).toBeVisible();
  await expect(page.getByText(`UPRN: ${savedAddress.uprn}`)).toBeVisible();
  for (const section of reportSections) {
    await expect(page.getByTestId(section.testId)).toContainText(section.label);
    await expect(page.getByTestId(section.testId)).toContainText(/found|not found/);
    await expect(page.getByTestId(section.testId)).not.toContainText("checking...");
  }

  expect(preview.requested).toMatchObject({
    uprn: savedAddress.uprn,
    postcode: stubPostcode,
    includeEpc: true,
    includePriceHistory: true,
    includeFloodRisk: true,
    includeCrimeStats: true,
  });
  const availableSectionCount = reportSections.filter(
    (section) => preview[section.availableKey] === true,
  ).length;
  for (const section of reportSections) {
    expect(typeof preview[section.availableKey]).toBe("boolean");
  }
  expect(preview.availableSectionCount).toBe(availableSectionCount);
});
