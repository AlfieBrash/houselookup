package com.houselookup.backend.controller;

import com.houselookup.backend.model.User;
import com.houselookup.backend.service.AuthService;
import com.houselookup.backend.service.CreditService;
import com.houselookup.backend.service.PaymentService;
import com.stripe.exception.EventDataObjectDeserializationException;
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

  @Value("${app.payment.dev-topup-enabled:true}")
  private boolean devTopupEnabled;

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
    return new CheckoutResponse(session.sessionId(), session.checkoutUrl(), session.credits(), session.amountCents());
  }

  @PostMapping("/dev-topup")
  public CreditResponse createDevTopup(@RequestBody CheckoutRequest request, HttpServletRequest httpRequest) {
    if (!devTopupEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Developer top-up is disabled.");
    }
    if (request == null || !paymentService.isValidCreditPack(request.credits())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credit pack.");
    }

    User user = authService.requireUser(httpRequest);
    creditService.addCredits(user.getId(), request.credits());
    return new CreditResponse(creditService.getBalance(user.getId()));
  }

  @PostMapping("/webhook/stripe")
  public ResponseEntity<Void> stripeWebhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String signatureHeader) {
    try {
      Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
      String sessionId = extractCheckoutSessionId(event);

      if (sessionId == null) {
        return ResponseEntity.ok().build();
      }

      if ("checkout.session.completed".equals(event.getType())) {
        paymentService.handleCheckoutSessionCompleted(sessionId);
      } else if ("checkout.session.expired".equals(event.getType())
          || "checkout.session.async_payment_failed".equals(event.getType())) {
        paymentService.handleCheckoutSessionFailed(sessionId);
      }

      return ResponseEntity.ok().build();
    } catch (SignatureVerificationException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature.");
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook payload.", e);
    }
  }

  private String extractCheckoutSessionId(Event event) {
    if (event == null) {
      return null;
    }

    Object payloadData;
    try {
      payloadData = event.getDataObjectDeserializer().deserializeUnsafe();
    } catch (EventDataObjectDeserializationException e) {
      return null;
    }

    if (!(payloadData instanceof Session)) {
      return null;
    }
    Session session = (Session) payloadData;
    return session.getId();
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
