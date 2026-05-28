package com.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.input-bucket}")
    private String inputBucket;

    @Value("${aws.s3.output-bucket}")
    private String outputBucket;

    /**
     * Fetch a PDF file from the S3 input bucket as byte array
     */
    public byte[] fetchFileFromS3(String fileName) {
        log.info("Fetching file from S3 input bucket: {}", fileName);
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(inputBucket)
                    .key(fileName)
                    .build();

            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            log.info("Successfully fetched file: {} ({} bytes)", fileName, response.asByteArray().length);
            return response.asByteArray();

        } catch (Exception e) {
            log.error("Failed to fetch file from S3: {}", fileName, e);
            throw new RuntimeException("Could not fetch file from S3: " + fileName, e);
        }
    }

    /**
     * Upload the output CSV file to the S3 output bucket
     */
    public void uploadFileToS3(String outputFileName, byte[] content) {
        log.info("Uploading output file to S3 output bucket: {}", outputFileName);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(outputBucket)
                    .key(outputFileName)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));
            log.info("Successfully uploaded output file: {}", outputFileName);

        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", outputFileName, e);
            throw new RuntimeException("Could not upload file to S3: " + outputFileName, e);
        }
    }
}
