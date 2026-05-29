package com.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SesClient sesClient;

    @Value("${aws.ses.sender-email}")
    private String senderEmail;

    @Value("${aws.s3.output-bucket}")
    private String outputBucket;

    /**
     * Send HTML notification email to customer after successful processing.
     * CC vendor email if available.
     *
     * @param customerEmail  extracted client/bill-to email from PDF
     * @param vendorEmail    extracted vendor/supplier email from PDF (optional)
     * @param invoiceNumber  extracted invoice number
     * @param vendorName     extracted vendor name
     * @param outputFileName generated CSV filename in S3
     * @param transactionId  unique transaction ID
     */
    public boolean sendProcessingCompleteEmail(String customerEmail,
                                            String vendorEmail,
                                            String invoiceNumber,
                                            String vendorName,
                                            String outputFileName,
                                            String transactionId) {

        // If no customer email found — check vendor email as fallback
        String primaryRecipient = customerEmail;
        if (primaryRecipient == null || primaryRecipient.isEmpty()) {
            if (vendorEmail != null && !vendorEmail.isEmpty()) {
                primaryRecipient = vendorEmail;
                log.warn("No customer email found. Falling back to vendor email: {}", vendorEmail);
            } else {
                log.warn("No email found in PDF for transactionId={}. Skipping email notification.", transactionId);
                return false;
            }
        }

        try {
            String subject = "Your Invoice Has Been Processed — " + invoiceNumber;
            String htmlBody = buildHtmlEmail(invoiceNumber, vendorName, outputFileName, transactionId);

            // Build destination — customer as TO, vendor as CC (if different email)
            Destination.Builder destinationBuilder = Destination.builder()
                    .toAddresses(primaryRecipient);

            if (vendorEmail != null
                    && !vendorEmail.isEmpty()
                    && !vendorEmail.equalsIgnoreCase(primaryRecipient)) {
                destinationBuilder.ccAddresses(Collections.singletonList(vendorEmail));
                log.info("CC vendor email: {}", vendorEmail);
            }

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(senderEmail)
                    .destination(destinationBuilder.build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(htmlBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("Email sent successfully → TO: {} | transactionId: {}", primaryRecipient, transactionId);
            return true;

        } catch (MessageRejectedException e) {
            log.error("SES rejected email — email address may not be verified (sandbox mode) | error: {}", e.getMessage());
        } catch (MailFromDomainNotVerifiedException e) {
            log.error("Sender email domain not verified in SES | sender: {} | error: {}", senderEmail, e.getMessage());
        } catch (ConfigurationSetDoesNotExistException e) {
            log.error("SES configuration set not found | error: {}", e.getMessage());
        } catch (Exception e) {
            // Email failure should NEVER fail the main pipeline
            log.error("Unexpected error sending email to {} | error: {}", primaryRecipient, e.getMessage());
        }
        return false;
    }

    /**
     * Build a professional HTML email body
     */
    private String buildHtmlEmail(String invoiceNumber,
                                  String vendorName,
                                  String outputFileName,
                                  String transactionId) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<body style='margin:0; padding:0; font-family: Arial, sans-serif; background-color:#f4f4f4;'>" +

                // ── Outer wrapper ──────────────────────────────────────
                "<div style='max-width:620px; margin:30px auto; background-color:#ffffff;" +
                "            border-radius:8px; overflow:hidden;" +
                "            box-shadow: 0 2px 8px rgba(0,0,0,0.1);'>" +

                // ── Header bar ─────────────────────────────────────────
                "<div style='background-color:#2C3E50; padding:24px 30px;'>" +
                "  <h1 style='margin:0; color:#ffffff; font-size:20px;'>" +
                "    ✅ Invoice Processing Complete" +
                "  </h1>" +
                "</div>" +

                // ── Body ───────────────────────────────────────────────
                "<div style='padding:30px;'>" +

                "  <p style='font-size:15px; color:#333;'>Dear <strong>" + vendorName + "</strong>,</p>" +
                "  <p style='font-size:14px; color:#555;'>" +
                "    Your invoice has been successfully processed by our system." +
                "    The extracted summary CSV is now available in your AWS S3 output bucket." +
                "  </p>" +

                // ── Details table ──────────────────────────────────────
                "  <table style='width:100%; border-collapse:collapse; margin:20px 0; font-size:14px;'>" +

                "    <tr style='background-color:#f2f2f2;'>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; font-weight:bold; width:40%;'>Invoice Number</td>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; color:#2C3E50;'>" + invoiceNumber + "</td>" +
                "    </tr>" +

                "    <tr>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; font-weight:bold;'>Transaction ID</td>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; color:#555; font-size:12px;'>" + transactionId + "</td>" +
                "    </tr>" +

                "    <tr style='background-color:#f2f2f2;'>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; font-weight:bold;'>Output File</td>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd;'>" +
                "        <code style='background:#eef; padding:2px 6px; border-radius:4px; font-size:12px;'>" +
                outputFileName +
                "        </code>" +
                "      </td>" +
                "    </tr>" +

                "    <tr>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; font-weight:bold;'>S3 Output Bucket</td>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; color:#555;'>" + outputBucket + "</td>" +
                "    </tr>" +

                "    <tr style='background-color:#eafaf1;'>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd; font-weight:bold;'>Status</td>" +
                "      <td style='padding:12px 16px; border:1px solid #ddd;'>" +
                "        <span style='color:#27AE60; font-weight:bold;'>✅ SUCCESS</span>" +
                "      </td>" +
                "    </tr>" +

                "  </table>" +

                // ── Instructions ───────────────────────────────────────
                "  <div style='background-color:#fef9e7; border-left:4px solid #f39c12;" +
                "              padding:14px 18px; border-radius:4px; margin:20px 0;'>" +
                "    <p style='margin:0; font-size:13px; color:#7d6608;'>" +
                "      <strong>How to access your file:</strong><br/>" +
                "      Log in to AWS S3 Console → Open <strong>" + outputBucket + "</strong> → " +
                "      Navigate to <strong>output/</strong> folder → Download <strong>" + outputFileName + "</strong>" +
                "    </p>" +
                "  </div>" +

                "</div>" +

                // ── Footer ─────────────────────────────────────────────
                "<div style='background-color:#f4f4f4; padding:16px 30px; text-align:center;" +
                "            border-top:1px solid #ddd;'>" +
                "  <p style='margin:0; font-size:11px; color:#aaa;'>" +
                "    This is an automated notification from Invoice Processor Service.<br/>" +
                "    Please do not reply to this email." +
                "  </p>" +
                "</div>" +

                "</div>" +
                "</body></html>";
    }
}