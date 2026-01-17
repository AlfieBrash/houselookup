package com.houselookup.backend.controller;

import com.houselookup.backend.model.Address;
import com.houselookup.backend.service.OsPlacesService;
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
  private final OsPlacesService osPlacesService;

  public AddressController(OsPlacesService osPlacesService) {
    this.osPlacesService = osPlacesService;
  }

  @GetMapping("/addresses")
  @ResponseStatus(HttpStatus.OK)
  public List<Address> lookupAddresses(@RequestParam String postcode) {
    if (postcode == null || postcode.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Postcode is required.");
    }
    try {
      return osPlacesService.lookupAddresses(postcode);
    } catch (IllegalArgumentException badRequest) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badRequest.getMessage(), badRequest);
    } catch (IllegalStateException serverError) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, serverError.getMessage(), serverError);
    }
  }
}
