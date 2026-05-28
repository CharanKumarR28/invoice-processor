package com.myapp.service;

import com.myapp.model.Invoice;
import com.myapp.model.InvoiceLineItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class CsvGeneratorService {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * Generate a CSV summary file from extracted Invoice data.
     * Uses Java FileWriter — writes to temp dir, reads back as bytes for S3 upload.
     */
    public byte[] generateCsv(Invoice invoice, String transactionId) throws IOException {
        String tempFilePath = TEMP_DIR + "/" + transactionId + "_output.csv";
        log.info("Generating CSV output at: {}", tempFilePath);

        FileWriter writer = new FileWriter(tempFilePath);

        // ── Invoice Header Section ────────────────────────────
        writer.write("=== INVOICE SUMMARY ===\n");
        writer.write("Transaction ID," + transactionId + "\n");
        writer.write("Invoice Number," + invoice.getInvoiceNumber() + "\n");
        writer.write("Vendor Name," + invoice.getVendorName() + "\n");
        writer.write("Invoice Date," + invoice.getInvoiceDate() + "\n");
        writer.write("Currency," + invoice.getCurrency() + "\n");
        writer.write("Total Amount," + invoice.getTotalAmount() + "\n");
        writer.write("\n");

        // ── Line Items Section ────────────────────────────────
        writer.write("=== LINE ITEMS ===\n");
        writer.write("Description,Quantity,Unit Price,Total Price\n");

        List<InvoiceLineItem> lineItems = invoice.getLineItems();
        if (lineItems != null && !lineItems.isEmpty()) {
            for (InvoiceLineItem item : lineItems) {
                writer.write(
                        item.getDescription() + "," +
                        item.getQuantity() + "," +
                        item.getUnitPrice() + "," +
                        item.getTotalPrice() + "\n"
                );
            }
        } else {
            writer.write("No line items found\n");
        }

        writer.flush();
        writer.close();

        // Read back the temp file as bytes for S3 upload
        byte[] csvBytes = Files.readAllBytes(Paths.get(tempFilePath));

        // Cleanup temp file
        Files.deleteIfExists(Paths.get(tempFilePath));

        log.info("CSV generated successfully ({} bytes)", csvBytes.length);
        return csvBytes;
    }
}
