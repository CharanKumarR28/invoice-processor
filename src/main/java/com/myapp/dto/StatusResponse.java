package com.myapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {
    private String transactionId;
    private String inputFileName;
    private String outputFileName;
    private String status;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
}
