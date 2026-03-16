package com.houselookup.backend.controller;

import com.houselookup.backend.model.User;
import com.houselookup.backend.service.AuthService;
import com.houselookup.backend.service.CreditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final CreditService creditService;

  public AuthController(AuthService authService, CreditService creditService) {
    this.authService = authService;
    this.creditService = creditService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthMeResponse register(
      @RequestBody AuthRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    User user = authService.register(request.email(), request.password(), httpRequest, response);
    int balance = creditService.getBalance(user.getId());
    return new AuthMeResponse(user.getEmail(), balance);
  }

  @PostMapping("/login")
  public AuthMeResponse login(
      @RequestBody AuthRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    User user = authService.login(request.email(), request.password(), httpRequest, response);
    int balance = creditService.getBalance(user.getId());
    return new AuthMeResponse(user.getEmail(), balance);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
  }

  @GetMapping("/me")
  public AuthMeResponse me(HttpServletRequest request) {
    User user = authService.requireUser(request);
    int balance = creditService.getBalance(user.getId());
    return new AuthMeResponse(user.getEmail(), balance);
  }

  public record AuthRequest(String email, String password) {}

  public record AuthMeResponse(String email, int credits) {}
}
