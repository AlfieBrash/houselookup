package com.houselookup.backend.service;

import com.houselookup.backend.model.PaymentTransaction;
import com.houselookup.backend.model.User;
import com.houselookup.backend.repository.PaymentTransactionRepository;
import com.houselookup.backend.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private static final String STATUS_CREATED = "CREATED";
  private static final String STATUS_SUCCEEDED = "SUCCEEDED";
  private static final String STRIPE_PROVIDER = "stripe";

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
    log.info("Payment packs configured packs={} currency={}", availablePacks, currency);
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      log.warn("Stripe checkout is not configured because STRIPE_SECRET_KEY is missing");
      return;
    }
    Stripe.apiKey = stripeSecretKey;
    log.info(
        "Stripe checkout configured frontendUrl={} successUrl={} cancelUrl={}",
        frontendUrl,
        successUrl,
        cancelUrl);
  }

  public List<CreditPack> getCreditPacks() {
    List<CreditPack> packs = new ArrayList<>();
    availablePacks.forEach((credits, cents) -> packs.add(new CreditPack(credits, cents, currency)));
    return packs;
  }

  public boolean isValidCreditPack(int credits) {
    return credits > 0 && availablePacks.containsKey(credits);
  }

  @Transactional
  public CheckoutSession createCheckout(long userId, int credits) {
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      log.warn("Checkout rejected userId={} credits={} reason=stripe_not_configured", userId, credits);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured.");
    }

    if (credits <= 0 || !availablePacks.containsKey(credits)) {
      log.warn("Checkout rejected userId={} credits={} reason=invalid_credit_pack", userId, credits);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credit pack.");
    }

    User user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      log.warn("Checkout rejected userId={} credits={} reason=user_not_found", userId, credits);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
    }
    long amountCents = availablePacks.get(credits);
    log.info(
        "Creating Stripe checkout userId={} credits={} amountCents={} currency={}",
        userId,
        credits,
        amountCents,
        currency);

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
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setSuccessUrl(resolveRedirectUrl(successUrl))
            .setCancelUrl(resolveRedirectUrl(cancelUrl))
            .setClientReferenceId(String.valueOf(user.getId()))
            .setCustomerEmail(user.getEmail())
            .addLineItem(lineItem)
            .putMetadata("userId", String.valueOf(user.getId()))
            .putMetadata("credits", String.valueOf(credits))
            .build();

    String idempotencyKey = UUID.randomUUID().toString();

    try {
      RequestOptions requestOptions =
          RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
      Session session = Session.create(params, requestOptions);
      String providerSessionId = session.getId();

      transactionRepository
          .findByProviderSessionId(providerSessionId)
          .ifPresent(
              existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout already exists.");
              });

      PaymentTransaction tx = new PaymentTransaction();
      tx.setUser(user);
      tx.setProvider(STRIPE_PROVIDER);
      tx.setProviderSessionId(providerSessionId);
      tx.setPackageSize(credits);
      tx.setAmountCents(amountCents);
      tx.setCurrency(currency);
      tx.setStatus(STATUS_CREATED);
      tx.setIdempotencyKey(idempotencyKey);
      transactionRepository.save(tx);

      log.info(
          "Stripe checkout created userId={} transactionId={} providerSessionId={} credits={} amountCents={}",
          userId,
          tx.getId(),
          providerSessionId,
          credits,
          amountCents);
      return new CheckoutSession(providerSessionId, session.getUrl(), credits, amountCents);
    } catch (StripeException e) {
      log.error(
          "Stripe checkout creation failed userId={} credits={} amountCents={} stripeStatus={} stripeCode={} stripeRequestId={} message={}",
          userId,
          credits,
          amountCents,
          e.getStatusCode(),
          e.getCode(),
          e.getRequestId(),
          e.getMessage(),
          e);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create checkout session.", e);
    }
  }

  @Transactional
  public void handleCheckoutSessionCompleted(String sessionId) {
    log.info("Handling Stripe checkout completion providerSessionId={}", sessionId);
    PaymentTransaction tx =
        transactionRepository
            .findForUpdateByProviderSessionId(sessionId)
            .orElse(null);

    if (tx == null) {
      log.warn("Stripe checkout completion ignored reason=transaction_not_found providerSessionId={}", sessionId);
      return;
    }

    if (STATUS_SUCCEEDED.equalsIgnoreCase(tx.getStatus())) {
      log.info(
          "Stripe checkout completion ignored reason=already_succeeded transactionId={} providerSessionId={}",
          tx.getId(),
          sessionId);
      return;
    }

    Session stripeSession = retrieveStripeSession(sessionId);
    if (!isPaidCheckoutSession(stripeSession)) {
      log.warn(
          "Stripe checkout completion ignored reason=session_not_paid transactionId={} providerSessionId={} stripeStatus={} stripePaymentStatus={}",
          tx.getId(),
          sessionId,
          stripeSession == null ? null : stripeSession.getStatus(),
          stripeSession == null ? null : stripeSession.getPaymentStatus());
      return;
    }
    validateStripeSessionMatchesTransaction(stripeSession, tx);

    creditService.addCredits(tx.getUser().getId(), tx.getPackageSize());
    tx.setStatus(STATUS_SUCCEEDED);
    transactionRepository.save(tx);
    log.info(
        "Stripe checkout completed transactionId={} userId={} providerSessionId={} credits={}",
        tx.getId(),
        tx.getUser().getId(),
        sessionId,
        tx.getPackageSize());
  }

  @Transactional
  public void handleCheckoutSessionFailed(String sessionId) {
    transactionRepository
        .findByProviderSessionId(sessionId)
        .ifPresent(
            tx -> {
              if (!STATUS_SUCCEEDED.equalsIgnoreCase(tx.getStatus())) {
                tx.setStatus("FAILED");
                transactionRepository.save(tx);
                log.info(
                    "Stripe checkout marked failed transactionId={} userId={} providerSessionId={}",
                    tx.getId(),
                    tx.getUser().getId(),
                    sessionId);
              } else {
                log.info(
                    "Stripe checkout failure ignored reason=already_succeeded transactionId={} providerSessionId={}",
                    tx.getId(),
                    sessionId);
              }
            });
  }

  private Session retrieveStripeSession(String sessionId) {
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      log.warn("Stripe checkout verification rejected providerSessionId={} reason=stripe_not_configured", sessionId);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured.");
    }

    try {
      return Session.retrieve(sessionId);
    } catch (StripeException e) {
      log.error(
          "Stripe checkout verification failed providerSessionId={} stripeStatus={} stripeCode={} stripeRequestId={} message={}",
          sessionId,
          e.getStatusCode(),
          e.getCode(),
          e.getRequestId(),
          e.getMessage(),
          e);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify Stripe checkout session.", e);
    }
  }

  private boolean isPaidCheckoutSession(Session stripeSession) {
    return stripeSession != null
        && "complete".equalsIgnoreCase(stripeSession.getStatus())
        && "paid".equalsIgnoreCase(stripeSession.getPaymentStatus());
  }

  private void validateStripeSessionMatchesTransaction(Session stripeSession, PaymentTransaction tx) {
    if (stripeSession.getAmountTotal() == null || !stripeSession.getAmountTotal().equals(tx.getAmountCents())) {
      log.warn(
          "Stripe checkout validation failed reason=amount_mismatch transactionId={} providerSessionId={} expectedAmountCents={} actualAmountCents={}",
          tx.getId(),
          tx.getProviderSessionId(),
          tx.getAmountCents(),
          stripeSession.getAmountTotal());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe checkout amount did not match.");
    }

    if (stripeSession.getCurrency() == null
        || !stripeSession.getCurrency().equalsIgnoreCase(tx.getCurrency())) {
      log.warn(
          "Stripe checkout validation failed reason=currency_mismatch transactionId={} providerSessionId={} expectedCurrency={} actualCurrency={}",
          tx.getId(),
          tx.getProviderSessionId(),
          tx.getCurrency(),
          stripeSession.getCurrency());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe checkout currency did not match.");
    }

    Map<String, String> metadata = stripeSession.getMetadata();
    if (metadata == null
        || !String.valueOf(tx.getUser().getId()).equals(metadata.get("userId"))
        || !String.valueOf(tx.getPackageSize()).equals(metadata.get("credits"))) {
      log.warn(
          "Stripe checkout validation failed reason=metadata_mismatch transactionId={} providerSessionId={}",
          tx.getId(),
          tx.getProviderSessionId());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe checkout metadata did not match.");
    }
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
        log.warn("Payment package config entry ignored reason=invalid_format entry={}", entry);
        continue;
      }
      try {
        int credits = Integer.parseInt(pair[0].trim());
        long cents = Long.parseLong(pair[1].trim());
        if (credits > 0 && cents >= 0) {
          availablePacks.put(credits, cents);
        } else {
          log.warn("Payment package config entry ignored reason=invalid_value entry={}", entry);
        }
      } catch (NumberFormatException ignored) {
        log.warn("Payment package config entry ignored reason=invalid_number entry={}", entry);
      }
    }

    if (availablePacks.isEmpty()) {
      availablePacks.put(1, 199L);
      availablePacks.put(5, 899L);
      availablePacks.put(10, 1690L);
      log.warn("Payment package config was empty or invalid; default packs were applied");
    }
  }

  public record CreditPack(int credits, long amountCents, String currency) {}

  public record CheckoutSession(String sessionId, String checkoutUrl, int credits, long amountCents) {}
}
