# Cloud Kitchen Financial Ingestion Service
## Application Document

> **Audience:** This document is written for anyone joining the project — developers, business stakeholders, or operations staff — with no prior knowledge of the codebase.

---

## Table of Contents

1. [What does this application do?](#1-what-does-this-application-do)
2. [Why was it built?](#2-why-was-it-built)
3. [How data flows through the system](#3-how-data-flows-through-the-system)
4. [File naming rules — the single most important convention](#4-file-naming-rules)
5. [Supported file types and platforms](#5-supported-file-types-and-platforms)
6. [Database tables explained](#6-database-tables-explained)
7. [What happens when you drop a file](#7-what-happens-when-you-drop-a-file)
8. [Duplicate protection — idempotency explained](#8-duplicate-protection)
9. [Confidence scoring — how the system flags bad data](#9-confidence-scoring)
10. [Where files can come from — LOCAL and S3](#10-where-files-can-come-from)
11. [Design patterns used — and why](#11-design-patterns-used)
12. [Package structure](#12-package-structure)
13. [How to run the application](#13-how-to-run-the-application)
14. [API reference](#14-api-reference)
15. [How to add a new platform](#15-how-to-add-a-new-platform)
16. [Edge cases the system handles](#16-edge-cases-the-system-handles)
17. [Monitoring and Grafana](#17-monitoring-and-grafana)
18. [Glossary](#18-glossary)

---

## 1. What does this application do?

This is a **financial data ingestion service** for a cloud kitchen business.

Every week or month, platforms like **Swiggy** and **Zomato** export reports about orders placed through their apps. These reports come as CSV or Excel files. The kitchen also receives reports from its own bank account and will eventually track **offline/walk-in orders** too.

**The problem this solves:**
- These files come in different formats, with different column names, from different platforms.
- Someone has to open each file, understand the data, and load it into a database so it can be analysed.
- Done manually, this is error-prone, slow, and completely un-auditable.

**What this application does instead:**
- You drop a file into a folder (or upload it to AWS S3).
- The application automatically detects what kind of file it is from the filename.
- It parses every row, validates the data, and stores it in the correct database table.
- If anything looks wrong, it flags the record for review instead of silently saving bad data.
- Every file processed is permanently recorded with its status — so you always know what was processed, when, and whether it succeeded.

---

## 2. Why was it built?

### The business problem

A cloud kitchen earns money through delivery platforms (Swiggy, Zomato) and sometimes directly from customers. Each platform charges different commissions, deducts taxes, and pays out on different schedules.

Without a system to track this, answering basic questions becomes very hard:
- "How much did Zomato actually pay us last month after all deductions?"
- "Which orders were cancelled and who bore the cost?"
- "What is our actual margin per order after platform fees and GST?"

### The technical approach

Instead of building a rigid system that only works for today's file format, we built a **pluggable, extensible pipeline**. Each platform gets its own parser. Adding a new platform or changing a column mapping requires touching exactly one file — nothing else changes.

---

## 3. How data flows through the system

Here is the complete journey of a file from the moment it arrives to the moment its data is queryable:

```
                        ┌─────────────────────────────────┐
                        │   FILE ARRIVES (Local or S3)    │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   1. FILENAME PARSER            │
                        │   Reads: ZOMATO_INVOICE_        │
                        │   20260323_20260329.xlsx        │
                        │   Extracts: source=ZOMATO       │
                        │            type=INVOICE         │
                        │            startDate=2026-03-23 │
                        │            endDate=2026-03-29   │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   2. DUPLICATE CHECK            │
                        │   Has a ZOMATO INVOICE for      │
                        │   this date range been          │
                        │   processed before?             │
                        │   YES → REJECT (log & stop)     │
                        │   NO  → CONTINUE                │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   3. FILE METADATA RECORD       │
                        │   Created in DB: status=        │
                        │   PROCESSING. This is a         │
                        │   permanent audit record.       │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   4. FILE READER                │
                        │   Reads bytes from LOCAL path   │
                        │   or downloads from AWS S3      │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   5. ROUTER                     │
                        │   source=ZOMATO + type=INVOICE  │
                        │   → ZomatoInvoiceProcessor      │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   6. PROCESSOR (Template)       │
                        │   a. Extract rows from file     │
                        │   b. Check all dates present    │
                        │      (if any null → REVIEW)     │
                        │   c. Map each row to a model    │
                        │   d. Score each record          │
                        │   e. Insert all rows (1 tx)     │
                        │      Any error → FULL ROLLBACK  │
                        └───────────────┬─────────────────┘
                                        │
                                        ▼
                        ┌─────────────────────────────────┐
                        │   7. UPDATE FILE METADATA       │
                        │   status = PROCESSED / FAILED   │
                        │   / REVIEW                      │
                        │   rows_processed, rows_failed   │
                        └─────────────────────────────────┘
```

---

## 4. File naming rules

**This is the most important convention in the entire system.** The application reads the filename — not the file contents — to decide how to process a file.

### Format

```
{SOURCE}_{PROCESSING_TYPE}_{YYYYMMDD}_{YYYYMMDD}.{extension}
```

| Part | What it means | Examples |
|------|--------------|---------|
| SOURCE | Which platform the file is from | SWIGGY, ZOMATO, OFFLINE |
| PROCESSING_TYPE | What kind of data is in the file | ORDER, INVOICE |
| First date | Start of the reporting period | 20260401 = 1st April 2026 |
| Second date | End of the reporting period | 20260419 = 19th April 2026 |
| Extension | File format | .csv, .xlsx |

### Valid examples

```
SWIGGY_ORDER_20260401_20260419.csv
ZOMATO_INVOICE_20260323_20260329.xlsx
ZOMATO_ORDER_20260412_20260418.csv
OFFLINE_ORDER_20260501_20260531.csv
```

### Invalid examples (these will be rejected)

```
swiggy_orders.csv              ← lowercase not recognised
swiggy_april.xlsx              ← no date range
ZOMATO_20260401_20260419.csv   ← missing processing type
report.xlsx                    ← no structure at all
```

### Why this matters

The filename-driven routing means:
- No manual configuration per file
- No UI to specify "this is a Swiggy order file"
- Just rename and drop — the system handles everything

---

## 5. Supported file types and platforms

| Filename Pattern | File Format | Data Stored In | Status |
|-----------------|-------------|----------------|--------|
| SWIGGY_ORDER_*.csv | CSV | swiggy_orders table | Active |
| SWIGGY_INVOICE_*.xlsx | Excel | (coming soon — pending PAN mapping) | Placeholder |
| ZOMATO_ORDER_*.csv | CSV | zomato_orders table | Active |
| ZOMATO_INVOICE_*.xlsx | Excel (Order Level sheet) | zomato_invoices table | Active |
| OFFLINE_ORDER_*.csv | CSV | offline_orders table | Active (pluggable schema) |

### Swiggy ORDER file — what it contains
Order-level operational data: order ID, timestamps (ordered, accepted, delivered), bill amount, GST breakdown (SGST/CGST/IGST on items, packaging, service charge), discounts, cancellation details, and a composite column listing all items ordered.

### Zomato ORDER file — what it contains
Order-level data including restaurant and city details, customer information, items, delivery distance and type, financial totals, operational metrics (KPT duration = kitchen preparation time, rider wait time), and customer ratings.

### Zomato INVOICE file — what it contains
The full payout settlement breakdown — 61 columns covering: subtotal, packaging charges, platform commissions, GST components, TCS (Tax Collected at Source), TDS 194O, long-distance fees, adjustments, credit/debit notes, and the final order-level payout. This is the financial source of truth for what Zomato actually pays the restaurant.

### OFFLINE ORDER file — what it contains
Currently flexible (schema TBD). All columns are stored as-is in a JSON field. When the final offline order format is agreed, only the POJO and one method need to change.

---

## 6. Database tables explained

### `file_metadata` — The control tower

Every file processed gets exactly one row in this table. Think of it as the permanent log of every file the system has ever seen.

| Column | What it stores |
|--------|---------------|
| id | Auto-incrementing primary key |
| file_name | Original filename |
| source | SWIGGY / ZOMATO / OFFLINE |
| processing_type | ORDER / INVOICE |
| file_origin | LOCAL / S3 |
| idempotency_key | SHA-256 fingerprint — prevents duplicates |
| report_start_date | Earliest date from the filename |
| report_end_date | Latest date from the filename |
| status | PENDING → PROCESSING → PROCESSED / FAILED / DUPLICATE / REVIEW |
| total_rows | How many data rows were in the file |
| processed_rows | How many were successfully saved |
| failed_rows | How many could not be mapped |
| error_message | What went wrong (if anything) |
| created_at | When the file was first seen |
| processed_at | When processing finished |

**Status meanings:**
- **PENDING** — File registered, not yet processed
- **PROCESSING** — Currently being read and inserted
- **PROCESSED** — All rows saved successfully
- **FAILED** — Error occurred, all rows rolled back
- **DUPLICATE** — Same date range from same platform was already processed — rejected
- **REVIEW** — File has null/missing dates in some rows — needs human inspection

---

### `swiggy_orders` — Swiggy order data

One row per order from a Swiggy ORDER file. Contains all 31 original columns from the Swiggy export, mapped to properly typed database columns (timestamps, decimals, booleans).

Key columns: order_id, order_relay_time, order_status, total_bill_amount, all GST components, item count, food_prepared flag, items_detail (raw composite column).

---

### `zomato_orders` — Zomato order data

One row per order from a Zomato ORDER file. Contains all 30 original columns including restaurant metadata, delivery metrics (KPT duration, rider wait time), financial totals, and customer information.

Key columns: order_id, order_placed_at, order_status, bill_subtotal, total, kpt_duration_minutes, rating.

---

### `zomato_invoices` — Zomato payout settlement

One row per order line from the Zomato INVOICE file (Order Level sheet). This is the widest table — all 61 financial columns preserved exactly as Zomato exports them.

Key columns: order_id, order_date, subtotal, net_order_value, total_commissionable_value, base_service_fee, tcs_igst_amount, tds_194o_amount, order_level_payout, settlement_status, bank_utr.

**Why so many columns?** Every column in the Zomato invoice is a distinct financial component that affects the final payout. Collapsing them into fewer columns would hide data needed for tax filing, reconciliation, and commission auditing.

---

### `offline_orders` — Walk-in/phone orders

One row per offline order. Since the offline order format is not yet finalised, each row stores the entire CSV row as a JSON blob alongside a system-generated order ID and a hash of the row contents (for deduplication).

When the offline format is agreed: update the POJO, update one method, add DB columns. Nothing else changes.

---

### How the tables relate

```
file_metadata (1)
    ├── (many) swiggy_orders     [file_metadata_id → file_metadata.id]
    ├── (many) zomato_orders     [file_metadata_id → file_metadata.id]
    ├── (many) zomato_invoices   [file_metadata_id → file_metadata.id]
    └── (many) offline_orders    [file_metadata_id → file_metadata.id]

Cross-table join (analytics):
    zomato_orders JOIN zomato_invoices ON order_id
    → Gives you: what did the customer pay + what did Zomato actually settle
```

---

## 7. What happens when you drop a file

### Automated trigger (scheduled)

The application checks configured folder paths every Monday at 8:00 AM (configurable). If no files are found: logs "No files present to process" and stops. If files are found: processes them in priority order (paths that have historically had more files are checked first).

### Manual trigger (API)

```bash
# Trigger a full scan of all configured paths
curl -X POST http://localhost:8080/api/v1/ingestion/run

# Process one specific local file
curl -X POST "http://localhost:8080/api/v1/ingestion/local?filePath=/data/swiggy/SWIGGY_ORDER_20260401_20260419.csv&sourceType=SWIGGY&processingType=ORDER"
```

### Step-by-step walkthrough

Let's trace exactly what happens when `ZOMATO_INVOICE_20260323_20260329.xlsx` is dropped into `/data/zomato/inbox/`:

**Step 1 — Discovery**
The scheduler (or manual API call) asks FileDiscoveryService to list all files in all configured paths. It finds our file. It checks the file_metadata table — this file's path has not been processed before, so it's included.

**Step 2 — Filename parsing**
FileNameParser reads the filename:
- `ZOMATO` → source = ZOMATO
- `INVOICE` → processing type = INVOICE
- `20260323` → start date = 23 March 2026
- `20260329` → end date = 29 March 2026
- Computes SHA-256 fingerprint: `ZOMATO_INVOICE_2026-03-23_2026-03-29` → unique key

**Step 3 — Duplicate check**
Queries file_metadata: "Has any file with this SHA-256 key been processed?" Answer: No. Continue.

**Step 4 — Audit record created**
Inserts a row into file_metadata with status=PROCESSING. This row always gets committed — even if processing later fails — so we always have an audit trail.

**Step 5 — File read**
Since the path starts with `/data/`, LocalFileSource reads the file bytes into memory.

**Step 6 — Routing**
FileProcessingRouter receives (ZOMATO, INVOICE) → selects ZomatoInvoiceProcessor.

**Step 7 — Processing (inside ZomatoInvoiceProcessor)**
- Opens the Excel file, finds the "Order Level" sheet
- Reads row 1 as headers, rows 2+ as data
- Checks every row: does the "Order date" column have a value? If any row has a blank date → throw exception → file marked REVIEW → all DB changes rolled back
- Maps each row to a ZomatoInvoice object (61 fields)
- Scores each record (confidence scoring — see Section 9)
- Opens a database transaction and inserts all rows at once
- If any insert fails → entire transaction rolled back → zero rows committed
- Transaction commits successfully → all rows saved

**Step 8 — Completion**
Updates file_metadata: status=PROCESSED, total_rows=47, processed_rows=47, processed_at=NOW().

**The data is now queryable.**

---

## 8. Duplicate protection

### The problem

Without protection, the same file could be processed twice — either by accident (someone drops the file again) or by a re-export from the platform (same date range, slightly different data). Either way, you'd end up with double entries in your financials.

### How it works

When a file is parsed, the system creates an **idempotency key** — a unique fingerprint for that file's combination of:
- Platform (SWIGGY / ZOMATO / OFFLINE)
- Type (ORDER / INVOICE)
- Start date
- End date

The fingerprint is computed as a SHA-256 hash. For example:

```
Input:  "ZOMATO_INVOICE_2026-03-23_2026-03-29"
Output: "a3f7c2d1e8b4..." (64-character hex string)
```

This key is stored in the `idempotency_key` column of file_metadata with a UNIQUE constraint. The database enforces that no two files with the same key can exist.

### What counts as a duplicate

**Same platform + same type + same date range = duplicate.**

This means:
- `ZOMATO_INVOICE_20260323_20260329.xlsx` uploaded twice → second one rejected
- `ZOMATO_INVOICE_20260323_20260329_corrected.xlsx` → also rejected (same dates = same key)
- `ZOMATO_INVOICE_20260330_20260405.xlsx` → accepted (different date range = different key)

### Handling corrections

If Zomato sends a corrected settlement for the same week, the system rejects it as a duplicate. To reprocess:
1. Delete the existing file_metadata record (and cascade-delete the data rows) manually via SQL
2. Drop the corrected file
3. The system now treats it as a new file

---

## 9. Confidence scoring

### What it is

Every record that gets parsed is assigned a **confidence score** — a number between 0 and 1 that represents how complete and trustworthy the data looks.

**Score of 1.0** = all expected fields present, values look reasonable.
**Score below 0.70** = something is missing or suspicious → record goes to review_queue instead of the main table.

### Scoring rules per source

**Swiggy Orders**
| Problem | Points deducted |
|---------|----------------|
| OrderID is missing | −0.30 |
| Order timestamp missing | −0.20 |
| Total bill amount missing | −0.20 |
| Bill amount is negative | −0.15 |
| Item count is zero or missing | −0.10 |
| Order status missing | −0.05 |

**Zomato Orders**
| Problem | Points deducted |
|---------|----------------|
| Order ID missing | −0.30 |
| Order placed timestamp missing | −0.20 |
| Bill subtotal missing | −0.20 |
| Subtotal is negative | −0.15 |
| Total amount missing | −0.10 |
| Order status missing | −0.05 |

**Zomato Invoices**
| Problem | Points deducted |
|---------|----------------|
| Order ID missing | −0.30 |
| Order date missing | −0.20 |
| Order level payout missing | −0.20 |
| Subtotal missing | −0.10 |
| Settlement status missing | −0.10 |
| Both net deductions and net additions missing | −0.10 |

**Offline Orders**
| Problem | Points deducted |
|---------|----------------|
| Order ID missing | −0.30 |
| Raw data empty | −0.50 |
| Hash key missing | −0.20 |

### What happens to flagged records

Records with confidence score below 0.70 are inserted into the `review_queue` table (not the main data table) along with the list of flags explaining what was wrong. A human or automated job then reviews these and either corrects them or rejects them.

---

## 10. Where files can come from

The system supports two file origins transparently:

### LOCAL (local filesystem)

Files placed in a directory on the machine where the application runs.

```yaml
# application.yml
app:
  ingestion:
    scan-paths:
      - type: LOCAL
        location: /data/swiggy/inbox
        source-hint: SWIGGY
        enabled: true
```

The application will scan `/data/swiggy/inbox/` and process any new CSV or Excel files it finds.

### S3 (AWS S3 bucket)

Files uploaded to an S3 bucket prefix.

```yaml
app:
  ingestion:
    scan-paths:
      - type: S3
        location: s3://my-kitchen-reports/zomato/
        source-hint: ZOMATO
        enabled: true
```

Credentials are resolved automatically using AWS DefaultCredentialsProvider:
1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
2. AWS credentials file (~/.aws/credentials)
3. EC2/ECS instance role (when deployed on AWS)

### Priority ordering

The system tracks which paths have historically had more files (using the file_metadata table). Paths with more activity are scanned first. This ensures the most important data sources are processed earliest.

### Adding new paths at runtime

No restart needed. Use the API:

```bash
curl -X POST "http://localhost:8080/api/v1/ingestion/paths?inputSource=LOCAL&location=/data/new-source&sourceType=SWIGGY"
```

---

## 11. Design patterns used

This section explains the engineering decisions in plain terms.

### 1. Strategy Pattern — "Swap the engine, keep the frame"

**Problem:** LOCAL files and S3 files are read differently. Swiggy, Zomato, and Offline data are parsed differently.

**Solution:** Define a common interface (FileSourceStrategy, FileProcessor). Each platform or source type gets its own implementation. The rest of the system only talks to the interface — it doesn't care which implementation it's using.

**Benefit:** Adding AWS GCS or an SFTP source later = one new class. Zero changes to existing code.

---

### 2. Template Method Pattern — "Same recipe, different ingredients"

**Problem:** Every file type follows the same pipeline: extract rows → validate dates → map to model → score → save. But the details of each step differ per platform.

**Solution:** AbstractFileProcessor defines the fixed pipeline as a `final` method. Each platform processor overrides only the steps that differ (extractRows, mapRow, extractDateValue).

**Benefit:** Bug fix in the pipeline (e.g., rollback logic) = fix in one place, all processors inherit the fix.

---

### 3. Factory / Router Pattern — "Post office routing"

**Problem:** Given a file named ZOMATO_INVOICE_*.xlsx, which class should process it?

**Solution:** FileProcessingRouter receives (ZOMATO, INVOICE) and finds the matching processor from the list of all registered processors.

**Benefit:** Adding ZomatoInvoiceProcessor for a new format = annotate the class with @Component. The router picks it up automatically. No routing table to maintain.

---

### 4. Repository Pattern — "Separate the business logic from the database"

**Problem:** If SQL queries are scattered throughout the business logic, changing the database becomes a nightmare.

**Solution:** Every table has a dedicated Repository class. All SQL lives in the repository. Processors call `repository.batchInsert(records)` without knowing anything about SQL.

**Benefit:** Adding a column to zomato_invoices = update ZomatoInvoice model + ZomatoInvoiceRepository only.

---

### 5. Registry Pattern — "A live list of paths"

**Problem:** Scan paths are configured at startup, but the business may need to add new paths without restarting the server.

**Solution:** PathRegistry holds all configured paths in a thread-safe map. Paths can be added, disabled, or re-prioritised at runtime via the REST API.

**Benefit:** Operations team can add an emergency scan path at 2 AM without redeploying the application.

---

### 6. Observer Pattern — "Notify interested parties without coupling"

**Problem:** When a file is processed, multiple things need to know: the audit log needs updating, future alerting systems need notifying, metrics need recording.

**Solution:** IngestionEventPublisher fires Spring Application Events (FileDiscoveredEvent, FileProcessedEvent, IngestionFailedEvent). Any listener can subscribe independently.

**Benefit:** Adding a Slack alert on failure = create one @EventListener class. Nothing else changes.

---

### 7. Chain of Responsibility — "Pass it down the chain until someone handles it"

**Problem:** A file arriving at the system could be PDF, XLSX, XLS, or CSV. How to detect which?

**Solution:** FormatDetectionChain links handlers in order: PdfHandler → XlsxHandler → XlsHandler → CsvHandler → UnknownHandler. Each handler checks if it can handle the file; if not, it passes to the next.

---

### 8. Builder Pattern — "Build complex objects cleanly"

**Problem:** Domain objects (SwiggyOrder, ZomatoInvoice) have 30–61 fields. Creating them via constructors is unreadable and error-prone.

**Solution:** Lombok @Builder generates a fluent builder for every model class.

```java
// Instead of: new SwiggyOrder(null, "fname", "SWIGGY", ...)
SwiggyOrder.builder()
    .fileName("fname")
    .orderId("ORD123")
    .totalBillAmount(new BigDecimal("450.00"))
    .build();
```

---

## 12. Package structure

```
src/main/java/com/cloudkitchen/ingestion/
│
├── IngestionApplication.java          ← Spring Boot entry point
│
├── config/
│   ├── AppProperties.java             ← Reads application.yml scan-paths config
│   ├── S3Config.java                  ← Creates the AWS S3 client bean
│   └── JdbcConfig.java               ← Activates config properties
│
├── model/
│   ├── enums/
│   │   ├── SourceType.java            ← SWIGGY, ZOMATO, OFFLINE
│   │   ├── ProcessingType.java        ← ORDER, INVOICE
│   │   ├── FileOrigin.java            ← LOCAL, S3
│   │   └── FileStatus.java            ← PENDING, PROCESSING, PROCESSED, FAILED, DUPLICATE, REVIEW
│   ├── FileMetadata.java              ← Represents one row in file_metadata table
│   ├── SwiggyOrder.java              ← Represents one Swiggy order row
│   ├── ZomatoOrder.java              ← Represents one Zomato order row
│   ├── ZomatoInvoice.java            ← Represents one Zomato invoice row (61 fields)
│   ├── OfflineOrder.java             ← Represents one offline order (flexible)
│   ├── DateRange.java                ← Holds report start/end dates
│   ├── ProcessingResult.java         ← Summary of what happened during processing
│   └── IngestionResult.java          ← API response object
│
├── filename/
│   ├── ParsedFileName.java            ← Result of parsing a filename
│   └── FileNameParser.java            ← Parses SOURCE_TYPE_YYYYMMDD_YYYYMMDD format
│
├── source/
│   ├── FileSourceStrategy.java        ← Interface: listFiles, openStream, computeHash
│   ├── LocalFileSource.java           ← Reads from local filesystem
│   └── S3FileSource.java              ← Reads from AWS S3
│
├── discovery/
│   ├── PathConfig.java                ← One configured scan path
│   ├── PathRegistry.java              ← Thread-safe store of all paths (runtime-editable)
│   └── FileDiscoveryService.java      ← Finds all unprocessed files across all paths
│
├── parser/
│   ├── FileProcessor.java             ← Interface: process(bytes, meta, fileMetadataId)
│   ├── AbstractFileProcessor.java     ← Template method: fixed pipeline, pluggable steps
│   ├── FormatDetectionChain.java      ← PDF→XLSX→XLS→CSV→Unknown detection chain
│   ├── swiggy/
│   │   └── SwiggyOrderProcessor.java  ← Parses SWIGGY_ORDER CSV files
│   ├── zomato/
│   │   ├── ZomatoOrderProcessor.java  ← Parses ZOMATO_ORDER CSV files
│   │   └── ZomatoInvoiceProcessor.java← Parses ZOMATO_INVOICE XLSX (Order Level sheet)
│   └── offline/
│       └── OfflineOrderProcessor.java ← Parses OFFLINE_ORDER CSV (pluggable schema)
│
├── router/
│   └── FileProcessingRouter.java      ← Routes (SourceType, ProcessingType) to processor
│
├── repository/
│   ├── FileMetadataRepository.java    ← JDBC: file_metadata table
│   ├── SwiggyOrderRepository.java     ← JDBC: swiggy_orders table
│   ├── ZomatoOrderRepository.java     ← JDBC: zomato_orders table
│   ├── ZomatoInvoiceRepository.java   ← JDBC: zomato_invoices table
│   └── OfflineOrderRepository.java    ← JDBC: offline_orders table
│
├── service/
│   ├── ConfidenceScorer.java          ← Scores each record for data quality
│   ├── IngestionEventPublisher.java   ← Fires Spring events (discovered/processed/failed)
│   └── IngestionOrchestrator.java     ← Facade: coordinates the entire pipeline
│
├── scheduler/
│   └── IngestionScheduler.java        ← Runs full ingestion on a cron schedule
│
├── controller/
│   └── IngestionController.java       ← REST API endpoints
│
└── exception/
    ├── IngestionException.java         ← Base exception for all ingestion errors
    └── UnsupportedFormatException.java ← Thrown when no processor handles the file
```

---

## 13. How to run the application

### Prerequisites

- Java 21 or later
- Maven 3.9 or later
- PostgreSQL 14 or later running on localhost:5432

### Step 1 — Create the database and tables

```bash
psql -U postgres -c "CREATE DATABASE cloudkitchen;"
psql -U postgres -d cloudkitchen -f src/main/resources/db/migration/V1__init.sql
```

### Step 2 — Create inbox directories

```bash
mkdir -p /data/swiggy/inbox
mkdir -p /data/zomato/inbox
mkdir -p /data/bank/inbox
mkdir -p /data/manual/inbox
```

### Step 3 — Set environment variables

```bash
export DB_USER=postgres
export DB_PASSWORD=yourpassword

# Only needed if using S3:
export AWS_REGION=ap-south-1
export AWS_ACCESS_KEY_ID=YOUR_KEY
export AWS_SECRET_ACCESS_KEY=YOUR_SECRET
```

### Step 4 — Build and run

```bash
mvn clean package -DskipTests
java -jar target/kitchen-ingestion-1.0.0.jar
```

### Step 5 — Process your first file

```bash
# Copy a file to the inbox
cp SWIGGY_ORDER_20260401_20260419.csv /data/swiggy/inbox/

# Trigger ingestion
curl -X POST http://localhost:8080/api/v1/ingestion/run

# Check what happened
psql -U postgres -d cloudkitchen -c "SELECT file_name, status, processed_rows FROM file_metadata;"
```

---

## 14. API reference

### POST /api/v1/ingestion/run
**What it does:** Scans all configured paths (LOCAL and S3) and processes any new files found.

**Response:**
```json
[
  {
    "sourceFile": "/data/swiggy/inbox/SWIGGY_ORDER_20260401_20260419.csv",
    "totalParsed": 42,
    "committed": 42,
    "queued": 0,
    "skipped": 0,
    "errors": []
  }
]
```

---

### POST /api/v1/ingestion/local
**What it does:** Processes one specific local file.

**Parameters:**
- `filePath` — absolute path to the file
- `sourceType` — SWIGGY / ZOMATO / OFFLINE
- `processingType` — ORDER / INVOICE

**Example:**
```bash
curl -X POST "http://localhost:8080/api/v1/ingestion/local?filePath=/data/zomato/ZOMATO_INVOICE_20260323_20260329.xlsx&sourceType=ZOMATO&processingType=INVOICE"
```

---

### POST /api/v1/ingestion/s3
**What it does:** Downloads and processes one specific S3 object.

**Parameters:**
- `bucket` — S3 bucket name
- `key` — S3 object key (full path)
- `sourceType` — SWIGGY / ZOMATO / OFFLINE

---

### POST /api/v1/ingestion/paths
**What it does:** Registers a new scan path at runtime (no restart required).

**Parameters:**
- `inputSource` — LOCAL / S3
- `location` — directory path or s3://bucket/prefix
- `sourceType` — SWIGGY / ZOMATO / OFFLINE

---

### GET /api/v1/ingestion/paths
**What it does:** Lists all configured scan paths.

---

### DELETE /api/v1/ingestion/paths/{id}
**What it does:** Disables a scan path (it is not deleted, just excluded from future scans).

---

### GET /api/v1/ingestion/status
**What it does:** Health check endpoint. Returns UP status and path counts. Used by Grafana.

```json
{
  "status": "UP",
  "activePaths": 4,
  "totalPaths": 6
}
```

---

## 15. How to add a new platform

Say you want to add **Dunzo** order reports.

**Step 1 — Add the enum value**
```java
// model/enums/SourceType.java
public enum SourceType { SWIGGY, ZOMATO, OFFLINE, DUNZO }  // add DUNZO
```

**Step 2 — Create the model**
```java
// model/DunzoOrder.java
@Data @Builder
public class DunzoOrder {
    private Long fileMetadataId;
    private String fileName;
    // ... add all Dunzo-specific columns
}
```

**Step 3 — Create the repository**
```java
// repository/DunzoOrderRepository.java
@Repository
public class DunzoOrderRepository {
    public void batchInsert(List<DunzoOrder> records) { ... }
}
```

**Step 4 — Create the processor**
```java
// parser/dunzo/DunzoOrderProcessor.java
@Component
public class DunzoOrderProcessor extends AbstractFileProcessor<DunzoOrder> {
    @Override public SourceType supportedSource() { return SourceType.DUNZO; }
    @Override public ProcessingType supportedType() { return ProcessingType.ORDER; }
    // implement extractRows, mapRow, extractDateValue, scoreRecord, persistBatch
}
```

**Step 5 — Add the database table**
```sql
CREATE TABLE dunzo_orders ( ... );
```

**Step 6 — Configure the scan path**
```yaml
app:
  ingestion:
    scan-paths:
      - type: LOCAL
        location: /data/dunzo/inbox
        source-hint: DUNZO
        enabled: true
```

**That's it.** The router, orchestrator, scheduler, and controller all pick it up automatically.

---

## 16. Edge cases the system handles

| Situation | What happens |
|-----------|-------------|
| File dropped twice (exact re-upload) | Second file rejected as duplicate via idempotency key |
| Same date range re-exported with corrections | Rejected as duplicate. Manual DB cleanup required before reprocessing |
| File with missing dates in some rows | Entire file marked REVIEW, all DB changes rolled back |
| Row 300 of 500 fails to parse | Entire batch rolled back, file marked FAILED, 0 rows committed |
| File with no data rows (header only) | Exception thrown, file marked FAILED |
| Filename doesn't follow convention | Rejected at parse stage, logged, not inserted into file_metadata |
| S3 bucket is empty | Logs "No files present to process", continues normally |
| Local directory doesn't exist | Logs warning, continues scanning other paths |
| Two instances running simultaneously (future) | SHA-256 idempotency key + DB UNIQUE constraint prevents double-processing |
| Column name changed in Swiggy export | Mapped column returns null → confidence score drops → rows flagged for review |

---

## 17. Monitoring and Grafana

All API endpoints return JSON. Connect Grafana to query:

**File processing history:**
```sql
SELECT source, processing_type, status, COUNT(*), MAX(processed_at)
FROM file_metadata
GROUP BY source, processing_type, status
ORDER BY MAX(processed_at) DESC;
```

**Low confidence records pending review:**
```sql
SELECT COUNT(*), source_type
FROM review_queue
WHERE resolved = FALSE
GROUP BY source_type;
```

**Payout trend (Zomato):**
```sql
SELECT order_date, SUM(order_level_payout) AS daily_payout
FROM zomato_invoices
GROUP BY order_date
ORDER BY order_date;
```

**Failed files:**
```sql
SELECT file_name, error_message, created_at
FROM file_metadata
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

---

## 18. Glossary

| Term | Meaning |
|------|---------|
| **Ingestion** | Reading data from a file and storing it in the database |
| **Idempotency** | The property of an operation that can be safely repeated without changing the result. If you process the same file twice, the second attempt has no effect. |
| **Idempotency key** | A unique fingerprint (SHA-256 hash) that identifies a file by its source + type + date range. Stored in the database. Prevents duplicates. |
| **Confidence score** | A number between 0 and 1 indicating how complete and trustworthy a data record is. |
| **Processing type** | What kind of data is in the file: ORDER (operational data) or INVOICE (financial settlement data) |
| **File origin** | Where the file came from: LOCAL (filesystem) or S3 (AWS cloud storage) |
| **Rollback** | Undoing all database changes for a file if any error occurs. Ensures the database is never left in a half-written state. |
| **TCS** | Tax Collected at Source — Zomato deducts this from restaurant payouts on behalf of the government |
| **TDS 194O** | Tax Deducted at Source under section 194O — another tax deduction on e-commerce operator payouts |
| **KPT duration** | Kitchen Preparation Time — how long it took the kitchen to prepare an order (Zomato metric) |
| **SGST / CGST / IGST** | Components of Indian GST (Goods and Services Tax). SGST = State, CGST = Central, IGST = Integrated (for interstate) |
| **UTR** | Unique Transaction Reference — the bank reference number for a payment settlement |
| **Facade** | A design pattern where one class (IngestionOrchestrator) hides all internal complexity and provides a simple interface to callers |
| **Template Method** | A design pattern where a parent class defines the steps of an algorithm, and child classes fill in the details of each step |

---

## Document history

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-21 | Initial document — covers ingestion pipeline, all tables, API, design patterns |

---

*This document is maintained alongside the codebase. When adding a new platform or changing the pipeline, update the relevant sections here.*
