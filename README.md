# Invoice Processor — Spring Boot PDF Processing Service

A production-grade backend service that accepts a PDF invoice filename,
fetches it from AWS S3, extracts invoice data using Apache PDFBox,
stores results in MySQL, generates a CSV summary, and uploads it back to S3.

---

## Tech Stack

| Layer        | Technology                        |
|--------------|-----------------------------------|
| Framework    | Spring Boot 2.7.18 + Java 8       |
| Build Tool   | Gradle                            |
| Database     | MySQL 8                           |
| Cloud        | AWS S3 (SDK v2)                   |
| PDF Parsing  | Apache PDFBox 2.0.29              |
| CSV Output   | Java FileWriter                   |
| Async        | Spring @Async + ThreadPoolExecutor|

---

## Project Structure

```
invoice-processor/
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/com/myapp/
    │   │   ├── InvoiceProcessorApplication.java   ← Main class
    │   │   ├── config/
    │   │   │   ├── S3Config.java                  ← AWS S3 bean
    │   │   │   └── AsyncConfig.java               ← Thread pool config
    │   │   ├── controller/
    │   │   │   ├── InvoiceController.java          ← REST endpoints
    │   │   │   └── GlobalExceptionHandler.java     ← Error handling
    │   │   ├── service/
    │   │   │   ├── InvoiceProcessingService.java   ← Core pipeline
    │   │   │   ├── S3Service.java                  ← S3 fetch/upload
    │   │   │   ├── PdfExtractorService.java        ← PDFBox extraction
    │   │   │   └── CsvGeneratorService.java        ← FileWriter CSV
    │   │   ├── model/
    │   │   │   ├── FileTransaction.java            ← Transaction entity
    │   │   │   ├── Invoice.java                    ← Invoice entity
    │   │   │   └── InvoiceLineItem.java            ← Line items entity
    │   │   ├── repository/
    │   │   │   ├── FileTransactionRepository.java
    │   │   │   ├── InvoiceRepository.java
    │   │   │   └── InvoiceLineItemRepository.java
    │   │   ├── dto/
    │   │   │   ├── ProcessRequest.java
    │   │   │   ├── ProcessResponse.java
    │   │   │   └── StatusResponse.java
    │   │   └── enums/
    │   │       └── FileStatus.java
    │   └── resources/
    │       ├── application.properties             ← Main config
    │       └── schema.sql                         ← MySQL DDL
    └── test/
        ├── java/com/myapp/
        │   └── InvoiceProcessorApplicationTests.java
        └── resources/
            └── application.properties             ← H2 test config
```

---

## How the Pipeline Works

```
POST /api/invoices/process  { "fileName": "invoice_001.pdf" }
         │
         ▼
[1] Save FileTransaction (status = RECEIVED) → MySQL
         │
         ▼
[2] Return transactionId immediately (202 Accepted)
         │
         ▼  (background thread)
[3] Fetch PDF from S3 input bucket
         │
         ▼
[4] Extract text using Apache PDFBox
         │
         ▼
[5] Parse: invoiceNumber, vendor, date, amount, lineItems
         │
         ▼
[6] Save Invoice + LineItems → MySQL
         │
         ▼
[7] Generate CSV summary using FileWriter
         │
         ▼
[8] Upload CSV to S3 output bucket
         │
         ▼
[9] Update FileTransaction (status = SUCCESS, outputFileName set)
```

---

## MySQL Tables

### file_transaction
| Column           | Type         | Description                        |
|------------------|--------------|------------------------------------|
| id               | BIGINT (PK)  | Auto increment                     |
| transaction_id   | VARCHAR(100) | UUID, unique per request           |
| input_file_name  | VARCHAR(255) | PDF filename from request          |
| output_file_name | VARCHAR(255) | Generated CSV path in S3           |
| status           | ENUM         | RECEIVED / PROCESSING / SUCCESS / FAILED |
| error_message    | TEXT         | Populated if status = FAILED       |
| created_at       | DATETIME     | Record creation time               |
| updated_at       | DATETIME     | Last status update time            |

### invoice
| Column         | Type          | Description              |
|----------------|---------------|--------------------------|
| invoice_number | VARCHAR(100)  | Extracted from PDF       |
| vendor_name    | VARCHAR(255)  | Extracted from PDF       |
| invoice_date   | DATE          | Extracted from PDF       |
| total_amount   | DECIMAL(15,2) | Extracted from PDF       |
| currency       | VARCHAR(10)   | Extracted from PDF       |

### invoice_line_item
| Column      | Type          | Description         |
|-------------|---------------|---------------------|
| description | VARCHAR(500)  | Item description    |
| quantity    | INT           | Item quantity       |
| unit_price  | DECIMAL(15,2) | Price per unit      |
| total_price | DECIMAL(15,2) | quantity × price    |

---

## Setup & Run

### 1. Create MySQL Database
```sql
CREATE DATABASE invoice_db;
```
Then run `src/main/resources/schema.sql` to create all tables.

### 2. Configure application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/invoice_db
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

aws.accessKeyId=YOUR_AWS_ACCESS_KEY
aws.secretKey=YOUR_AWS_SECRET_KEY
aws.region=ap-south-1
aws.s3.input-bucket=your-input-bucket
aws.s3.output-bucket=your-output-bucket
```

### 3. Create S3 Buckets (AWS Console)
- Create bucket: `invoice-input-bucket` (upload your test PDFs here)
- Create bucket: `invoice-output-bucket` (processed CSVs will land here)

### 4. Run the Application
```bash
./gradlew bootRun
```

### 5. Run Tests
```bash
./gradlew test
```

---

## API Reference

### Process Invoice
```
POST http://localhost:8080/api/invoices/process
Content-Type: application/json

{
  "fileName": "invoice_2024_001.pdf"
}
```

**Response (202 Accepted):**
```json
{
  "transactionId": "a1b2c3d4-...",
  "status": "RECEIVED",
  "message": "Processing started. Use /api/invoices/status/a1b2c3d4-... to track progress."
}
```

---

### Check Status
```
GET http://localhost:8080/api/invoices/status/{transactionId}
```

**Response (200 OK):**
```json
{
  "transactionId": "a1b2c3d4-...",
  "inputFileName": "invoice_2024_001.pdf",
  "outputFileName": "output/a1b2c3d4-..._invoice_summary.csv",
  "status": "SUCCESS",
  "errorMessage": null,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:05"
}
```

**Possible status values:** `RECEIVED` → `PROCESSING` → `SUCCESS` / `FAILED`

---

## Sample PDF Format (for testing)

Your PDF should contain text like this for best extraction results:

```
Invoice No: INV-2024-001
Date: 2024-01-15
Vendor: Acme Solutions Pvt Ltd
Currency: INR

Web Development     5    2000.00    10000.00
UI Design           3    1500.00     4500.00
Testing             2    1000.00     2000.00

Total Amount: 16500.00
```

---

## Key Design Decisions

- **Async processing** — API returns immediately with `202 Accepted`. Processing runs in background thread pool. Client polls `/status` to track progress.
- **Transaction tracking** — Every request is tracked in `file_transaction` table with timestamps and status.
- **Error resilience** — If any stage fails, status is set to `FAILED` with the error message stored in DB.
- **FileWriter for CSV** — Intentionally uses Java's built-in `FileWriter` to demonstrate core Java file I/O alongside the PDFBox library.

---

## Author
Built with Spring Boot 2.7.18 + Java 8 + Gradle + MySQL + AWS S3


#FILE REGEX

What your Regex expects vs What this PDF has
FieldYour Regex looks forThis PDF hasInvoice NumberInvoice No: / Invoice #:Invoice IDVendor NameVendor: / From:Vendor NameTotal AmountTotal Amount: / Amount Due:Grand TotalDateYYYY-MM-DD or DD/MM/YYYY28-May-2026CurrencyUSD/INR/EUR etcEUR ✅ works
