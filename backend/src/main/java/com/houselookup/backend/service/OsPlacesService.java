package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.houselookup.backend.model.Address;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OsPlacesService {
  private final RestTemplate restTemplate;
  private final String endpoint;
  private final String apiKey;

  public OsPlacesService(
      RestTemplate restTemplate,
      @Value("${app.os-places.endpoint}") String endpoint,
      @Value("${app.os-places.api-key:}") String apiKey) {
    this.restTemplate = restTemplate;
    this.endpoint = endpoint;
    this.apiKey = apiKey;
  }

  public List<Address> lookupAddresses(String postcode) {
    String trimmed = postcode == null ? "" : postcode.trim();
    if (trimmed.contains("%")) {
      trimmed = URLDecoder.decode(trimmed, StandardCharsets.UTF_8);
    }

    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "OS Places API key missing. Set OS_PLACES_API_KEY to enable lookup.");
    }

    String normalised = trimmed.replaceAll("\\s+", "").toUpperCase();
    String url =
        UriComponentsBuilder.fromHttpUrl(endpoint)
            .queryParam("postcode", normalised)
            .queryParam("key", apiKey)
            .toUriString();

    ResponseEntity<JsonNode> response;
    try {
      response = restTemplate.getForEntity(url, JsonNode.class);
    } catch (HttpClientErrorException.BadRequest badRequest) {
      String message = badRequest.getResponseBodyAsString();
      throw new IllegalArgumentException(
          message == null || message.isBlank() ? "Invalid postcode for OS Places." : message,
          badRequest);
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden authError) {
      throw new IllegalStateException("OS Places API key is invalid or lacks access.", authError);
    }
    JsonNode root = response.getBody();
    if (root == null || !root.has("results")) {
      return List.of();
    }

    List<Address> addresses = new ArrayList<>();
    for (JsonNode result : root.get("results")) {
      JsonNode dpa = result.get("DPA");
      Address address = buildAddress(dpa, trimmed);
      if (address != null) {
        addresses.add(address);
      }
    }

    return addresses;
  }

  private Address buildAddress(JsonNode dpa, String fallbackPostcode) {
    if (dpa == null || dpa.isNull()) {
      return null;
    }

    String street =
        normaliseParts(
            dpa.path("DEPENDENT_THOROUGHFARE_NAME").asText(null),
            dpa.path("THOROUGHFARE_NAME").asText(null));
    String line1 =
        normaliseParts(
            dpa.path("ORGANISATION_NAME").asText(null),
            dpa.path("SUB_BUILDING_NAME").asText(null),
            dpa.path("BUILDING_NAME").asText(null),
            dpa.path("BUILDING_NUMBER").asText(null),
            street == null || street.isBlank() ? null : street);

    String line2 =
        joinWithComma(
            dpa.path("DOUBLE_DEPENDENT_LOCALITY").asText(null),
            dpa.path("DEPENDENT_LOCALITY").asText(null));

    String addressRaw = dpa.path("ADDRESS").asText(null);
    String fallbackLine1 = addressRaw == null ? null : addressRaw.split(",")[0].trim();
    String resolvedLine1 = firstNonEmpty(line1, fallbackLine1);
    String town = safeTrim(dpa.path("POST_TOWN").asText(null));
    String postcode = safeTrim(dpa.path("POSTCODE").asText(null));
    String uprn = safeTrim(dpa.path("UPRN").asText(null));

    if (resolvedLine1 == null || town == null || uprn == null) {
      return null;
    }

    String resolvedPostcode = postcode == null ? fallbackPostcode : postcode;
    if (resolvedPostcode == null || resolvedPostcode.isBlank()) {
      return null;
    }

    return new Address(uprn, resolvedLine1, emptyToNull(line2), town, resolvedPostcode);
  }

  private String normaliseParts(String... parts) {
    List<String> filtered = new ArrayList<>();
    for (String part : parts) {
      String trimmed = safeTrim(part);
      if (trimmed != null && !trimmed.isEmpty()) {
        filtered.add(trimmed);
      }
    }
    if (filtered.isEmpty()) {
      return null;
    }
    return String.join(" ", filtered).replaceAll("\\s+", " ").trim();
  }

  private String joinWithComma(String... parts) {
    List<String> filtered = new ArrayList<>();
    for (String part : parts) {
      String trimmed = safeTrim(part);
      if (trimmed != null && !trimmed.isEmpty()) {
        filtered.add(trimmed);
      }
    }
    if (filtered.isEmpty()) {
      return null;
    }
    return String.join(", ", filtered);
  }

  private String safeTrim(String value) {
    return value == null ? null : value.trim();
  }

  private String emptyToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String firstNonEmpty(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary.trim();
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback.trim();
    }
    return null;
  }
}
