package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FloodRiskService {
  private final RestTemplate restTemplate;
  private final String endpoint;
  private final int searchRadiusKm;

  public FloodRiskService(
      RestTemplate restTemplate,
      @Value("${app.flood-monitoring.endpoint}") String endpoint,
      @Value("${app.flood-monitoring.search-radius-km}") int searchRadiusKm) {
    this.restTemplate = restTemplate;
    this.endpoint = endpoint;
    this.searchRadiusKm = searchRadiusKm;
  }

  public Map<String, Object> fetchAssessment(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return unavailableAssessment("Flood risk could not be assessed because location was missing.");
    }

    String url =
        UriComponentsBuilder.fromHttpUrl(endpoint)
            .queryParam("lat", latitude)
            .queryParam("long", longitude)
            .queryParam("dist", searchRadiusKm)
            .queryParam("min-severity", 3)
            .toUriString();

    try {
      ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
      return parseAssessment(response.getBody(), latitude, longitude);
    } catch (Exception ignored) {
      return unavailableAssessment("Flood risk data is temporarily unavailable.");
    }
  }

  private Map<String, Object> parseAssessment(
      JsonNode root, Double latitude, Double longitude) {
    List<Map<String, Object>> warnings = new ArrayList<>();
    int highestSeverityLevel = Integer.MAX_VALUE;

    if (root != null && root.has("items") && root.get("items").isArray()) {
      for (JsonNode item : root.get("items")) {
        int severityLevel = parseSeverityLevel(item.path("severityLevel").asText(null));
        highestSeverityLevel = Math.min(highestSeverityLevel, severityLevel);

        JsonNode floodArea = item.path("floodArea");
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("severity", safeText(item.path("severity")));
        warning.put("severityLevel", severityLevel);
        warning.put("areaName", safeText(floodArea.path("description")));
        warning.put("county", safeText(floodArea.path("county")));
        warning.put("riverOrSea", safeText(floodArea.path("riverOrSea")));
        warning.put("message", safeText(item.path("message")));
        warning.put("timeRaised", safeText(item.path("timeRaised")));
        warnings.add(warning);
      }
    }

    String overallRisk = warnings.isEmpty() ? "Low" : overallRisk(highestSeverityLevel);
    String summary =
        warnings.isEmpty()
            ? "No active flood alerts or warnings were found within "
                + searchRadiusKm
                + " km of this location."
            : warnings.size()
                + " active flood alert(s) or warning(s) were found within "
                + searchRadiusKm
                + " km. Highest severity: "
                + overallRisk
                + ".";

    Map<String, Object> assessment = new LinkedHashMap<>();
    assessment.put("available", true);
    assessment.put("overallRisk", overallRisk);
    assessment.put("summary", summary);
    assessment.put("searchRadiusKm", searchRadiusKm);
    assessment.put("activeWarningCount", warnings.size());
    assessment.put("warnings", warnings);
    assessment.put("latitude", latitude);
    assessment.put("longitude", longitude);

    return assessment;
  }

  private Map<String, Object> unavailableAssessment(String summary) {
    Map<String, Object> assessment = new LinkedHashMap<>();
    assessment.put("available", false);
    assessment.put("overallRisk", "Unknown");
    assessment.put("summary", summary);
    assessment.put("searchRadiusKm", searchRadiusKm);
    assessment.put("activeWarningCount", 0);
    assessment.put("warnings", List.of());
    return assessment;
  }

  private int parseSeverityLevel(String value) {
    if (value == null || value.isBlank()) {
      return Integer.MAX_VALUE;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return Integer.MAX_VALUE;
    }
  }

  private String overallRisk(int severityLevel) {
    if (severityLevel <= 1) {
      return "Severe";
    }
    if (severityLevel == 2) {
      return "High";
    }
    if (severityLevel == 3) {
      return "Moderate";
    }
    return "Low";
  }

  private String safeText(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value.trim();
  }
}
