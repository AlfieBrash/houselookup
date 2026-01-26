package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LandRegistryService {
  private static final String SPARQL_ENDPOINT = "https://landregistry.data.gov.uk/landregistry/query";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public LandRegistryService(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Fetches price paid data for a given postcode from HM Land Registry.
   *
   * @param postcode the postcode to search
   * @param paon primary addressable object name (house number/name) - optional filter
   * @return list of price paid records
   */
  public List<Map<String, Object>> fetchPriceHistory(String postcode, String paon) {
    String sparqlQuery = buildSparqlQuery(postcode, paon);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));

    String body = "query=" + URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);
    HttpEntity<String> entity = new HttpEntity<>(body, headers);

    try {
      ResponseEntity<JsonNode> response =
          restTemplate.exchange(SPARQL_ENDPOINT, HttpMethod.POST, entity, JsonNode.class);

      return parseResults(response.getBody());
    } catch (Exception e) {
      // Return empty list on error - price history is supplementary data
      return new ArrayList<>();
    }
  }

  private String buildSparqlQuery(String postcode, String paon) {
    StringBuilder query = new StringBuilder();
    query.append("PREFIX lrppi: <http://landregistry.data.gov.uk/def/ppi/>\n");
    query.append("PREFIX lrcommon: <http://landregistry.data.gov.uk/def/common/>\n");
    query.append("PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n");
    query.append("\n");
    query.append("SELECT ?paon ?saon ?street ?town ?county ?postcode ?amount ?date ?propertyType ?estateType ?category\n");
    query.append("WHERE {\n");
    query.append("  ?transx lrppi:pricePaid ?amount ;\n");
    query.append("          lrppi:transactionDate ?date ;\n");
    query.append("          lrppi:propertyAddress ?addr .\n");
    query.append("\n");
    query.append("  ?addr lrcommon:postcode \"").append(postcode.toUpperCase().trim()).append("\" .\n");
    
    // If paon (house number) is provided, filter by it
    if (paon != null && !paon.isBlank()) {
      query.append("  ?addr lrcommon:paon \"").append(paon.trim()).append("\" .\n");
    }
    
    query.append("\n");
    query.append("  OPTIONAL { ?addr lrcommon:paon ?paon }\n");
    query.append("  OPTIONAL { ?addr lrcommon:saon ?saon }\n");
    query.append("  OPTIONAL { ?addr lrcommon:street ?street }\n");
    query.append("  OPTIONAL { ?addr lrcommon:town ?town }\n");
    query.append("  OPTIONAL { ?addr lrcommon:county ?county }\n");
    query.append("  OPTIONAL { ?addr lrcommon:postcode ?postcode }\n");
    query.append("  OPTIONAL { ?transx lrppi:propertyType/skos:prefLabel ?propertyType }\n");
    query.append("  OPTIONAL { ?transx lrppi:estateType/skos:prefLabel ?estateType }\n");
    query.append("  OPTIONAL { ?transx lrppi:transactionCategory/skos:prefLabel ?category }\n");
    query.append("}\n");
    query.append("ORDER BY DESC(?date)\n");
    query.append("LIMIT 20");

    return query.toString();
  }

  private List<Map<String, Object>> parseResults(JsonNode root) {
    List<Map<String, Object>> results = new ArrayList<>();

    if (root == null || !root.has("results") || !root.get("results").has("bindings")) {
      return results;
    }

    JsonNode bindings = root.get("results").get("bindings");
    for (JsonNode binding : bindings) {
      Map<String, Object> record = new LinkedHashMap<>();
      
      record.put("paon", getBindingValue(binding, "paon"));
      record.put("saon", getBindingValue(binding, "saon"));
      record.put("street", getBindingValue(binding, "street"));
      record.put("town", getBindingValue(binding, "town"));
      record.put("county", getBindingValue(binding, "county"));
      record.put("postcode", getBindingValue(binding, "postcode"));
      record.put("amount", getBindingValue(binding, "amount"));
      record.put("date", getBindingValue(binding, "date"));
      record.put("propertyType", getBindingValue(binding, "propertyType"));
      record.put("estateType", getBindingValue(binding, "estateType"));
      record.put("category", getBindingValue(binding, "category"));

      results.add(record);
    }

    return results;
  }

  private String getBindingValue(JsonNode binding, String key) {
    if (binding.has(key) && binding.get(key).has("value")) {
      return binding.get(key).get("value").asText();
    }
    return null;
  }
}
