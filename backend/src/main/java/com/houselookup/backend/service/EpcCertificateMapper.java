package com.houselookup.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adapts the government EPC detail schema to the application's existing report fields. */
final class EpcCertificateMapper {
  // Labels are defined in the government's epb-data-warehouse/spec/fixtures/look_up_data.csv.
  private static final Map<String, String> PROPERTY_TYPES = Map.of(
      "0", "House", "1", "Bungalow", "2", "Flat", "3", "Maisonette", "4", "Park home");
  private static final Map<String, String> BUILT_FORMS = Map.of(
      "1", "Detached", "2", "Semi-Detached", "3", "End-Terrace", "4", "Mid-Terrace",
      "5", "Enclosed End-Terrace", "6", "Enclosed Mid-Terrace", "NR", "Not Recorded");
  private static final Map<String, String> TENURES = Map.of(
      "1", "owner-occupied", "2", "rented (social)", "3", "rented (private)", "ND", "unknown");
  private static final Map<String, String> TRANSACTIONS = Map.ofEntries(
      Map.entry("1", "Marketed sale"), Map.entry("2", "Non-marketed sale"),
      Map.entry("5", "None of the above"), Map.entry("6", "New dwelling"),
      Map.entry("8", "Rental"), Map.entry("9", "Assessment for Green Deal"),
      Map.entry("10", "Following Green Deal"), Map.entry("11", "FiT application"),
      Map.entry("12", "RHI application"), Map.entry("13", "ECO assessment"),
      Map.entry("14", "Stock condition survey"), Map.entry("15", "Re-mortgaging"),
      Map.entry("16", "Grant scheme"), Map.entry("17", "Non-grant scheme"));
  private static final Map<String, String> EFFICIENCIES = Map.of(
      "0", "N/A", "1", "Very Poor", "2", "Poor", "3", "Average", "4", "Good", "5", "Very Good");
  private static final Map<String, String> FLAT_LEVELS = Map.of(
      "0", "basement", "1", "ground floor", "2", "mid floor", "3", "top floor");
  private static final Map<String, String> CONSTRUCTION_AGES = Map.ofEntries(
      Map.entry("A", "England and Wales: before 1900"),
      Map.entry("B", "England and Wales: 1900-1929"),
      Map.entry("C", "England and Wales: 1930-1949"),
      Map.entry("D", "England and Wales: 1950-1966"),
      Map.entry("E", "England and Wales: 1967-1975"),
      Map.entry("F", "England and Wales: 1976-1982"),
      Map.entry("G", "England and Wales: 1983-1990"),
      Map.entry("H", "England and Wales: 1991-1995"),
      Map.entry("I", "England and Wales: 1996-2002"),
      Map.entry("J", "England and Wales: 2003-2006"),
      Map.entry("K", "England and Wales: 2007-2011"),
      Map.entry("0", "Not applicable"), Map.entry("NR", "Not recorded"));
  private static final Map<String, String> RDSAP_FUELS = Map.ofEntries(
      Map.entry("1", "mains gas"), Map.entry("2", "LPG"), Map.entry("3", "bottled LPG"),
      Map.entry("4", "oil"), Map.entry("5", "anthracite"), Map.entry("6", "wood logs"),
      Map.entry("7", "bulk wood pellets"), Map.entry("8", "wood chips"),
      Map.entry("9", "dual fuel - mineral + wood"), Map.entry("10", "electricity"),
      Map.entry("11", "waste combustion"), Map.entry("12", "biomass"),
      Map.entry("13", "biogas - landfill"), Map.entry("14", "house coal"),
      Map.entry("15", "smokeless coal"),
      Map.entry("16", "wood pellets in bags for secondary heating"),
      Map.entry("17", "LPG special condition"), Map.entry("18", "B30K (not community)"),
      Map.entry("19", "bioethanol"), Map.entry("20", "mains gas (community)"),
      Map.entry("21", "LPG (community)"), Map.entry("22", "oil (community)"),
      Map.entry("23", "B30D (community)"), Map.entry("24", "coal (community)"),
      Map.entry("25", "electricity (community)"), Map.entry("26", "mains gas (not community)"),
      Map.entry("27", "LPG (not community)"), Map.entry("28", "oil (not community)"),
      Map.entry("29", "electricity (not community)"), Map.entry("30", "waste combustion (community)"),
      Map.entry("31", "biomass (community)"), Map.entry("32", "biogas (community)"),
      Map.entry("33", "house coal (not community)"),
      Map.entry("34", "biodiesel from any biomass source"),
      Map.entry("35", "biodiesel from used cooking oil only"),
      Map.entry("36", "biodiesel from vegetable oil only (not community)"),
      Map.entry("37", "appliances able to use mineral oil or liquid biofuel"),
      Map.entry("51", "biogas (not community)"),
      Map.entry("56", "heat from boilers that can use mineral oil or biodiesel (community)"),
      Map.entry("57", "heat from boilers using biodiesel from any biomass source (community)"),
      Map.entry("58", "biodiesel from vegetable oil only (community)"),
      Map.entry("99", "from heat network data (community)"));
  private static final Map<String, String> SAP_FUELS = Map.ofEntries(
      Map.entry("1", "Gas: mains gas"), Map.entry("2", "Gas: bulk LPG"),
      Map.entry("3", "Gas: bottled LPG"), Map.entry("4", "Oil: heating oil"),
      Map.entry("7", "Gas: biogas"), Map.entry("8", "LNG"),
      Map.entry("9", "LPG subject to Special Condition 18"),
      Map.entry("10", "Solid fuel: dual fuel appliance (mineral and wood)"),
      Map.entry("11", "Solid fuel: house coal"), Map.entry("12", "Solid fuel: manufactured smokeless fuel"),
      Map.entry("15", "Solid fuel: anthracite"), Map.entry("20", "Solid fuel: wood logs"),
      Map.entry("21", "Solid fuel: wood chips"),
      Map.entry("22", "Solid fuel: wood pellets (in bags, for secondary heating)"),
      Map.entry("23", "Solid fuel: wood pellets (bulk supply in bags, for main heating)"),
      Map.entry("39", "Electricity: electricity, unspecified tariff"));

  private final ObjectMapper objectMapper;

  EpcCertificateMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  Map<String, Object> map(JsonNode detail, JsonNode summary) {
    JsonNode certificate = detail == null ? MissingNode.getInstance() : detail;
    JsonNode listing = summary == null ? MissingNode.getInstance() : summary;
    Map<String, Object> result = new LinkedHashMap<>();

    for (int line = 1; line <= 4; line++) {
      result.put("address" + line,
          text(first(certificate.path("address_line_" + line), listing.path("addressLine" + line))));
    }
    result.put("post-town", text(first(certificate.path("post_town"), listing.path("postTown"))));
    result.put("postcode", text(first(certificate.path("postcode"), listing.path("postcode"))));
    result.put("address", address(result));
    result.put("uprn", text(first(certificate.path("uprn"), listing.path("uprn"))));
    result.put("lmk-key", text(first(certificate.path("certificate_number"), listing.path("certificateNumber"))));
    result.put("building-reference-number", text(certificate.path("building_reference_number")));
    result.put("local-authority-label", text(first(certificate.path("council"), listing.path("council"))));
    result.put("lodgement-date", text(first(certificate.path("registration_date"), listing.path("registrationDate"))));
    result.put("inspection-date", text(certificate.path("inspection_date")));

    result.put("current-energy-rating", text(first(certificate.path("current_energy_efficiency_band"),
        listing.path("currentEnergyEfficiencyBand"))));
    result.put("potential-energy-rating", text(certificate.path("potential_energy_efficiency_band")));
    result.put("current-energy-efficiency", number(certificate.path("energy_rating_current")));
    result.put("potential-energy-efficiency", number(certificate.path("energy_rating_potential")));
    result.put("property-type", label(certificate.path("property_type"), PROPERTY_TYPES));
    result.put("built-form", label(certificate.path("built_form"), BUILT_FORMS));
    result.put("tenure", label(certificate.path("tenure"), TENURES));
    result.put("transaction-type", label(certificate.path("transaction_type"), TRANSACTIONS));
    result.put("total-floor-area", number(certificate.path("total_floor_area")));
    result.put("number-habitable-rooms", number(certificate.path("habitable_room_count")));
    result.put("floor-level", label(certificate.path("sap_flat_details").path("level"), FLAT_LEVELS));
    result.put("construction-age-band", constructionAge(certificate));
    result.put("main-fuel", mainFuel(certificate));

    for (String key : List.of("heating-cost-current", "heating-cost-potential", "hot-water-cost-current",
        "hot-water-cost-potential", "lighting-cost-current", "lighting-cost-potential",
        "co2-emissions-current", "co2-emissions-potential", "energy-consumption-current",
        "energy-consumption-potential")) {
      result.put(key, number(certificate.path(key.replace('-', '_'))));
    }
    result.put("environment-impact-current", number(certificate.path("environmental_impact_current")));
    result.put("environment-impact-potential", number(certificate.path("environmental_impact_potential")));
    result.put("co2-emiss-curr-per-floor-area", number(certificate.path("co2_emissions_current_per_floor_area")));

    component(result, certificate.path("walls"), "walls", "walls");
    component(result, certificate.path("roofs"), "roof", "roof");
    component(result, certificate.path("floors"), "floor", "floor");
    component(result, first(certificate.path("window"), certificate.path("windows")), "windows", "windows");
    component(result, certificate.path("main_heating"), "mainheat", "mainheat");
    component(result, certificate.path("main_heating_controls"), "mainheatcont", "mainheatc");
    component(result, certificate.path("hot_water"), "hotwater", "hot-water");
    component(result, certificate.path("lighting"), "lighting", "lighting");
    result.put("main-heat-description", result.get("mainheat-description"));
    result.put("main-heating-controls", result.get("mainheatcont-description"));
    result.put("hot-water-description", result.get("hotwater-description"));
    return result;
  }

  private String address(Map<String, Object> fields) {
    Set<String> parts = new LinkedHashSet<>();
    for (String key : List.of("address1", "address2", "address3", "address4", "post-town", "postcode")) {
      if (fields.get(key) instanceof String value && !value.isBlank()) {
        parts.add(value);
      }
    }
    return parts.isEmpty() ? null : String.join(", ", parts);
  }

  private void component(Map<String, Object> result, JsonNode node, String descriptionPrefix, String ratingPrefix) {
    List<JsonNode> components = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(components::add);
    } else if (node.isObject()) {
      components.add(node);
    }
    Set<String> descriptions = new LinkedHashSet<>();
    for (JsonNode item : components) {
      String description = text(item.path("description"));
      if (description != null) {
        descriptions.add(description);
      }
    }
    result.put(descriptionPrefix + "-description", descriptions.isEmpty() ? null : String.join("; ", descriptions));
    result.put(ratingPrefix + "-energy-eff", sharedEfficiency(components, "energy_efficiency_rating"));
    result.put(ratingPrefix + "-env-eff", sharedEfficiency(components, "environmental_efficiency_rating"));
  }

  private String sharedEfficiency(List<JsonNode> components, String field) {
    Set<String> ratings = new LinkedHashSet<>();
    for (JsonNode item : components) {
      String rating = label(item.path(field), EFFICIENCIES);
      if (rating == null) {
        return null;
      }
      ratings.add(rating);
    }
    // The templates have one badge per category; a mixed category has no single rating.
    return ratings.size() == 1 ? ratings.iterator().next() : null;
  }

  private String constructionAge(JsonNode certificate) {
    JsonNode age = certificate.path("construction_age_band");
    if (!present(age)) {
      JsonNode parts = certificate.path("sap_building_parts");
      JsonNode main = parts.path(0);
      if (parts.isArray()) {
        for (JsonNode part : parts) {
          if ("Main Dwelling".equalsIgnoreCase(text(part.path("identifier")))
              || "1".equals(text(part.path("building_part_number")))) {
            main = part;
            break;
          }
        }
      }
      age = main.path("construction_age_band");
    }
    String code = text(age);
    if (code == null) {
      return null;
    }
    if (code.length() > 2) {
      return code;
    }
    String country = text(certificate.path("country_code"));
    if (country != null && !Set.of("ENG", "WLS", "EAW").contains(country)) {
      return null;
    }
    String schema = text(certificate.path("schema_type"));
    boolean rdsap21 = schema != null && schema.startsWith("RdSAP-Schema-21.");
    if ("L".equals(code)) {
      return rdsap21 ? "England and Wales: 2012-2021" : "England and Wales: 2012 onwards";
    }
    if ("M".equals(code)) {
      return rdsap21 ? "England and Wales: 2022 onwards" : null;
    }
    return CONSTRUCTION_AGES.get(code);
  }

  private String mainFuel(JsonNode certificate) {
    String schema = text(certificate.path("schema_type"));
    String assessment = text(certificate.path("assessment_type"));
    boolean rdsap = "RdSAP".equalsIgnoreCase(assessment)
        || (assessment == null && schema != null && schema.startsWith("RdSAP-Schema-"));
    boolean sap = "SAP".equalsIgnoreCase(assessment)
        || (assessment == null && schema != null && schema.startsWith("SAP-Schema-"));
    Set<String> fuels = new LinkedHashSet<>();
    JsonNode heating = certificate.path("sap_heating").path("main_heating_details");
    if (heating.isArray()) {
      for (JsonNode system : heating) {
        JsonNode fuel = system.path("main_fuel_type");
        String code = text(fuel);
        String description = rdsap ? label(fuel, RDSAP_FUELS) : sap ? label(fuel, SAP_FUELS) : null;
        if ((rdsap && "38".equals(code) && schema != null && schema.startsWith("RdSAP-Schema-21."))
            || (sap && "5".equals(code) && "SAP-Schema-19.2.0".equals(schema))) {
          description = "Gas: bottled LPG (for secondary heating)";
        }
        if (description != null) {
          fuels.add(description);
        }
      }
    }
    return fuels.isEmpty() ? null : String.join("; ", fuels);
  }

  private String label(JsonNode node, Map<String, String> labels) {
    String value = text(node);
    if (value == null) {
      return null;
    }
    if (labels.containsKey(value)) {
      return labels.get(value);
    }
    // Unknown codes are absent rather than presented as meaningful property information.
    return value.matches("[+-]?\\d+(\\.\\d+)?|[A-Z]{1,2}") ? null : value;
  }

  private Object number(JsonNode node) {
    JsonNode value = unwrap(node);
    if (!present(value) || (!value.isNumber() && !value.isTextual())) {
      return null;
    }
    try {
      return objectMapper.convertValue(value, Number.class);
    } catch (IllegalArgumentException invalidNumber) {
      return null;
    }
  }

  private String text(JsonNode node) {
    JsonNode value = unwrap(node);
    if (!present(value) || !value.isValueNode()) {
      return null;
    }
    String result = value.asText().trim();
    return result.isEmpty() ? null : result;
  }

  private JsonNode first(JsonNode... nodes) {
    for (JsonNode node : nodes) {
      if (present(unwrap(node))) {
        return node;
      }
    }
    return MissingNode.getInstance();
  }

  private JsonNode unwrap(JsonNode node) {
    JsonNode value = node == null ? MissingNode.getInstance() : node;
    while (value.isObject() && value.has("value")) {
      value = value.path("value");
    }
    return value;
  }

  private boolean present(JsonNode node) {
    return node != null && !node.isMissingNode() && !node.isNull()
        && (!node.isTextual() || !node.asText().isBlank());
  }
}
