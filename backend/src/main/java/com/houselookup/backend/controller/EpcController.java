package com.houselookup.backend.controller;

import com.houselookup.backend.service.EpcService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class EpcController {
  private final EpcService epcService;

  public EpcController(EpcService epcService) {
    this.epcService = epcService;
  }

  @GetMapping("/epc")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> fetchEpc(@RequestParam String uprn) {
    if (uprn == null || uprn.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPRN is required.");
    }

    Optional<Map<String, Object>> result = epcService.fetchByUprn(uprn.trim());
    return result.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No EPC found."));
  }
}
