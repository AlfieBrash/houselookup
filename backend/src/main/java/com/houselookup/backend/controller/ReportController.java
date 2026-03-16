package com.houselookup.backend.controller;

import com.houselookup.backend.model.ReportDownloadToken;
import com.houselookup.backend.model.User;
import com.houselookup.backend.service.AuthService;
import com.houselookup.backend.service.CreditService;
import com.houselookup.backend.service.EpcService;
import com.houselookup.backend.service.FloodRiskService;
import com.houselookup.backend.service.LandRegistryService;
import com.houselookup.backend.service.ReportPdfService;
import com.houselookup.backend.service.ReportTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final AuthService authService;
  private final CreditService creditService;
  private final ReportTokenService tokenService;

  @Value("${app.report.legacy-download-enabled:false}")
  private boolean legacyDownloadEnabled;

  public ReportController(
      EpcService epcService,
      LandRegistryService landRegistryService,
      FloodRiskService floodRiskService,
      ReportPdfService reportPdfService,
      AuthService authService,
      CreditService creditService,
      ReportTokenService tokenService) {
    this.epcService = epcService;
    this.landRegistryService = landRegistryService;
    this.floodRiskService = floodRiskService;
    this.reportPdfService = reportPdfService;
    this.authService = authService;
    this.creditService = creditService;
    this.tokenService = tokenService;
  }

  @GetMapping("/preview")
  public ReportPreviewResponse previewReport(
      @RequestParam String uprn,
      @RequestParam String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory,
      @RequestParam(defaultValue = "false") boolean includeFloodRisk,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {

    if (!hasAnySelection(includeEpc, includePriceHistory, includeFloodRisk)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one data section to preview.");
    }

    ReportInputs inputs =
        normalizeInputs(uprn, postcode, paon, includeEpc, includePriceHistory, includeFloodRisk, latitude, longitude);
    ReportData data = fetchReportData(inputs);

    boolean epcAvailable = data.epcData() != null;
    boolean priceHistoryAvailable = isPriceHistoryAvailable(data.priceHistory());
    boolean floodRiskAvailable = isFloodRiskAvailable(data.floodRiskData());

    int sections =
        (epcAvailable ? 1 : 0) + (priceHistoryAvailable ? 1 : 0) + (floodRiskAvailable ? 1 : 0);

    return new ReportPreviewResponse(
        inputs,
        epcAvailable,
        priceHistoryAvailable,
        floodRiskAvailable,
        sections,
        estimateSummary(epcAvailable, priceHistoryAvailable, floodRiskAvailable));
  }

  @PostMapping("/prepare")
  public ReportPrepareResponse prepareDownload(
      @RequestParam String uprn,
      @RequestParam String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory,
      @RequestParam(defaultValue = "false") boolean includeFloodRisk,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      HttpServletRequest request) {

    User user = authService.requireUser(request);
    if (!hasAnySelection(includeEpc, includePriceHistory, includeFloodRisk)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one data section to prepare.");
    }

    ReportInputs inputs =
        normalizeInputs(uprn, postcode, paon, includeEpc, includePriceHistory, includeFloodRisk, latitude, longitude);

    if (!creditService.consumeOne(user.getId())) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient credits.");
    }

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("uprn", inputs.uprn());
      payload.put("postcode", inputs.postcode());
      if (inputs.paon() != null) {
        payload.put("paon", inputs.paon());
      }
      payload.put("includeEpc", inputs.includeEpc());
      payload.put("includePriceHistory", inputs.includePriceHistory());
      payload.put("includeFloodRisk", inputs.includeFloodRisk());
      if (inputs.latitude() != null) {
        payload.put("latitude", inputs.latitude());
      }
      if (inputs.longitude() != null) {
        payload.put("longitude", inputs.longitude());
      }

      String token =
          tokenService.createToken(user, payload);

      String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
      return new ReportPrepareResponse(token, "/api/report/pdf?token=" + encoded);
    } catch (RuntimeException e) {
      creditService.addCredits(user.getId(), 1);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create download token.");
    }
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> generateReport(
      @RequestParam(required = false) String token,
      @RequestParam(required = false) String uprn,
      @RequestParam(required = false) String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory,
      @RequestParam(defaultValue = "false") boolean includeFloodRisk,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {

    if (token != null && !token.isBlank()) {
      return downloadByToken(token);
    }

    if (!legacyDownloadEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token is required for report downloads.");
    }

    if (uprn == null || postcode == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN and postcode are required for legacy download.");
    }

    ReportInputs inputs =
        normalizeInputs(uprn, postcode, paon, includeEpc, includePriceHistory, includeFloodRisk, latitude, longitude);
    return servePdf(inputs);
  }

  private ResponseEntity<byte[]> downloadByToken(String token) {
    ReportDownloadToken tokenRecord = tokenService.claimToken(token);

    try {
      ReportInputs inputs = readInputs(tokenRecord.getRequestPayloadJson());
      ReportData data = fetchReportData(inputs);

      if (hasNoAvailableData(data)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for this property.");
      }

      byte[] bytes = buildPdf(inputs, data);
      tokenService.markCompleted(tokenRecord);
      return servePdfResponse(bytes, buildReportFilename(data, inputs.postcode()));
    } catch (ResponseStatusException ex) {
      handleTokenDownloadFailure(tokenRecord, ex.getMessage());
      throw ex;
    } catch (Exception ex) {
      handleTokenDownloadFailure(tokenRecord, "Failed to generate report.");
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.");
    }
  }

  private void handleTokenDownloadFailure(ReportDownloadToken tokenRecord, String message) {
    tokenService.markFailed(tokenRecord, message == null ? "Failed to generate report." : message);
    if (tokenRecord.getUser() != null) {
      creditService.addCredits(tokenRecord.getUser().getId(), 1);
    }
  }

  private ResponseEntity<byte[]> servePdf(ReportInputs inputs) {
    ReportData data = fetchReportData(inputs);
    if (hasNoAvailableData(data)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for this property.");
    }
    byte[] bytes = buildPdf(inputs, data);
    return servePdfResponse(bytes, buildReportFilename(data, inputs.postcode()));
  }

  private byte[] buildPdf(ReportInputs inputs, ReportData data) {
    try {
      return reportPdfService.generateReport(
          data.epcData(),
          data.priceHistory(),
          data.floodRiskData(),
          inputs.postcode());
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage());
    }
  }

  private ResponseEntity<byte[]> servePdfResponse(byte[] pdfBytes, String filename) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", filename);
    headers.setContentLength(pdfBytes.length);
    return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
  }

  private ReportInputs normalizeInputs(
      String uprn,
      String postcode,
      String paon,
      boolean includeEpc,
      boolean includePriceHistory,
      boolean includeFloodRisk,
      Double latitude,
      Double longitude) {
    if (uprn == null || uprn.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }
    if (postcode == null || postcode.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postcode is required.");
    }
    return new ReportInputs(
        uprn.trim(),
        postcode.trim(),
        trimToNull(paon),
        includeEpc,
        includePriceHistory,
        includeFloodRisk,
        latitude,
        longitude);
  }

  private ReportData fetchReportData(ReportInputs inputs) {
    Map<String, Object> epcData = null;
    if (inputs.includeEpc()) {
      epcData = epcService.fetchByUprn(inputs.uprn()).orElse(null);
    }

    List<Map<String, Object>> priceHistory = List.of();
    if (inputs.includePriceHistory()) {
      priceHistory = landRegistryService.fetchPriceHistory(inputs.postcode(), inputs.paon());
      if (priceHistory == null) {
        priceHistory = List.of();
      }
    }

    Map<String, Object> floodRiskData = null;
    if (inputs.includeFloodRisk()) {
      floodRiskData = floodRiskService.fetchAssessment(inputs.latitude(), inputs.longitude());
    }

    return new ReportData(epcData, priceHistory, floodRiskData);
  }

  private boolean hasAnySelection(boolean includeEpc, boolean includePriceHistory, boolean includeFloodRisk) {
    return includeEpc || includePriceHistory || includeFloodRisk;
  }

  private boolean isPriceHistoryAvailable(List<Map<String, Object>> priceHistory) {
    return priceHistory != null && !priceHistory.isEmpty();
  }

  private boolean isFloodRiskAvailable(Map<String, Object> floodRiskData) {
    if (floodRiskData == null) {
      return false;
    }
    Object available = floodRiskData.get("available");
    if (available instanceof Boolean) {
      return (Boolean) available;
    }
    return false;
  }

  private boolean hasNoAvailableData(ReportData data) {
    return data.epcData() == null
        && !isPriceHistoryAvailable(data.priceHistory())
        && !isFloodRiskAvailable(data.floodRiskData());
  }

  private ReportInputs readInputs(String payloadJson) {
    Map<String, Object> payload = tokenService.parsePayload(payloadJson);
    return new ReportInputs(
        requireText(payload.get("uprn"), "UPRN"),
        requireText(payload.get("postcode"), "Postcode"),
        asString(payload.get("paon")),
        parseBoolean(payload.get("includeEpc"), true),
        parseBoolean(payload.get("includePriceHistory"), true),
        parseBoolean(payload.get("includeFloodRisk"), false),
        parseDouble(payload.get("latitude")),
        parseDouble(payload.get("longitude")));
  }

  private boolean parseBoolean(Object value, boolean fallback) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String str) {
      return Boolean.parseBoolean(str);
    }
    return fallback;
  }

  private Double parseDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Double d) {
      return d;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private String asString(Object value) {
    return value == null ? null : value.toString();
  }

  private String requireText(Object value, String label) {
    String text = asString(value);
    if (text == null || text.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required.");
    }
    return text;
  }

  private String estimateSummary(boolean epcAvailable, boolean priceHistoryAvailable, boolean floodRiskAvailable) {
    if (epcAvailable && priceHistoryAvailable && floodRiskAvailable) {
      return "EPC, price history and flood risk data available.";
    }
    if (epcAvailable && priceHistoryAvailable) {
      return "EPC and price history available.";
    }
    if (epcAvailable && floodRiskAvailable) {
      return "EPC and flood risk available.";
    }
    if (priceHistoryAvailable && floodRiskAvailable) {
      return "Price history and flood risk available.";
    }
    if (epcAvailable) {
      return "EPC data available.";
    }
    if (priceHistoryAvailable) {
      return "Price history data available.";
    }
    if (floodRiskAvailable) {
      return "Flood risk data available.";
    }
    return "Limited data available.";
  }

  private String buildReportFilename(ReportData data, String postcode) {
    String firstLine = firstLineFromEpc(data.epcData());
    if (firstLine == null || firstLine.isBlank()) {
      firstLine = firstLineFromPriceHistory(data.priceHistory());
    }
    if (firstLine == null || firstLine.isBlank()) {
      firstLine = "Property";
    }

    String rawName = "Property report - " + firstLine.trim() + " " + postcode;
    return safeFilename(rawName) + ".pdf";
  }

  private String firstLineFromEpc(Map<String, Object> epcData) {
    if (epcData == null) {
      return null;
    }
    Object address1 = epcData.get("address1");
    if (address1 instanceof String && !((String) address1).isBlank()) {
      return ((String) address1).trim();
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
    if (value instanceof String valueString && !valueString.isBlank()) {
      builder.append(valueString.trim()).append(suffix);
    }
  }

  private String safeFilename(String rawName) {
    return rawName
        .replaceAll("[\\/:*?\"<>|]", "-")
        .replaceAll("\\s{2,}", " ")
        .trim();
  }

  private String trimToNull(String value) {
    String text = asString(value);
    if (text == null || text.isBlank()) {
      return null;
    }
    return text.trim();
  }

  public record ReportInputs(
      String uprn,
      String postcode,
      String paon,
      boolean includeEpc,
      boolean includePriceHistory,
      boolean includeFloodRisk,
      Double latitude,
      Double longitude) {}

  private record ReportData(
      Map<String, Object> epcData, List<Map<String, Object>> priceHistory, Map<String, Object> floodRiskData) {}

  public record ReportPreviewResponse(
      ReportInputs requested,
      boolean epcAvailable,
      boolean priceHistoryAvailable,
      boolean floodRiskAvailable,
      int availableSectionCount,
      String summary) {}

  public record ReportPrepareResponse(String downloadToken, String downloadUrl) {}
}
