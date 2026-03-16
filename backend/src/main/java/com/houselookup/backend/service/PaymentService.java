package com.houselookup.backend.service;

import com.houselookup.backend.model.PaymentTransaction;
import com.houselookup.backend.model.User;
import com.houselookup.backend.repository.PaymentTransactionRepository;
import com.houselookup.backend.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

  private final PaymentTransactionRepository transactionRepository;
  private final UserRepository userRepository;
  private final CreditService creditService;

  @Value("${app.payment.package-config:1:199,5:899,10:1690}")
  private String packageConfig;

  @Value("${app.payment.currency:GBP}")
  private String currency;

  @Value("${app.payment.frontend-url:http://localhost:8080}")
  private String frontendUrl;

  @Value("${app.payment.success-url:${app.payment.frontend-url}/checkout/success}")
  private String successUrl;

  @Value("${app.payment.cancel-url:${app.payment.frontend-url}/checkout/cancel}")
  private String cancelUrl;

  @Value("${app.payment.stripe.secret-key:}")
  private String stripeSecretKey;

  private final Map<Integer, Long> availablePacks = new LinkedHashMap<>();

  public PaymentService(
      PaymentTransactionRepository transactionRepository,
      UserRepository userRepository,
      CreditService creditService) {
    this.transactionRepository = transactionRepository;
    this.userRepository = userRepository;
    this.creditService = creditService;
  }

  @PostConstruct
  void init() {
    parsePackages();
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      return;
    }
    Stripe.apiKey = stripeSecretKey;
  }

  public List<CreditPack> getCreditPacks() {
    List<CreditPack> packs = new ArrayList<>();
    availablePacks.forEach((credits, cents) -> packs.add(new CreditPack(credits, cents, currency)));
    return packs;
  }

  @Transactional
  public CheckoutSession createCheckout(long userId, int credits) {
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured.");
    }

    if (credits <= 0 || !availablePacks.containsKey(credits)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credit pack.");
    }

    User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    long amountCents = availablePacks.get(credits);

    SessionCreateParams.LineItem.PriceData.ProductData productData =
        SessionCreateParams.LineItem.PriceData.ProductData.builder()
            .setName("Property report credits")
            .setDescription(credits + " report credits")
            .build();

    SessionCreateParams.LineItem.PriceData priceData =
        SessionCreateParams.LineItem.PriceData.builder()
            .setCurrency(currency.toLowerCase(Locale.ROOT))
            .setUnitAmount(amountCents)
            .setProductData(productData)
            .build();

    SessionCreateParams.LineItem lineItem =
        SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(priceData).build();

    SessionCreateParams params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(resolveRedirectUrl(successUrl))
            .setCancelUrl(resolveRedirectUrl(cancelUrl))
            .addLineItem(lineItem)
            .putMetadata("userId", String.valueOf(user.getId()))
            .putMetadata("credits", String.valueOf(credits))
            .build();

    try {
      Session session = Session.create(params);
      String providerSessionId = session.getId();

      transactionRepository
          .findByProviderSessionId(providerSessionId)
          .ifPresent(
              existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout already exists.");
              });

      PaymentTransaction tx = new PaymentTransaction();
      tx.setUser(user);
      tx.setProvider("stripe");
      tx.setProviderSessionId(providerSessionId);
      tx.setPackageSize(credits);
      tx.setAmountCents(amountCents);
      tx.setCurrency(currency);
      tx.setStatus("CREATED");
      tx.setIdempotencyKey(UUID.randomUUID().toString());
      transactionRepository.save(tx);

      return new CheckoutSession(providerSessionId, session.getUrl(), credits, amountCents);
    } catch (StripeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create checkout session.", e);
    }
  }

  @Transactional
  public void handleCheckoutSessionCompleted(String sessionId) {
    PaymentTransaction tx =
        transactionRepository
            .findByProviderSessionId(sessionId)
            .orElse(null);

    if (tx == null) {
      return;
    }

    if ("SUCCEEDED".equalsIgnoreCase(tx.getStatus())) {
      return;
    }

    creditService.addCredits(tx.getUser().getId(), tx.getPackageSize());
    tx.setStatus("SUCCEEDED");
    transactionRepository.save(tx);
  }

  @Transactional
  public void handleCheckoutSessionFailed(String sessionId) {
    transactionRepository
        .findByProviderSessionId(sessionId)
        .ifPresent(
            tx -> {
              if (!"SUCCEEDED".equalsIgnoreCase(tx.getStatus())) {
                tx.setStatus("FAILED");
                transactionRepository.save(tx);
              }
            });
  }

  private String resolveRedirectUrl(String configuredUrl) {
    if (configuredUrl == null || configuredUrl.isBlank()) {
      return frontendUrl + "/";
    }
    if (configuredUrl.contains("{CHECKOUT_SESSION_ID}")) {
      return configuredUrl;
    }
    if (configuredUrl.contains("?") || configuredUrl.contains("#")) {
      return configuredUrl + "&session_id={CHECKOUT_SESSION_ID}";
    }
    return configuredUrl + "?session_id={CHECKOUT_SESSION_ID}";
  }

  private void parsePackages() {
    availablePacks.clear();
    String[] entries = packageConfig.split(",");
    for (String entry : entries) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      String[] pair = entry.split(":", 2);
      if (pair.length != 2) {
        continue;
      }
      try {
        int credits = Integer.parseInt(pair[0].trim());
        long cents = Long.parseLong(pair[1].trim());
        if (credits > 0 && cents >= 0) {
          availablePacks.put(credits, cents);
        }
      } catch (NumberFormatException ignored) {
        // ignore malformed entries
      }
    }

    if (availablePacks.isEmpty()) {
      availablePacks.put(1, 199L);
      availablePacks.put(5, 899L);
      availablePacks.put(10, 1690L);
    }
  }

  public record CreditPack(int credits, long amountCents, String currency) {}

  public record CheckoutSession(String sessionId, String checkoutUrl, int credits, long amountCents) {}
}
