package com.houselookup.backend.service;

import com.lowagie.text.DocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Service
public class ReportPdfService {

  private final TemplateEngine templateEngine;

  public ReportPdfService(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  /**
   * Generates a combined property report PDF.
   *
   * @param epcData EPC certificate data (may be null)
   * @param priceHistory list of price paid records (may be null or empty)
   * @param floodRiskData flood risk assessment data (may be null)
   * @param postcode the property postcode
   * @return PDF bytes
   */
  public byte[] generateReport(
      Map<String, Object> epcData,
      List<Map<String, Object>> priceHistory,
      Map<String, Object> floodRiskData,
      String postcode)
      throws IOException, DocumentException {

    Context context = new Context();
    context.setVariable("epc", epcData);
    context.setVariable("priceHistory", priceHistory);
    context.setVariable("floodRisk", floodRiskData);
    context.setVariable("postcode", postcode);
    context.setVariable("hasEpc", epcData != null);
    context.setVariable("hasPriceHistory", priceHistory != null && !priceHistory.isEmpty());
    context.setVariable("hasFloodRisk", floodRiskData != null);

    String html = templateEngine.process("property-report", context);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ITextRenderer renderer = new ITextRenderer();
    renderer.setDocumentFromString(html);
    renderer.layout();
    renderer.createPDF(outputStream);
    renderer.finishPDF();

    return outputStream.toByteArray();
  }
}
