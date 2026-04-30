package com.houselookup.backend.controller;

import com.houselookup.backend.model.Address;
import com.houselookup.backend.service.OsPlacesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AddressController {
  private static final Logger log = LoggerFactory.getLogger(AddressController.class);

  private final OsPlacesService osPlacesService;

  public AddressController(OsPlacesService osPlacesService) {
    this.osPlacesService = osPlacesService;
  }

  @GetMapping("/addresses")
  @ResponseStatus(HttpStatus.OK)
  public List<Address> lookupAddresses(@RequestParam String postcode) {
    if (postcode == null || postcode.trim().isEmpty()) {
      log.warn("Address lookup rejected: empty postcode");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postcode is required.");
    }
    try {
      return osPlacesService.lookupAddresses(postcode);
    } catch (ResponseStatusException upstreamError) {
      throw upstreamError;
    } catch (IllegalArgumentException badRequest) {
      log.warn("Address lookup bad request postcode={}", redactPostcode(postcode), badRequest);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badRequest.getMessage(), badRequest);
    } catch (IllegalStateException serverError) {
      log.error("Address lookup service error postcode={}", redactPostcode(postcode), serverError);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, serverError.getMessage(), serverError);
    } catch (Exception unexpected) {
      log.error("Unexpected error looking up addresses postcode={}", redactPostcode(postcode), unexpected);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Address lookup failed.", unexpected);
    }
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
}
