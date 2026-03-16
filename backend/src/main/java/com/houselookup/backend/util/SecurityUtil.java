package com.houselookup.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityUtil {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private SecurityUtil() {}

  public static String generateSecureToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return BASE64_URL.encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return BASE64_URL.encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash token", e);
    }
  }
}
