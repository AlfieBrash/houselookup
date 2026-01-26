package com.houselookup.backend.controller;

import com.houselookup.backend.service.EpcService;
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
  private final ReportPdfService reportPdfService;

  public ReportController(
      EpcService epcService,
      LandRegistryService landRegistryService,
      ReportPdfService reportPdfService) {
    this.epcService = epcService;
    this.landRegistryService = landRegistryService;
    this.reportPdfService = reportPdfService;
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> generateReport(
      @RequestParam String uprn,
      @RequestParam String postcode,
      @RequestParam(required = false) String paon,
      @RequestParam(defaultValue = "true") boolean includeEpc,
      @RequestParam(defaultValue = "true") boolean includePriceHistory) {

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

    // Check we have at least some data
    if (epcData == null && (priceHistory == null || priceHistory.isEmpty())) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No data found for this property.");
    }

    try {
      byte[] pdfBytes = reportPdfService.generateReport(epcData, priceHistory, postcode);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "property-report-" + uprn.trim() + ".pdf");
      headers.setContentLength(pdfBytes.length);

      return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage());
    }
  }
}
