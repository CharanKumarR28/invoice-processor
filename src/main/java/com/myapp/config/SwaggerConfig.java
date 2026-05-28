package com.myapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI invoiceProcessorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Invoice Processor API")
                        .description("Spring Boot service that fetches PDF invoices from S3, extracts data using Apache PDFBox, stores in MySQL, and uploads CSV output back to S3.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Invoice Processor")
                                .email("admin@invoiceprocessor.com")));
    }
}