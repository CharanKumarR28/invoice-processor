package com.myapp.controller;

import com.myapp.dto.ProcessRequest;
import com.myapp.dto.ProcessResponse;
import com.myapp.dto.StatusResponse;
import com.myapp.model.FileTransaction;
import com.myapp.service.InvoiceProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice Processor", description = "APIs for processing PDF invoices from S3")
public class InvoiceController {

    private final InvoiceProcessingService invoiceProcessingService;

    @PostMapping("/process")
    @Operation(
            summary = "Process an invoice PDF",
            description = "Fetches a PDF from S3 input bucket, extracts invoice data, saves to MySQL, and uploads CSV summary to S3 output bucket"
    )
    public ResponseEntity<ProcessResponse> processInvoice(
            @Valid @RequestBody ProcessRequest request) {

        log.info("Received process request for file: {}", request.getFileName());
        String transactionId = invoiceProcessingService.initiateProcessing(request.getFileName());

        ProcessResponse response = ProcessResponse.builder()
                .transactionId(transactionId)
                .status("RECEIVED")
                .message("Processing started. Use /api/invoices/status/" + transactionId + " to track progress.")
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/status/{transactionId}")
    @Operation(
            summary = "Check processing status",
            description = "Returns current status of the invoice processing job — RECEIVED / PROCESSING / SUCCESS / FAILED"
    )
    public ResponseEntity<StatusResponse> getStatus(
            @Parameter(description = "Transaction ID returned from /process")
            @PathVariable String transactionId) {

        log.info("Status check for transactionId: {}", transactionId);
        FileTransaction transaction = invoiceProcessingService.getStatus(transactionId);

        StatusResponse response = StatusResponse.builder()
                .transactionId(transaction.getTransactionId())
                .inputFileName(transaction.getInputFileName())
                .outputFileName(transaction.getOutputFileName())
                .status(transaction.getStatus().name())
                .errorMessage(transaction.getErrorMessage())
                .createdAt(transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null)
                .updatedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt().toString() : null)
                .build();

        return ResponseEntity.ok(response);
    }
}