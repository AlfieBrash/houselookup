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
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("EPC API key missing. Set APP_EPC_API_KEY and APP_EPC_USERNAME to enable lookup.");
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
}
