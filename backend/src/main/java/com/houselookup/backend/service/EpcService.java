package com.houselookup.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class EpcService {
  private static final String TEST_UPRN = "200000644141";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String endpoint;
  private final String username;
  private final String apiKey;

  public EpcService(
      RestTemplate restTemplate,
      ObjectMapper objectMapper,
      @Value("${app.epc.endpoint}") String endpoint,
      @Value("${app.epc.username:}") String username,
      @Value("${app.epc.api-key:}") String apiKey) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.endpoint = endpoint;
    this.username = username;
    this.apiKey = apiKey;
  }

  public Optional<Map<String, Object>> fetchByUprn(String uprn) {
    if (TEST_UPRN.equals(uprn)) {
      return Optional.of(mockEpc());
    }

    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("EPC API key missing. Set EPC_API_KEY to enable lookup.");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    headers.set(HttpHeaders.AUTHORIZATION, "Basic " + buildAuthToken());
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<JsonNode> response;
    try {
      response =
          restTemplate.exchange(
              endpoint + "?uprn={uprn}", HttpMethod.GET, entity, JsonNode.class, uprn);
    } catch (HttpClientErrorException.NotFound notFound) {
      return Optional.empty();
    } catch (HttpClientErrorException.Unauthorized
        | HttpClientErrorException.Forbidden authError) {
      throw new IllegalStateException("EPC API key is invalid or lacks access.", authError);
    }

    JsonNode root = response.getBody();
    if (root == null || !root.has("rows") || !root.get("rows").isArray()) {
      return Optional.empty();
    }

    JsonNode rows = root.get("rows");
    if (rows.isEmpty()) {
      return Optional.empty();
    }

    Map<String, Object> row =
        objectMapper.convertValue(rows.get(0), new TypeReference<LinkedHashMap<String, Object>>() {});
    return Optional.of(row);
  }

  private String buildAuthToken() {
    if (username != null && !username.isBlank()) {
      String credentials = username + ":" + apiKey;
      return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.US_ASCII));
    }
    return apiKey;
  }

  private Map<String, Object> mockEpc() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("address", "1 Test Street, Great Givendale, York, YO42 1TT");
    data.put("address1", "1 Test Street");
    data.put("address2", "Great Givendale");
    data.put("address3", "");
    data.put("postcode", "YO42 1TT");
    data.put("uprn", "200000644141");
    data.put("building-reference-number", "1234567890");
    data.put("current-energy-rating", "D");
    data.put("potential-energy-rating", "B");
    data.put("current-energy-efficiency", 58);
    data.put("potential-energy-efficiency", 82);
    data.put("property-type", "House");
    data.put("built-form", "Detached");
    data.put("total-floor-area", 120);
    data.put("main-fuel", "Oil");
    data.put("lodgement-date", "2022-03-15");
    data.put("inspection-date", "2022-03-10");
    data.put("transaction-type", "marketed sale");
    data.put("environment-impact-current", 45);
    data.put("environment-impact-potential", 70);
    data.put("energy-consumption-current", 280);
    data.put("energy-consumption-potential", 150);
    data.put("co2-emissions-current", 5.2);
    data.put("co2-emissions-potential", 2.8);
    data.put("co2-emiss-curr-per-floor-area", 43);
    data.put("lighting-cost-current", 120);
    data.put("lighting-cost-potential", 80);
    data.put("heating-cost-current", 1200);
    data.put("heating-cost-potential", 650);
    data.put("hot-water-cost-current", 180);
    data.put("hot-water-cost-potential", 120);
    data.put("windows-description", "Fully double glazed");
    data.put("walls-description", "Cavity wall, as built, insulated (assumed)");
    data.put("roof-description", "Pitched, 200 mm loft insulation");
    data.put("floor-description", "Suspended, limited insulation (assumed)");
    data.put("main-heating-controls", "Programmer and room thermostat");
    data.put("main-heat-description", "Boiler and radiators, oil");
    data.put("hot-water-description", "From main system");
    data.put("lighting-description", "Low energy lighting in 50% of fixed outlets");
    data.put("tenure", "owner-occupied");
    data.put("construction-age-band", "1950-1966");
    return data;
  }
}
