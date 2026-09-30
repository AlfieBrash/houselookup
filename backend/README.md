# Property Pal Backend

Spring Boot backend that proxies EPC + OS Places lookups to avoid CORS issues and keep API keys off the client.

## Requirements
- Java 17+
- Maven

## Environment variables
- `APP_EPC_API_KEY` (bearer token from **My account** on the [EPC service](https://get-energy-performance-data.communities.gov.uk/guidance/energy-certificate-data-apis); save the token alone, without a `Bearer ` prefix)
- `APP_OS_PLACES_API_KEY` (Ordnance Survey Places API key with access to postcode lookups)
- `APP_FLOOD_SEARCH_RADIUS_KM` (optional, default `5`; radius for Environment Agency flood checks)

The EPC integration uses `https://api.get-energy-performance-data.communities.gov.uk/api/` with Bearer authentication. It searches domestic certificates by UPRN and retrieves the latest certificate's full details. `APP_EPC_USERNAME` is no longer required or used.

`APP_EPC_ENDPOINT` optionally overrides the API base URL (ending in `/api`, not `/domestic/search`). Remove any deployment override pointing at `epc.opendatacommunities.org`. Existing credentials for the old service must be replaced with a token from the new service.

The backend calls `/domestic/search?uprn=...&page_size=1`, then `/certificate?certificate_number=...`. Search results are ordered by newest registration first by the [provider's search implementation](https://github.com/communitiesuk/epb-data-warehouse/blob/main/lib/gateway/assessment_search_gateway.rb). Certificate details are adapted to the existing `/api/epc` response fields, including numeric costs, readable building features and energy ratings, so EPC PDFs and combined reports use the same data. The upstream contract is documented in the [official OpenAPI specification](https://github.com/communitiesuk/epb-data-warehouse/blob/main/api/api.yml).

## Run locally
```
mvn spring-boot:run
```

The server starts on `http://localhost:8081`.

Spring Boot reads the environment variables exported to its process; it does not automatically load the frontend's `.env.local` files.

## Test the EPC integration

From `backend/`, run `mvn test` for offline API contract and PDF regression tests. No API token or database is needed. From the project root, `npm run test:web-services -- --grep EPC` checks access to the live provider using `APP_EPC_API_KEY`.

## Frontend integration
Set `VITE_BACKEND_URL=http://localhost:8081` in the frontend environment (optional; defaults to 8081).
