package com.houselookup.backend.controller;

import com.houselookup.backend.service.EpcService;
import com.houselookup.backend.service.FloodRiskService;
import com.houselookup.backend.service.LandRegistryService;
import com.houselookup.backend.service.ReportPdfService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/report")
public class ReportController {

  private final EpcService epcService;
  private final LandRegistryService landRegistryService;
  private final FloodRiskService floodRiskService;
  private final ReportPdfService reportPdfService;

  public ReportController(
      EpcService epcService,
      LandRegistryService landRegistryService,
      FloodRiskService floodRiskService,
      ReportPdfService reportPdfService) {
    this.epcService = epcService;
    this.landRegistryService = landRegistryService;
    this.floodRiskService = floodRiskService;
    this.reportPdfService = reportPdfService;
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> generateReport(
      @RequestParam String uprn,
      @RequestParam String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory,
      @RequestParam(defaultValue = "false") boolean includeFloodRisk,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {

    if (uprn == null || uprn.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }
    if (postcode == null || postcode.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postcode is required.");
    }

    // Fetch EPC data if requested
    Map<String, Object> epcData = null;
    if (includeEpc) {
      Optional<Map<String, Object>> epcResult = epcService.fetchByUprn(uprn.trim());
      epcData = epcResult.orElse(null);
    }

    // Fetch price history if requested
    List<Map<String, Object>> priceHistory = null;
    if (includePriceHistory) {
      priceHistory = landRegistryService.fetchPriceHistory(postcode.trim(), paon);
    }

    // Fetch flood risk if requested
    Map<String, Object> floodRiskData = null;
    if (includeFloodRisk) {
      floodRiskData = floodRiskService.fetchAssessment(latitude, longitude);
    }

    // Check we have at least some data
    if (epcData == null
        && (priceHistory == null || priceHistory.isEmpty())
        && floodRiskData == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No data found for this property.");
    }

    try {
      byte[] pdfBytes = reportPdfService.generateReport(epcData, priceHistory, floodRiskData, postcode);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      String filename = buildReportFilename(epcData, priceHistory, postcode);
      headers.setContentDispositionFormData("attachment", filename);
      headers.setContentLength(pdfBytes.length);

      return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage());
    }
  }

  private String buildReportFilename(
      Map<String, Object> epcData, List<Map<String, Object>> priceHistory, String postcode) {
    String firstLine = firstLineFromEpc(epcData);
    if (firstLine == null || firstLine.isBlank()) {
      firstLine = firstLineFromPriceHistory(priceHistory);
    }
    if (firstLine == null || firstLine.isBlank()) {
      firstLine = "Property";
    }

    String rawName = "Property Report: " + firstLine.trim() + " " + postcode.trim();
    return safeFilename(rawName) + ".pdf";
  }

  private String firstLineFromEpc(Map<String, Object> epcData) {
    if (epcData == null) {
      return null;
    }
    Object address1 = epcData.get("address1");
    if (address1 instanceof String && !((String) address1).isBlank()) {
      return (String) address1;
    }
    Object address = epcData.get("address");
    if (address instanceof String && !((String) address).isBlank()) {
      String fullAddress = ((String) address).trim();
      int commaIndex = fullAddress.indexOf(',');
      return commaIndex >= 0 ? fullAddress.substring(0, commaIndex).trim() : fullAddress;
    }
    return null;
  }

  private String firstLineFromPriceHistory(List<Map<String, Object>> priceHistory) {
    if (priceHistory == null || priceHistory.isEmpty()) {
      return null;
    }
    Map<String, Object> record = priceHistory.get(0);
    StringBuilder firstLine = new StringBuilder();
    appendIfPresent(firstLine, record.get("saon"), ", ");
    appendIfPresent(firstLine, record.get("paon"), " ");
    appendIfPresent(firstLine, record.get("street"), "");
    return firstLine.toString().trim();
  }

  private void appendIfPresent(StringBuilder builder, Object value, String suffix) {
    if (value instanceof String && !((String) value).isBlank()) {
      builder.append(((String) value).trim()).append(suffix);
    }
  }

  private String safeFilename(String rawName) {
    return rawName.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s{2,}", " ").trim();
  }
}
