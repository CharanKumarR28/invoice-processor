package com.myapp.service;

import com.myapp.enums.FileStatus;
import com.myapp.model.FileTransaction;
import com.myapp.model.Invoice;
import com.myapp.repository.FileTransactionRepository;
import com.myapp.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceProcessingService {

    private final S3Service s3Service;
    private final PdfExtractorService pdfExtractorService;
    private final CsvGeneratorService csvGeneratorService;
    private final FileTransactionRepository fileTransactionRepository;
    private final InvoiceRepository invoiceRepository;
    // Add to constructor injection
    private final EmailService emailService;

    /**
     * Step 1: Create transaction record and kick off async processing.
     * Returns immediately with transactionId so caller doesn't have to wait.
     */
    @Transactional
    public String initiateProcessing(String fileName) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Initiating processing | transactionId={} | file={}", transactionId, fileName);

        // Save initial record with RECEIVED status
        FileTransaction transaction = FileTransaction.builder()
                .transactionId(transactionId)
                .inputFileName(fileName)
                .status(FileStatus.RECEIVED)
                .build();
        fileTransactionRepository.save(transaction);

        // Trigger async processing — this runs in background thread
        processAsync(transactionId, fileName);

        return transactionId;
    }

    /**
     * Step 2: Async pipeline — fetch → extract → save → generate CSV → upload
     * Runs in background via InvoiceWorker thread pool (defined in AsyncConfig)
     */
    @Async("invoiceTaskExecutor")
    public void processAsync(String transactionId, String fileName) {
        log.info("Async processing started | transactionId={}", transactionId);

        // Mark as PROCESSING
        updateStatus(transactionId, FileStatus.PROCESSING, null);

        try {
            // ── Stage 1: Fetch PDF from S3 ──────────────────────
            log.info("[Stage 1] Fetching PDF from S3: {}", fileName);
            byte[] pdfBytes = s3Service.fetchFileFromS3(fileName);

            // ── Stage 2: Extract Invoice Data from PDF ───────────
            log.info("[Stage 2] Extracting invoice data from PDF");
            Invoice invoice = pdfExtractorService.extractInvoiceData(pdfBytes);

            // ── Stage 3: Save Extracted Data to MySQL ────────────
            log.info("[Stage 3] Saving invoice data to MySQL");
            saveInvoice(transactionId, invoice);

            // ── Stage 4: Generate CSV output using FileWriter ────
            log.info("[Stage 4] Generating CSV output");
            byte[] csvBytes = csvGeneratorService.generateCsv(invoice, transactionId);

            // ── Stage 5: Upload CSV to S3 output bucket ──────────
            String outputFileName = "output/" + transactionId + "_invoice_summary.csv";
            log.info("[Stage 5] Uploading CSV to S3: {}", outputFileName);
            s3Service.uploadFileToS3(outputFileName, csvBytes);

            // ── Mark SUCCESS after upload ─────────────────────────
            updateStatusWithOutput(transactionId, FileStatus.SUCCESS, outputFileName);
            log.info("Invoice processed successfully. Moving to notification stage.");

            // ── Stage 6: Send Email Notification ─────────────
            log.info("[Stage 6] Sending email notification");
            boolean emailSent = emailService.sendProcessingCompleteEmail(
                    invoice.getCustomerEmail(),
                    invoice.getVendorEmail(),
                    invoice.getInvoiceNumber(),
                    invoice.getVendorName(),
                    outputFileName,
                    transactionId
            );

            // ── Stage 7: Update final status based on email result ───
            if (emailSent) {
                updateStatus(transactionId, FileStatus.NOTIFICATION_SENT, null);
                log.info("[Stage 7] Status updated to NOTIFICATION_SENT | transactionId={}", transactionId);
            } else {
                updateStatus(transactionId, FileStatus.NOTIFICATION_FAILED_TO_SEND,
                        "Email notification could not be delivered.");
                log.warn("[Stage 7] Status updated to NOTIFICATION_FAILED_TO_SEND | transactionId={}", transactionId);
            }


        } catch (Exception e) {
            log.error("Processing failed | transactionId={} | error={}", transactionId, e.getMessage(), e);
            updateStatus(transactionId, FileStatus.FAILED, e.getMessage());
        }
    }

    /**
     * Get current status of a transaction
     */
    public FileTransaction getStatus(String transactionId) {
        return fileTransactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
    }

    // ── Private Helpers ──────────────────────────────────────────────

    @Transactional
    private void saveInvoice(String transactionId, Invoice invoice) {
        log.info("Saving invoice → customerEmail={} | vendorEmail={}",
                invoice.getCustomerEmail(), invoice.getVendorEmail());
        FileTransaction transaction = fileTransactionRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        invoice.setFileTransaction(transaction);
        invoiceRepository.save(invoice);
    }

    @Transactional
    private void updateStatus(String transactionId, FileStatus status, String errorMessage) {
        fileTransactionRepository.findByTransactionId(transactionId).ifPresent(t -> {
            t.setStatus(status);
            t.setErrorMessage(errorMessage);
            fileTransactionRepository.save(t);
        });
    }

    @Transactional
    private void updateStatusWithOutput(String transactionId, FileStatus status, String outputFileName) {
        fileTransactionRepository.findByTransactionId(transactionId).ifPresent(t -> {
            t.setStatus(status);
            t.setOutputFileName(outputFileName);
            fileTransactionRepository.save(t);
        });
    }
}
