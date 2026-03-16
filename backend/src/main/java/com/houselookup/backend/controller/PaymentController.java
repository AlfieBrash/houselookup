package com.houselookup.backend.controller;

import com.houselookup.backend.model.User;
import com.houselookup.backend.service.AuthService;
import com.houselookup.backend.service.CreditService;
import com.houselookup.backend.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService paymentService;
  private final AuthService authService;
  private final CreditService creditService;

  @Value("${app.payment.webhook.secret:}")
  private String webhookSecret;

  public PaymentController(PaymentService paymentService, AuthService authService, CreditService creditService) {
    this.paymentService = paymentService;
    this.authService = authService;
    this.creditService = creditService;
  }

  @GetMapping("/pricing")
  public List<PaymentService.CreditPack> pricing() {
    return paymentService.getCreditPacks();
  }

  @PostMapping("/checkout")
  public CheckoutResponse createCheckout(@RequestBody CheckoutRequest request, HttpServletRequest httpRequest) {
    if (request == null || request.credits() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid checkout request.");
    }
    User user = authService.requireUser(httpRequest);
    PaymentService.CheckoutSession session = paymentService.createCheckout(user.getId(), request.credits());
    return new CheckoutResponse(session.checkoutSessionId(), session.checkoutUrl(), session.credits(), session.amountCents());
  }

  @PostMapping("/webhook/stripe")
  public ResponseEntity<Void> stripeWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String signatureHeader) {
    Event event;

    try {
      event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature.");
    }

    if ("checkout.session.completed".equals(event.getType())) {
      Session session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
      if (session != null && session.getId() != null) {
        paymentService.handleCheckoutSessionCompleted(session.getId());
      }
      return ResponseEntity.ok().build();
    }

    if ("checkout.session.expired".equals(event.getType())
        || "checkout.session.async_payment_failed".equals(event.getType())) {
      Session session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
      if (session != null && session.getId() != null) {
        paymentService.handleCheckoutSessionFailed(session.getId());
      }
    }

    return ResponseEntity.ok().build();
  }

  @GetMapping("/credits")
  public CreditResponse credits(HttpServletRequest request) {
    User user = authService.requireUser(request);
    int credits = creditService.getBalance(user.getId());
    return new CreditResponse(credits);
  }

  public record CheckoutRequest(int credits) {}

  public record CheckoutResponse(String sessionId, String checkoutUrl, int credits, long amountCents) {}

  public record CreditResponse(int credits) {}
}
