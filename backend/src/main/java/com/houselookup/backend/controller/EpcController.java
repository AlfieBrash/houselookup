package com.houselookup.backend.controller;

import com.houselookup.backend.service.EpcService;
import com.houselookup.backend.service.PdfGenerationService;
import java.util.Map;
import java.util.Optional;
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
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }

    Optional<Map<String, Object>> result = epcService.fetchByUprn(uprn.trim());
    return result.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No EPC found."));
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> fetchEpcPdf(@RequestParam String uprn) {
    if (uprn == null || uprn.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }

    Optional<Map<String, Object>> result = epcService.fetchByUprn(uprn.trim());
    if (result.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No EPC found.");
    }

    try {
      byte[] pdfBytes = pdfGenerationService.generateEpcPdf(result.get());

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "epc-" + uprn.trim() + ".pdf");
      headers.setContentLength(pdfBytes.length);

      return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage());
    }
  }
}
