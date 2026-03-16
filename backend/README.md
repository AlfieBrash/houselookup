# Property Pal Backend

Spring Boot backend that proxies EPC + OS Places lookups to avoid CORS issues and keep API keys off the client.

## Requirements
- Java 17+
- Maven

## Environment variables
- `APP_EPC_API_KEY` (API key from https://epc.opendatacommunities.org/login)
- `APP_EPC_USERNAME` (email/username for the EPC API; when set, backend builds Basic auth using `username:apiKey`)
- `APP_OS_PLACES_API_KEY` (Ordnance Survey Places API key with access to postcode lookups)
- `APP_FLOOD_SEARCH_RADIUS_KM` (optional, default `5`; radius for Environment Agency flood checks)

## Run locally
```
mvn spring-boot:run
```

The server starts on `http://localhost:8081`.

## Frontend integration
Set `VITE_BACKEND_URL=http://localhost:8081` in the frontend environment (optional; defaults to 8081).
