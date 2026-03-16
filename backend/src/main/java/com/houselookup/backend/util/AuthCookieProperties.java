package com.houselookup.backend.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthCookieProperties {
  private String cookieName = "pp_session";
  private int cookieTtlDays = 14;
  private boolean cookieSecure = false;
  private String cookieSameSite = "Lax";

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }

  public int getCookieTtlDays() {
    return cookieTtlDays;
  }

  public void setCookieTtlDays(int cookieTtlDays) {
    this.cookieTtlDays = cookieTtlDays;
  }

  public boolean isCookieSecure() {
    return cookieSecure;
  }

  public void setCookieSecure(boolean cookieSecure) {
    this.cookieSecure = cookieSecure;
  }

  public String getCookieSameSite() {
    return cookieSameSite;
  }

  public void setCookieSameSite(String cookieSameSite) {
    this.cookieSameSite = cookieSameSite;
  }
}
