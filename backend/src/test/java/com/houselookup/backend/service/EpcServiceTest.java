package com.houselookup.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EpcServiceTest {
  private static final String BASE = "https://api.get-energy-performance-data.communities.gov.uk/api";
  private static final String UPRN = "100012345678";
  private static final String CERTIFICATE = "0000-1111-2222-3333-4444";
  private static final String SEARCH = """
      {
        "data": [{
          "certificateNumber": "0000-1111-2222-3333-4444",
          "uprn": 100012345678,
          "addressLine1": "10 Test Road",
          "postTown": "London",
          "postcode": "SW1A 1AA",
          "registrationDate": "2026-09-01",
          "currentEnergyEfficiencyBand": "D"
        }],
        "pagination": {"currentPage": 1, "totalPages": 1, "totalRecords": 1}
      }
      """;
  private static final String DETAIL = """
      {
        "data": {
          "certificate_number": "0000-1111-2222-3333-4444",
          "uprn": 100012345678,
          "address_line_1": "10 Test Road",
          "address_line_2": "",
          "post_town": "London",
          "postcode": "SW1A 1AA",
          "current_energy_efficiency_band": "C",
          "potential_energy_efficiency_band": "B",
          "energy_rating_current": 72,
          "energy_rating_potential": 85,
          "total_floor_area": 96.5,
          "property_type": 0,
          "built_form": 2,
          "tenure": 1,
          "heating_cost_current": {"value": 703, "currency": "GBP"},
          "hot_water_cost_current": 135,
          "lighting_cost_current": {"value": 80, "currency": "GBP"},
          "walls": [{
            "description": {"value": "Cavity wall, insulated", "language": "1"},
            "energy_efficiency_rating": 4
          }],
          "main_heating": [{
            "description": "Boiler and radiators, mains gas",
            "energy_efficiency_rating": 4
          }],
          "main_heating_controls": [{
            "description": "Programmer, room thermostat and TRVs",
            "energy_efficiency_rating": 5
          }],
          "hot_water": {
            "description": {"value": "From main system", "language": "1"},
            "energy_efficiency_rating": 3
          },
          "window": {
            "description": "Fully double glazed",
            "energy_efficiency_rating": 4
          },
          "lighting": {
            "description": "Low energy lighting in all fixed outlets",
            "energy_efficiency_rating": 5
          },
          "inspection_date": "2026-08-28",
          "registration_date": "2026-09-01"
        }
      }
      """;

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private EpcService service;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    service = new EpcService(restTemplate, new ObjectMapper(), BASE, "  test-token  ");
  }

  @Test
  void searchesThenFetchesDetailsUsingBearerAuthAndPreservesTheAppContract() {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));

    Map<String, Object> certificate = service.fetchByUprn(UPRN).orElseThrow();

    assertThat(certificate)
        .containsEntry("uprn", UPRN)
        .containsEntry("lmk-key", CERTIFICATE)
        .containsEntry("current-energy-rating", "C")
        .containsEntry("potential-energy-rating", "B")
        .containsEntry("current-energy-efficiency", 72)
        .containsEntry("potential-energy-efficiency", 85)
        .containsEntry("total-floor-area", 96.5)
        .containsEntry("property-type", "House")
        .containsEntry("built-form", "Semi-Detached")
        .containsEntry("tenure", "owner-occupied")
        .containsEntry("heating-cost-current", 703)
        .containsEntry("hot-water-cost-current", 135)
        .containsEntry("lighting-cost-current", 80)
        .containsEntry("walls-description", "Cavity wall, insulated")
        .containsEntry("walls-energy-eff", "Good")
        .containsEntry("main-heat-description", "Boiler and radiators, mains gas")
        .containsEntry("mainheat-description", "Boiler and radiators, mains gas")
        .containsEntry("mainheat-energy-eff", "Good")
        .containsEntry("main-heating-controls", "Programmer, room thermostat and TRVs")
        .containsEntry("mainheatcont-description", "Programmer, room thermostat and TRVs")
        .containsEntry("mainheatc-energy-eff", "Very Good")
        .containsEntry("hot-water-description", "From main system")
        .containsEntry("hotwater-description", "From main system")
        .containsEntry("hot-water-energy-eff", "Average")
        .containsEntry("windows-description", "Fully double glazed")
        .containsEntry("windows-energy-eff", "Good")
        .containsEntry("lighting-description", "Low energy lighting in all fixed outlets")
        .containsEntry("lighting-energy-eff", "Very Good")
        .containsEntry("inspection-date", "2026-08-28")
        .containsEntry("lodgement-date", "2026-09-01");
    server.verify();
  }

  @Test
  void acceptsATrailingSlashInTheConfiguredBaseUrl() {
    service = new EpcService(restTemplate, new ObjectMapper(), BASE + "/", "test-token");
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));

    assertThat(service.fetchByUprn(UPRN)).isPresent();
    server.verify();
  }

  @Test
  void usesSummaryMetadataWhenItIsAbsentFromTheCertificate() {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess("""
        {"data": {
          "current_energy_efficiency_band": "C",
          "potential_energy_efficiency_band": "B",
          "energy_rating_current": 72,
          "energy_rating_potential": 85
        }}
        """, MediaType.APPLICATION_JSON));

    Map<String, Object> certificate = service.fetchByUprn(UPRN).orElseThrow();

    assertThat(certificate)
        .containsEntry("lmk-key", CERTIFICATE)
        .containsEntry("uprn", UPRN)
        .containsEntry("lodgement-date", "2026-09-01");
    assertThat(certificate.get("address")).asString().contains("10 Test Road");
    server.verify();
  }

  @Test
  void emptySearchReturnsNoCertificateWithoutRequestingDetails() {
    expectSearch().andRespond(withSuccess("{\"data\":[],\"pagination\":{}}", MediaType.APPLICATION_JSON));

    assertThat(service.fetchByUprn(UPRN)).isEmpty();
    server.verify();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void missingTokenFailsBeforeMakingAnUpstreamRequest(String token) {
    service = new EpcService(restTemplate, new ObjectMapper(), BASE, token);

    assertThatThrownBy(() -> service.fetchByUprn(UPRN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_EPC_API_KEY");
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 429, 500})
  void upstreamSearchFailuresAreNotReportedAsMissingCertificates(int status) {
    expectSearch().andRespond(withStatus(HttpStatus.valueOf(status)));

    assertUpstreamFailure(status);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 429, 500})
  void upstreamDetailFailuresAreNotReportedAsMissingCertificates(int status) {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withStatus(HttpStatus.valueOf(status)));

    assertUpstreamFailure(status);
    server.verify();
  }

  @Test
  void searchNotFoundReturnsNoCertificate() {
    expectSearch().andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(service.fetchByUprn(UPRN)).isEmpty();
    server.verify();
  }

  @Test
  void certificateRemovedBetweenSearchAndDetailReturnsNoCertificate() {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(service.fetchByUprn(UPRN)).isEmpty();
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", "{}", "{\"rows\":[]}", "{\"data\":null}", "{\"data\":{}}",
      "{\"data\":[null]}", "{\"data\":[{}]}", "{\"data\":[\"certificate\"]}",
      "{\"data\":[{\"certificateNumber\":\" \"}]}", "invalid json"
  })
  void malformedSearchResponsesFailInsteadOfReturningNoCertificate(String body) {
    expectSearch().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.fetchByUprn(UPRN)).isInstanceOf(RestClientException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "{\"data\":null}", "{\"data\":{}}", "{\"data\":[]}", "{\"data\":\"certificate\"}", "invalid json"})
  void malformedCertificateResponsesFailInsteadOfReturningNoCertificate(String body) {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.fetchByUprn(UPRN)).isInstanceOf(RestClientException.class);
    server.verify();
  }

  @Test
  void normalizedCertificateStillRendersInBothPdfs() throws Exception {
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess(DETAIL, MediaType.APPLICATION_JSON));
    Map<String, Object> certificate = service.fetchByUprn(UPRN).orElseThrow();

    SpringTemplateEngine engine = templateEngine();
    String certificateText = pdfText(new PdfGenerationService(engine).generateEpcPdf(certificate));
    String reportText = pdfText(new ReportPdfService(engine)
        .generateReport(certificate, null, null, null, "SW1A 1AA"));

    assertThat(certificateText)
        .contains("Energy Performance Certificate", UPRN, CERTIFICATE, "72", "85", "96.5",
            "10 Test Road", "London", "703", "Cavity wall, insulated", "Boiler and radiators");
    assertThat(reportText)
        .contains("10 Test Road", "72", "85", "96.5", "703", "Cavity wall, insulated");
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "999"})
  void absentOrUnknownOptionalEnumsDoNotBreakEitherPdf(String optionalEnum) throws Exception {
    String detail = DETAIL.replace("\"tenure\": 1", "\"tenure\": " + optionalEnum)
        .replace("\"property_type\": 0", "\"property_type\": " + optionalEnum)
        .replace("\"built_form\": 2", "\"built_form\": " + optionalEnum);
    expectSearch().andRespond(withSuccess(SEARCH, MediaType.APPLICATION_JSON));
    expectCertificate().andRespond(withSuccess(detail, MediaType.APPLICATION_JSON));
    Map<String, Object> certificate = service.fetchByUprn(UPRN).orElseThrow();
    SpringTemplateEngine engine = templateEngine();

    assertThat(pdfText(new PdfGenerationService(engine).generateEpcPdf(certificate)))
        .contains(CERTIFICATE, "72", "85");
    assertThat(pdfText(new ReportPdfService(engine)
        .generateReport(certificate, null, null, null, "SW1A 1AA")))
        .contains("10 Test Road", "72", "85");
    server.verify();
  }

  private SpringTemplateEngine templateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private void assertUpstreamFailure(int status) {
    if (status == 401 || status == 403) {
      assertThatThrownBy(() -> service.fetchByUprn(UPRN))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APP_EPC_API_KEY");
    } else {
      assertThatThrownBy(() -> service.fetchByUprn(UPRN)).isInstanceOf(RestClientException.class);
    }
  }

  private String pdfText(byte[] pdf) throws Exception {
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    PdfReader reader = new PdfReader(pdf);
    try {
      StringBuilder text = new StringBuilder();
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        text.append(extractor.getTextFromPage(page));
      }
      return text.toString();
    } finally {
      reader.close();
    }
  }

  private ResponseActions expectSearch() {
    return server.expect(requestTo(startsWith(BASE + "/domestic/search?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("uprn", UPRN))
        .andExpect(queryParam("page_size", "1"))
        .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));
  }

  private ResponseActions expectCertificate() {
    return server.expect(requestTo(startsWith(BASE + "/certificate?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("certificate_number", CERTIFICATE))
        .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));
  }
}
