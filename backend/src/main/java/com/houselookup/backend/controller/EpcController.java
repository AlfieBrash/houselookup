package com.houselookup.backend.controller;

import com.houselookup.backend.service.EpcService;
import com.houselookup.backend.service.PdfGenerationService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/epc")
public class EpcController {
  private static final Logger log = LoggerFactory.getLogger(EpcController.class);

  private final EpcService epcService;
  private final PdfGenerationService pdfGenerationService;

  public EpcController(EpcService epcService, PdfGenerationService pdfGenerationService) {
    this.epcService = epcService;
    this.pdfGenerationService = pdfGenerationService;
  }

  @GetMapping("")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> fetchEpc(@RequestParam String uprn) {
    if (uprn == null || uprn.trim().isEmpty()) {
      log.warn("EPC request rejected reason=missing_uprn");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }

    Optional<Map<String, Object>> result = epcService.fetchByUprn(uprn.trim());
    log.info("EPC request completed uprn={} found={}", redactIdentifier(uprn), result.isPresent());
    return result.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No EPC found."));
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> fetchEpcPdf(@RequestParam String uprn) {
    if (uprn == null || uprn.trim().isEmpty()) {
      log.warn("EPC PDF request rejected reason=missing_uprn");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }

    Optional<Map<String, Object>> result = epcService.fetchByUprn(uprn.trim());
    if (result.isEmpty()) {
      log.info("EPC PDF request completed uprn={} found=false", redactIdentifier(uprn));
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No EPC found.");
    }

    try {
      byte[] pdfBytes = pdfGenerationService.generateEpcPdf(result.get());
      log.info("EPC PDF generated uprn={} bytes={}", redactIdentifier(uprn), pdfBytes.length);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "epc-" + uprn.trim() + ".pdf");
      headers.setContentLength(pdfBytes.length);

      return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    } catch (Exception e) {
      log.error("EPC PDF generation failed uprn={}", redactIdentifier(uprn), e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage(), e);
    }
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
}
