## Property Pal - Project Info

**URL**: https://propal.onrender.com/

## What does this app do?

Property Pal is for anybody looking to rent or buy a house in the UK. It provides a rapid sanity check on the important things about a property. The idea of this tool is to highlight the red flags long before the point of paying for a property report.  


## Web service checks

Run the independent live checks from the project root (Node.js 20+ and `npm install` required):

```sh
npm run test:web-services
```

Each provider has its own test and result. All seven run even if another fails; the suite needs internet access but no browser, frontend, backend or database. These are direct provider checks for API access and response shape; the existing `npm run test:e2e` still checks the app flow.

| Service | Credentials | Replacement keys / provider documentation |
| --- | --- | --- |
| Ordnance Survey Places | `APP_OS_PLACES_API_KEY` | [OS Data Hub projects](https://osdatahub.os.uk/projects); enable Places access on the project |
| Energy Performance Certificate data | `APP_EPC_API_KEY` (bearer token) | [Energy certificate API access](https://get-energy-performance-data.communities.gov.uk/guidance/energy-certificate-data-apis) |
| Stripe | `STRIPE_SECRET_KEY` | [Stripe API keys](https://dashboard.stripe.com/apikeys) |
| Postcodes.io | None | [Documentation](https://postcodes.io/docs/) |
| Environment Agency flood monitoring | None | [Documentation](https://environment.data.gov.uk/flood-monitoring/doc/reference) |
| Police UK crime data | None | [Documentation](https://data.police.uk/docs/) |
| HM Land Registry price paid data | None | [Open data](https://landregistry.data.gov.uk/) |

Use the same credentials as the backend, exported in your shell or saved in a gitignored `.env.local` in the project root or `backend/`. The runner loads Vite's `test` mode env files (`.env`, `.env.local`, `.env.test`, `.env.test.local`); shell variables take precedence, followed by root files, then backend files. Missing credentials fail their own test with setup guidance. HTTP 401/403 failures include the provider's key-management link and variable to update. Request URLs, authorization headers and error response bodies are excluded from diagnostics.

For EPC, save the bearer token from **My account** on the [EPC service](https://get-energy-performance-data.communities.gov.uk/guidance/energy-certificate-data-apis) in `APP_EPC_API_KEY`, without the `Bearer ` prefix. The backend and test send it as Bearer authentication to `https://api.get-energy-performance-data.communities.gov.uk/api/`. `APP_EPC_USERNAME` is no longer required or used. The EPC test checks domestic certificate search and, when a certificate is found, retrieves its full details and validates the energy ratings and floor area.

Stripe uses a read-only Checkout Sessions list request; it never creates payments or checkouts. A restricted key needs permission to read Checkout Sessions. This check does not validate checkout creation or `STRIPE_WEBHOOK_SECRET`. Empty flood alerts, crime records, EPC search results and price history are valid responses. Requests time out after 20 seconds and are not retried.

Run one provider, or verify the diagnostic messages offline:

```sh
npm run test:web-services -- --grep "OS Places"
npm run test:web-services:unit
```

## Disclaimer

This has been an experiment to dip my toes into the world of vibe-coding. I can not vouch for the code quality.
