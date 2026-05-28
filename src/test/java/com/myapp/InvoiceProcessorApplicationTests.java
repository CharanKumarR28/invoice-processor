package com.myapp;

import com.myapp.enums.FileStatus;
import com.myapp.model.FileTransaction;
import com.myapp.repository.FileTransactionRepository;
import com.myapp.service.InvoiceProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class InvoiceProcessorApplicationTests {

    @Autowired
    private InvoiceProcessingService invoiceProcessingService;

    @Autowired
    private FileTransactionRepository fileTransactionRepository;

    @MockBean
    private S3Client s3Client;

    /**
     * Test: initiateProcessing should create a transaction record in DB
     * with status RECEIVED and return a valid transactionId
     */
    @Test
    void testInitiateProcessing_CreatesTransactionRecord() {
        String fileName = "test_invoice.pdf";

        String transactionId = invoiceProcessingService.initiateProcessing(fileName);

        assertNotNull(transactionId);
        assertFalse(transactionId.isEmpty());

        Optional<FileTransaction> transaction = fileTransactionRepository.findByTransactionId(transactionId);
        assertTrue(transaction.isPresent());
        assertEquals(fileName, transaction.get().getInputFileName());
        assertEquals(FileStatus.RECEIVED, transaction.get().getStatus());
    }

    /**
     * Test: getStatus should throw RuntimeException for unknown transactionId
     */
    @Test
    void testGetStatus_ThrowsForUnknownTransaction() {
        assertThrows(RuntimeException.class, () ->
                invoiceProcessingService.getStatus("non-existent-id")
        );
    }

    /**
     * Test: Two calls with different file names should produce unique transactionIds
     */
    @Test
    void testInitiateProcessing_UniqueTransactionIds() {
        String txn1 = invoiceProcessingService.initiateProcessing("file1.pdf");
        String txn2 = invoiceProcessingService.initiateProcessing("file2.pdf");
        assertNotEquals(txn1, txn2);
    }
}
