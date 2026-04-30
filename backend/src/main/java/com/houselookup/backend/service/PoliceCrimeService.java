package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PoliceCrimeService {
  private static final Logger log = LoggerFactory.getLogger(PoliceCrimeService.class);

  private static final Map<String, String> CATEGORY_LABELS =
      Map.ofEntries(
          Map.entry("anti-social-behaviour", "Anti-social behaviour"),
          Map.entry("bicycle-theft", "Bicycle theft"),
          Map.entry("burglary", "Burglary"),
          Map.entry("criminal-damage-arson", "Criminal damage and arson"),
          Map.entry("drugs", "Drugs"),
          Map.entry("other-crime", "Other crime"),
          Map.entry("other-theft", "Other theft"),
          Map.entry("possession-of-weapons", "Possession of weapons"),
          Map.entry("public-order", "Public order"),
          Map.entry("robbery", "Robbery"),
          Map.entry("shoplifting", "Shoplifting"),
          Map.entry("theft-from-the-person", "Theft from the person"),
          Map.entry("vehicle-crime", "Vehicle crime"),
          Map.entry("violent-crime", "Violence and sexual offences"));

  private final RestTemplate restTemplate;
  private final String endpoint;
  private final String lastUpdatedEndpoint;
  private final int reportingMonths;

  public PoliceCrimeService(
      RestTemplate restTemplate,
      @Value("${app.police-crime.endpoint}") String endpoint,
      @Value("${app.police-crime.last-updated-endpoint}") String lastUpdatedEndpoint,
      @Value("${app.police-crime.reporting-months}") int reportingMonths) {
    this.restTemplate = restTemplate;
    this.endpoint = endpoint;
    this.lastUpdatedEndpoint = lastUpdatedEndpoint;
    this.reportingMonths = Math.max(1, reportingMonths);
  }

  public Map<String, Object> fetchAssessment(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      log.warn("Crime assessment unavailable reason=missing_location");
      return unavailableAssessment("Crime data could not be assessed because location was missing.");
    }

    try {
      YearMonth latestMonth = fetchLatestAvailableMonth();
      if (latestMonth == null) {
        log.warn("Crime assessment unavailable reason=latest_month_missing");
        return unavailableAssessment("Crime data is temporarily unavailable.");
      }

      List<MonthlyCrimeData> monthlyData = new ArrayList<>();
      for (int i = 0; i < reportingMonths; i++) {
        YearMonth month = latestMonth.minusMonths(i);
        try {
          JsonNode crimes = fetchCrimes(latitude, longitude, month);
          if (crimes != null && crimes.isArray()) {
            monthlyData.add(new MonthlyCrimeData(month, crimes));
          }
        } catch (Exception e) {
          log.debug(
              "Crime monthly lookup failed location={} month={}",
              roundedLocation(latitude, longitude),
              month,
              e);
          // Keep any months that did return data instead of dropping the whole crime section.
        }
      }
      if (monthlyData.isEmpty()) {
        log.warn("Crime assessment unavailable reason=no_monthly_data location={}", roundedLocation(latitude, longitude));
        return unavailableAssessment("Crime data is temporarily unavailable.");
      }
      Map<String, Object> assessment = parseAssessment(monthlyData, latitude, longitude);
      log.info(
          "Crime assessment completed location={} reportingMonths={} totalCrimes={}",
          roundedLocation(latitude, longitude),
          assessment.get("reportingMonths"),
          assessment.get("totalCrimes"));
      return assessment;
    } catch (Exception e) {
      log.warn("Crime assessment failed location={}", roundedLocation(latitude, longitude), e);
      return unavailableAssessment("Crime data is temporarily unavailable.");
    }
  }

  private String roundedLocation(Double latitude, Double longitude) {
    return String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
  }

  private JsonNode fetchCrimes(Double latitude, Double longitude, YearMonth month) {
    String url =
        UriComponentsBuilder.fromHttpUrl(endpoint)
            .queryParam("lat", latitude)
            .queryParam("lng", longitude)
            .queryParam("date", month)
            .toUriString();

    ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
    return response.getBody();
  }

  private YearMonth fetchLatestAvailableMonth() {
    ResponseEntity<JsonNode> response = restTemplate.getForEntity(lastUpdatedEndpoint, JsonNode.class);
    String latestDate = safeText(response.getBody() == null ? null : response.getBody().path("date"));
    if (latestDate == null || latestDate.length() < 7) {
      return null;
    }
    return YearMonth.parse(latestDate.substring(0, 7));
  }

  private Map<String, Object> parseAssessment(
      List<MonthlyCrimeData> monthlyData, Double latitude, Double longitude) {
    Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    int totalCrimes = 0;

    for (MonthlyCrimeData monthData : monthlyData) {
      JsonNode root = monthData.crimes();
      if (root != null && root.isArray()) {
        for (JsonNode item : root) {
          totalCrimes++;

          String category = safeText(item.path("category"));
          if (category != null) {
            categoryCounts.merge(category, 1, Integer::sum);
          }
        }
      }
    }

    List<Map<String, Object>> categories = new ArrayList<>();
    categoryCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
        .forEach(
            entry -> {
              Map<String, Object> category = new LinkedHashMap<>();
              category.put("category", entry.getKey());
              category.put("label", categoryLabel(entry.getKey()));
              category.put("count", entry.getValue());
              categories.add(category);
            });

    YearMonth periodEnd = monthlyData.get(0).month();
    YearMonth periodStart = monthlyData.get(monthlyData.size() - 1).month();
    double monthlyAverage = totalCrimes / (double) monthlyData.size();

    Map<String, Object> assessment = new LinkedHashMap<>();
    assessment.put("available", totalCrimes > 0);
    assessment.put("summary", buildSummary(totalCrimes, periodStart, periodEnd));
    assessment.put("month", periodEnd.toString());
    assessment.put("periodStart", periodStart.toString());
    assessment.put("periodEnd", periodEnd.toString());
    assessment.put("periodLabel", periodLabel(periodStart, periodEnd));
    assessment.put("reportingMonths", monthlyData.size());
    assessment.put("requestedReportingMonths", reportingMonths);
    assessment.put("totalCrimes", totalCrimes);
    assessment.put("monthlyAverage", roundOneDecimal(monthlyAverage));
    assessment.put("monthlyAverageSummary", buildMonthlyAverageSummary(monthlyAverage));
    assessment.put("categories", categories);
    assessment.put("latitude", latitude);
    assessment.put("longitude", longitude);
    assessment.put("source", "Police UK");
    assessment.put("approximateLocation", true);

    return assessment;
  }

  private Map<String, Object> unavailableAssessment(String summary) {
    Map<String, Object> assessment = new LinkedHashMap<>();
    assessment.put("available", false);
    assessment.put("summary", summary);
    assessment.put("month", null);
    assessment.put("periodStart", null);
    assessment.put("periodEnd", null);
    assessment.put("periodLabel", null);
    assessment.put("reportingMonths", reportingMonths);
    assessment.put("requestedReportingMonths", reportingMonths);
    assessment.put("totalCrimes", 0);
    assessment.put("monthlyAverage", 0);
    assessment.put("monthlyAverageSummary", null);
    assessment.put("categories", List.of());
    assessment.put("source", "Police UK");
    assessment.put("approximateLocation", true);
    return assessment;
  }

  private String buildSummary(int totalCrimes, YearMonth periodStart, YearMonth periodEnd) {
    String period = periodLabel(periodStart, periodEnd);
    return totalCrimes == 0
        ? "No street-level crimes were reported from " + period + " near this location."
        : totalCrimes
            + " street-level crimes were reported from "
            + period
            + " within about 1 mile of this location.";
  }

  private String buildMonthlyAverageSummary(double monthlyAverage) {
    return "That is an average of "
        + roundOneDecimal(monthlyAverage)
        + " reported crimes per month.";
  }

  private String periodLabel(YearMonth periodStart, YearMonth periodEnd) {
    return monthLabel(periodStart) + " to " + monthLabel(periodEnd);
  }

  private String monthLabel(YearMonth month) {
    return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK) + " " + month.getYear();
  }

  private double roundOneDecimal(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private String categoryLabel(String category) {
    return CATEGORY_LABELS.getOrDefault(category, humanizeCategory(category));
  }

  private String humanizeCategory(String category) {
    if (category == null || category.isBlank()) {
      return "Unknown";
    }
    String[] parts = category.split("-");
    StringBuilder label = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isBlank()) {
        continue;
      }
      if (label.length() > 0) {
        label.append(' ');
      }
      label.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT));
      if (parts[i].length() > 1) {
        label.append(parts[i].substring(1));
      }
    }
    return label.toString();
  }

  private String safeText(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record MonthlyCrimeData(YearMonth month, JsonNode crimes) {}
}
