package com.houselookup.backend.service;

import com.lowagie.text.DocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Service
public class PdfGenerationService {

  private final TemplateEngine templateEngine;

  public PdfGenerationService(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  /**
   * Generates a PDF from EPC certificate data.
   *
   * @param epcData the EPC data map from the API
   * @return PDF bytes
   */
  public byte[] generateEpcPdf(Map<String, Object> epcData) throws IOException, DocumentException {
    // Create Thymeleaf context with EPC data
    Context context = new Context();
    context.setVariable("epc", epcData);

    // Render HTML template
    String html = templateEngine.process("epc-certificate", context);

    // Convert HTML to PDF using Flying Saucer
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ITextRenderer renderer = new ITextRenderer();
    renderer.setDocumentFromString(html);
    renderer.layout();
    renderer.createPDF(outputStream);
    renderer.finishPDF();

    return outputStream.toByteArray();
  }
}
