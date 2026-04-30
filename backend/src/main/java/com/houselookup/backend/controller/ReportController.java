package com.houselookup.backend.controller;

import com.houselookup.backend.model.ReportDownloadToken;
import com.houselookup.backend.model.User;
import com.houselookup.backend.service.AuthService;
import com.houselookup.backend.service.CreditService;
import com.houselookup.backend.service.EpcService;
import com.houselookup.backend.service.FloodRiskService;
import com.houselookup.backend.service.LandRegistryService;
import com.houselookup.backend.service.PoliceCrimeService;
import com.houselookup.backend.service.ReportPdfService;
import com.houselookup.backend.service.ReportTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(ReportController.class);

  private final EpcService epcService;
  private final LandRegistryService landRegistryService;
  private final FloodRiskService floodRiskService;
  private final PoliceCrimeService policeCrimeService;
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
      PoliceCrimeService policeCrimeService,
      ReportPdfService reportPdfService,
      AuthService authService,
      CreditService creditService,
      ReportTokenService tokenService) {
    this.epcService = epcService;
    this.landRegistryService = landRegistryService;
    this.floodRiskService = floodRiskService;
    this.policeCrimeService = policeCrimeService;
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
      @RequestParam(defaultValue = "false") boolean includeCrimeStats,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {

    if (!hasAnySelection(includeEpc, includePriceHistory, includeFloodRisk, includeCrimeStats)) {
      log.warn("Report preview rejected reason=no_sections_selected");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one data section to preview.");
    }

    ReportInputs inputs =
        normalizeInputs(
            uprn,
            postcode,
            paon,
            includeEpc,
            includePriceHistory,
            includeFloodRisk,
            includeCrimeStats,
            latitude,
            longitude);
    log.info(
        "Report preview requested uprn={} postcode={} includeEpc={} includePriceHistory={} includeFloodRisk={} includeCrimeStats={}",
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()),
        inputs.includeEpc(),
        inputs.includePriceHistory(),
        inputs.includeFloodRisk(),
        inputs.includeCrimeStats());
    ReportData data = fetchReportData(inputs);

    boolean epcAvailable = data.epcData() != null;
    boolean priceHistoryAvailable = isPriceHistoryAvailable(data.priceHistory());
    boolean floodRiskAvailable = isFloodRiskAvailable(data.floodRiskData());
    boolean crimeStatsAvailable = isCrimeStatsAvailable(data.crimeStatsData());

    int sections =
        (epcAvailable ? 1 : 0)
            + (priceHistoryAvailable ? 1 : 0)
            + (floodRiskAvailable ? 1 : 0)
            + (crimeStatsAvailable ? 1 : 0);

    ReportPreviewResponse response =
        new ReportPreviewResponse(
            inputs,
            epcAvailable,
            priceHistoryAvailable,
            floodRiskAvailable,
            crimeStatsAvailable,
            sections,
            estimateSummary(epcAvailable, priceHistoryAvailable, floodRiskAvailable, crimeStatsAvailable));
    log.info(
        "Report preview completed uprn={} postcode={} availableSectionCount={}",
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()),
        sections);
    return response;
  }

  @PostMapping("/prepare")
  public ReportPrepareResponse prepareDownload(
      @RequestParam String uprn,
      @RequestParam String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory,
      @RequestParam(defaultValue = "false") boolean includeFloodRisk,
      @RequestParam(defaultValue = "false") boolean includeCrimeStats,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      HttpServletRequest request) {

    User user = authService.requireUser(request);
    if (!hasAnySelection(includeEpc, includePriceHistory, includeFloodRisk, includeCrimeStats)) {
      log.warn("Report download prepare rejected userId={} reason=no_sections_selected", user.getId());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one data section to prepare.");
    }

    ReportInputs inputs =
        normalizeInputs(
            uprn,
            postcode,
            paon,
            includeEpc,
            includePriceHistory,
            includeFloodRisk,
            includeCrimeStats,
            latitude,
            longitude);
    log.info(
        "Report download prepare requested userId={} uprn={} postcode={} includeEpc={} includePriceHistory={} includeFloodRisk={} includeCrimeStats={}",
        user.getId(),
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()),
        inputs.includeEpc(),
        inputs.includePriceHistory(),
        inputs.includeFloodRisk(),
        inputs.includeCrimeStats());

    if (!creditService.consumeOne(user.getId())) {
      log.warn("Report download prepare rejected userId={} reason=insufficient_credits", user.getId());
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
      payload.put("includeCrimeStats", inputs.includeCrimeStats());
      if (inputs.latitude() != null) {
        payload.put("latitude", inputs.latitude());
      }
      if (inputs.longitude() != null) {
        payload.put("longitude", inputs.longitude());
      }

      String token =
          tokenService.createToken(user, payload);

      String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
      log.info(
          "Report download prepared userId={} uprn={} postcode={}",
          user.getId(),
          redactIdentifier(inputs.uprn()),
          redactPostcode(inputs.postcode()));
      return new ReportPrepareResponse(token, "/api/report/pdf?token=" + encoded);
    } catch (RuntimeException e) {
      creditService.addCredits(user.getId(), 1);
      log.error(
          "Report download prepare failed and credit was refunded userId={} uprn={} postcode={}",
          user.getId(),
          redactIdentifier(inputs.uprn()),
          redactPostcode(inputs.postcode()),
          e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not create download token.", e);
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
      @RequestParam(defaultValue = "false") boolean includeCrimeStats,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {

    if (token != null && !token.isBlank()) {
      log.info("Report PDF requested using download token");
      return downloadByToken(token);
    }

    if (!legacyDownloadEnabled) {
      log.warn("Report PDF legacy request rejected reason=legacy_download_disabled");
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token is required for report downloads.");
    }

    if (uprn == null || postcode == null) {
      log.warn("Report PDF legacy request rejected reason=missing_required_inputs");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN and postcode are required for legacy download.");
    }

    ReportInputs inputs =
        normalizeInputs(
            uprn,
            postcode,
            paon,
            includeEpc,
            includePriceHistory,
            includeFloodRisk,
            includeCrimeStats,
            latitude,
            longitude);
    return servePdf(inputs);
  }

  private ResponseEntity<byte[]> downloadByToken(String token) {
    ReportDownloadToken tokenRecord;
    try {
      tokenRecord = tokenService.claimToken(token);
    } catch (IllegalArgumentException e) {
      log.warn("Report PDF token download rejected reason={}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (IllegalStateException e) {
      log.warn("Report PDF token download rejected reason={}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }

    log.info(
        "Report PDF token download started tokenId={} userId={}",
        tokenRecord.getId(),
        tokenRecord.getUser().getId());

    try {
      ReportInputs inputs = readInputs(tokenRecord.getRequestPayloadJson());
      ReportData data = fetchReportData(inputs);

      if (hasNoAvailableData(data)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for this property.");
      }

      byte[] bytes = buildPdf(inputs, data);
      tokenService.markCompleted(tokenRecord);
      log.info(
          "Report PDF token download completed tokenId={} userId={} bytes={}",
          tokenRecord.getId(),
          tokenRecord.getUser().getId(),
          bytes.length);
      return servePdfResponse(bytes, buildReportFilename(data, inputs.postcode()));
    } catch (ResponseStatusException ex) {
      log.warn(
          "Report PDF token download failed tokenId={} userId={} status={} message={}",
          tokenRecord.getId(),
          tokenRecord.getUser().getId(),
          ex.getStatusCode().value(),
          ex.getReason());
      handleTokenDownloadFailure(tokenRecord, ex.getMessage());
      throw ex;
    } catch (Exception ex) {
      log.error(
          "Report PDF token download failed tokenId={} userId={}",
          tokenRecord.getId(),
          tokenRecord.getUser().getId(),
          ex);
      handleTokenDownloadFailure(tokenRecord, "Failed to generate report.");
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report.", ex);
    }
  }

  private void handleTokenDownloadFailure(ReportDownloadToken tokenRecord, String message) {
    tokenService.markFailed(tokenRecord, message == null ? "Failed to generate report." : message);
    if (tokenRecord.getUser() != null) {
      creditService.addCredits(tokenRecord.getUser().getId(), 1);
      log.info(
          "Report PDF token download credit refunded tokenId={} userId={}",
          tokenRecord.getId(),
          tokenRecord.getUser().getId());
    }
  }

  private ResponseEntity<byte[]> servePdf(ReportInputs inputs) {
    log.info(
        "Report PDF legacy download started uprn={} postcode={}",
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()));
    ReportData data = fetchReportData(inputs);
    if (hasNoAvailableData(data)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for this property.");
    }
    byte[] bytes = buildPdf(inputs, data);
    log.info(
        "Report PDF legacy download completed uprn={} postcode={} bytes={}",
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()),
        bytes.length);
    return servePdfResponse(bytes, buildReportFilename(data, inputs.postcode()));
  }

  private byte[] buildPdf(ReportInputs inputs, ReportData data) {
    try {
      return reportPdfService.generateReport(
          data.epcData(),
          data.priceHistory(),
          data.floodRiskData(),
          data.crimeStatsData(),
          inputs.postcode());
    } catch (Exception e) {
      log.error(
          "Report PDF generation failed uprn={} postcode={}",
          redactIdentifier(inputs.uprn()),
          redactPostcode(inputs.postcode()),
          e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage(), e);
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
      boolean includeCrimeStats,
      Double latitude,
      Double longitude) {
    if (uprn == null || uprn.trim().isEmpty()) {
      log.warn("Report request rejected reason=missing_uprn");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }
    if (postcode == null || postcode.trim().isEmpty()) {
      log.warn("Report request rejected reason=missing_postcode");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postcode is required.");
    }
    return new ReportInputs(
        uprn.trim(),
        postcode.trim(),
        trimToNull(paon),
        includeEpc,
        includePriceHistory,
        includeFloodRisk,
        includeCrimeStats,
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

    Map<String, Object> crimeStatsData = null;
    if (inputs.includeCrimeStats()) {
      crimeStatsData = policeCrimeService.fetchAssessment(inputs.latitude(), inputs.longitude());
    }

    log.info(
        "Report data fetched uprn={} postcode={} epcAvailable={} priceHistoryCount={} floodRiskAvailable={} crimeStatsAvailable={}",
        redactIdentifier(inputs.uprn()),
        redactPostcode(inputs.postcode()),
        epcData != null,
        priceHistory == null ? 0 : priceHistory.size(),
        isFloodRiskAvailable(floodRiskData),
        isCrimeStatsAvailable(crimeStatsData));
    return new ReportData(epcData, priceHistory, floodRiskData, crimeStatsData);
  }

  private boolean hasAnySelection(
      boolean includeEpc,
      boolean includePriceHistory,
      boolean includeFloodRisk,
      boolean includeCrimeStats) {
    return includeEpc || includePriceHistory || includeFloodRisk || includeCrimeStats;
  }

  private boolean isPriceHistoryAvailable(List<Map<String, Object>> priceHistory) {
    return priceHistory != null && !priceHistory.isEmpty();
  }

  private boolean isFloodRiskAvailable(Map<String, Object> floodRiskData) {
    return isAvailableFlagTrue(floodRiskData);
  }

  private boolean isCrimeStatsAvailable(Map<String, Object> crimeStatsData) {
    return isAvailableFlagTrue(crimeStatsData);
  }

  private boolean isAvailableFlagTrue(Map<String, Object> data) {
    if (data == null) {
      return false;
    }
    Object available = data.get("available");
    if (available instanceof Boolean) {
      return (Boolean) available;
    }
    return false;
  }

  private boolean hasNoAvailableData(ReportData data) {
    return data.epcData() == null
        && !isPriceHistoryAvailable(data.priceHistory())
        && !isFloodRiskAvailable(data.floodRiskData())
        && !isCrimeStatsAvailable(data.crimeStatsData());
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
        parseBoolean(payload.get("includeCrimeStats"), false),
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

  private String estimateSummary(
      boolean epcAvailable,
      boolean priceHistoryAvailable,
      boolean floodRiskAvailable,
      boolean crimeStatsAvailable) {
    List<String> availableSections = new java.util.ArrayList<>();
    if (epcAvailable) {
      availableSections.add("EPC");
    }
    if (priceHistoryAvailable) {
      availableSections.add("price history");
    }
    if (floodRiskAvailable) {
      availableSections.add("flood risk");
    }
    if (crimeStatsAvailable) {
      availableSections.add("crime data");
    }

    if (availableSections.isEmpty()) {
      return "Limited data available.";
    }
    if (availableSections.size() == 1) {
      return availableSections.get(0).substring(0, 1).toUpperCase()
          + availableSections.get(0).substring(1)
          + " available.";
    }
    if (availableSections.size() == 2) {
      return availableSections.get(0) + " and " + availableSections.get(1) + " available.";
    }

    StringBuilder summary = new StringBuilder();
    for (int i = 0; i < availableSections.size(); i++) {
      if (i > 0) {
        summary.append(i == availableSections.size() - 1 ? " and " : ", ");
      }
      summary.append(availableSections.get(i));
    }
    summary.append(" available.");
    summary.setCharAt(0, Character.toUpperCase(summary.charAt(0)));
    return summary.toString();
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

  private String redactIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return "missing";
    }
    String trimmed = value.trim();
    if (trimmed.length() <= 4) {
      return "****";
    }
    return "****" + trimmed.substring(trimmed.length() - 4);
  }

  private String redactPostcode(String postcode) {
    if (postcode == null || postcode.isBlank()) {
      return "missing";
    }
    String normalised = postcode.replaceAll("\\s+", "").toUpperCase();
    if (normalised.length() <= 3) {
      return "***";
    }
    return normalised.substring(0, Math.min(3, normalised.length())) + "***";
  }

  public record ReportInputs(
      String uprn,
      String postcode,
      String paon,
      boolean includeEpc,
      boolean includePriceHistory,
      boolean includeFloodRisk,
      boolean includeCrimeStats,
      Double latitude,
      Double longitude) {}

  private record ReportData(
      Map<String, Object> epcData,
      List<Map<String, Object>> priceHistory,
      Map<String, Object> floodRiskData,
      Map<String, Object> crimeStatsData) {}

  public record ReportPreviewResponse(
      ReportInputs requested,
      boolean epcAvailable,
      boolean priceHistoryAvailable,
      boolean floodRiskAvailable,
      boolean crimeStatsAvailable,
      int availableSectionCount,
      String summary) {}

  public record ReportPrepareResponse(String downloadToken, String downloadUrl) {}
}
