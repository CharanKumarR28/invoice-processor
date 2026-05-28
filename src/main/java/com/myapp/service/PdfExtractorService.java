package com.myapp.service;

import com.myapp.dto.ProcessRequest;
import com.myapp.model.Invoice;
import com.myapp.model.InvoiceLineItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfExtractorService {

    /**
     * Extract text from PDF bytes and map to Invoice object
     */
    public Invoice extractInvoiceData(byte[] pdfBytes) throws IOException {
        log.info("Starting PDF text extraction...");

        // Step 1: Load PDF from bytes using PDFBox
        PDDocument document = PDDocument.load(pdfBytes);
        PDFTextStripper stripper = new PDFTextStripper();
        String rawText = stripper.getText(document);
        document.close();

        log.info("PDF text extracted successfully. Parsing invoice fields...");
        log.debug("Raw PDF text:\n{}", rawText);

        // Step 2: Parse fields from raw text
        return parseInvoiceFromText(rawText);
    }

    /**
     * Parse raw PDF text into an Invoice object using Regex patterns.
     * These patterns match common invoice PDF formats.
     * You can customize them to match your specific PDF layout.
     */
    private Invoice parseInvoiceFromText(String text) {
        Invoice invoice = new Invoice();

        // Extract Invoice Number — looks for: "Invoice No: INV-001" or "Invoice #: INV-001"
        invoice.setInvoiceNumber(extractField(text,
                "(?i)invoice\\s*(no|number|#)[:\\s]+([A-Z0-9\\-]+)", 2, "UNKNOWN"));

        // Extract Vendor Name — looks for: "Vendor: Acme Corp" or "From: Acme Corp"
        invoice.setVendorName(extractField(text,
                "(?i)(vendor|from|bill from|seller)[:\\s]+([\\w\\s]+)", 2, "UNKNOWN VENDOR"));

        // Extract Currency — looks for: USD, INR, EUR etc.
        invoice.setCurrency(extractField(text,
                "(?i)(USD|INR|EUR|GBP|AUD)", 1, "USD"));

        // Extract Total Amount — looks for: "Total: 5000.00" or "Amount Due: 5000"
        String totalStr = extractField(text,
                "(?i)(total amount|total due|amount due|grand total)[:\\s$₹€£]*([\\d,]+\\.?\\d*)", 2, "0");
        invoice.setTotalAmount(parseBigDecimal(totalStr));

        // Extract Date — looks for: "Date: 2024-01-15" or "Invoice Date: 15/01/2024"
        invoice.setInvoiceDate(parseDate(text));

        // Extract Line Items — looks for rows like: "Item description  2  500.00  1000.00"
        invoice.setLineItems(extractLineItems(text, invoice));

        log.info("Parsed invoice: number={}, vendor={}, total={}",
                invoice.getInvoiceNumber(), invoice.getVendorName(), invoice.getTotalAmount());

        return invoice;
    }

    private String extractField(String text, String pattern, int group, String defaultValue) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(group).trim();
            }
        } catch (Exception e) {
            log.warn("Failed to extract field with pattern: {}", pattern);
        }
        return defaultValue;
    }

    private LocalDate parseDate(String text) {
        try {
            // Try: YYYY-MM-DD
            Pattern p = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
            Matcher m = p.matcher(text);
            if (m.find()) {
                return LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))
                );
            }
            // Try: DD/MM/YYYY
            p = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
            m = p.matcher(text);
            if (m.find()) {
                return LocalDate.of(
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(1))
                );
            }
        } catch (Exception e) {
            log.warn("Could not parse date from PDF text");
        }
        return LocalDate.now();
    }

    private List<InvoiceLineItem> extractLineItems(String text, Invoice invoice) {
        List<InvoiceLineItem> items = new ArrayList<>();
        try {
            // Pattern matches: description  qty  unitPrice  totalPrice
            // e.g.: "Web Development  5  200.00  1000.00"
            Pattern p = Pattern.compile("([A-Za-z ]+)\\s+(\\d+)\\s+([\\d.]+)\\s+([\\d.]+)");
            Matcher m = p.matcher(text);
            while (m.find()) {
                InvoiceLineItem item = InvoiceLineItem.builder()
                        .invoice(invoice)
                        .description(m.group(1).trim())
                        .quantity(Integer.parseInt(m.group(2).trim()))
                        .unitPrice(parseBigDecimal(m.group(3)))
                        .totalPrice(parseBigDecimal(m.group(4)))
                        .build();
                items.add(item);
            }
        } catch (Exception e) {
            log.warn("Could not extract line items: {}", e.getMessage());
        }
        return items;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return new BigDecimal(value.replaceAll(",", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
