package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class EpcService {
  private static final Logger log = LoggerFactory.getLogger(EpcService.class);

  private final RestTemplate restTemplate;
  private final EpcCertificateMapper certificateMapper;
  private final String endpoint;
  private final String apiKey;

  public EpcService(
      RestTemplate restTemplate,
      ObjectMapper objectMapper,
      @Value("${app.epc.endpoint}") String endpoint,
      @Value("${app.epc.api-key:}") String apiKey) {
    this.restTemplate = restTemplate;
    this.certificateMapper = new EpcCertificateMapper(objectMapper);
    this.endpoint = endpoint.replaceAll("/+$", "");
    this.apiKey = apiKey;
  }

  public Optional<Map<String, Object>> fetchByUprn(String uprn) {
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("EPC lookup rejected reason=api_key_missing uprn={}", redactIdentifier(uprn));
      throw new IllegalStateException(
          "EPC API key missing. Set APP_EPC_API_KEY to the bearer token from My account on the EPC service.");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    headers.setBearerAuth(apiKey.trim());
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    try {
      // The provider orders domestic search results by registration_date DESC.
      // Search returns summaries only; the certificate endpoint supplies the report fields.
      JsonNode search = restTemplate.exchange(
          endpoint + "/domestic/search?uprn={uprn}&page_size=1",
          HttpMethod.GET, entity, JsonNode.class, uprn.trim()).getBody();
      if (search == null || !search.path("data").isArray()) {
        throw new RestClientException("EPC search returned an invalid data envelope.");
      }
      JsonNode certificates = search.get("data");
      if (certificates.isEmpty()) {
        log.info("EPC lookup completed uprn={} found=false", redactIdentifier(uprn));
        return Optional.empty();
      }
      JsonNode summary = certificates.get(0);
      JsonNode certificateNumber = summary.path("certificateNumber");
      if (!certificateNumber.isTextual() || certificateNumber.asText().isBlank()) {
        throw new RestClientException("EPC search returned a certificate without a certificate number.");
      }
      JsonNode certificate = restTemplate.exchange(
          endpoint + "/certificate?certificate_number={certificateNumber}",
          HttpMethod.GET, entity, JsonNode.class, certificateNumber.asText()).getBody();
      if (certificate == null || !certificate.path("data").isObject()
          || certificate.path("data").isEmpty()) {
        throw new RestClientException("EPC certificate returned an invalid data envelope.");
      }
      Map<String, Object> result = certificateMapper.map(certificate.get("data"), summary);
      log.info("EPC lookup completed uprn={} found=true", redactIdentifier(uprn));
      return Optional.of(result);
    } catch (HttpClientErrorException.NotFound notFound) {
      log.info("EPC lookup completed uprn={} found=false", redactIdentifier(uprn));
      return Optional.empty();
    } catch (HttpClientErrorException.Unauthorized
        | HttpClientErrorException.Forbidden authError) {
      log.error(
          "EPC lookup failed reason=auth uprn={} upstreamStatus={}",
          redactIdentifier(uprn),
          authError.getStatusCode().value());
      throw new IllegalStateException(
          "EPC API key is invalid or lacks access. Update APP_EPC_API_KEY with the bearer token from My account on the EPC service.",
          authError);
    } catch (RestClientException upstreamError) {
      log.error("EPC lookup failed uprn={}", redactIdentifier(uprn), upstreamError);
      throw upstreamError;
    }
  }

  private String redactIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return "missing";
    }
    String trimmed = value.trim();
    if (trimmed.length() <= 4) {
      return "****";
    }
    return "****" + trimmed.substring(trimmed.length() - 4);
  }
}
