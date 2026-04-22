# Kitchen Ingestion Service

A Spring Boot 3.2 / Java 21 financial data ingestion pipeline for cloud kitchen operations.

Automatically parses Swiggy order reports, Zomato order and invoice reports, and offline orders from CSV/Excel files — from local filesystem or AWS S3 — and stores them in PostgreSQL.

## Quick Start

```bash
# 1. Create database
psql -U postgres -c "CREATE DATABASE cloudkitchen;"
psql -U postgres -d cloudkitchen -f src/main/resources/db/migration/V1__init.sql

# 2. Set credentials
export DB_USER=postgres
export DB_PASSWORD=yourpassword

# 3. Run
mvn spring-boot:run

# 4. Trigger ingestion
curl -X POST http://localhost:8080/api/v1/ingestion/run
```

## File naming convention

Files MUST follow this format:
```
{SOURCE}_{TYPE}_{YYYYMMDD}_{YYYYMMDD}.{ext}
```

Examples:
- `SWIGGY_ORDER_20260401_20260419.csv`
- `ZOMATO_INVOICE_20260323_20260329.xlsx`
- `ZOMATO_ORDER_20260412_20260418.csv`
- `OFFLINE_ORDER_20260501_20260531.csv`

## Supported sources

| Source | Type | Format | Table |
|--------|------|--------|-------|
| SWIGGY | ORDER | CSV | swiggy_orders |
| ZOMATO | ORDER | CSV | zomato_orders |
| ZOMATO | INVOICE | XLSX (Order Level sheet) | zomato_invoices |
| OFFLINE | ORDER | CSV (flexible) | offline_orders |

## Tech stack

- Java 21, Spring Boot 3.2.4
- PostgreSQL with JDBC Template (no JPA)
- AWS SDK v2 for S3
- Apache POI for Excel, Apache Commons CSV for CSV
- Lombok

## Full documentation

See [docs/APPLICATION.md](docs/APPLICATION.md) for:
- Complete flow diagrams
- Database schema explanation
- Design patterns used
- API reference
- How to add a new platform
- Monitoring queries for Grafana

## API

`POST /api/v1/ingestion/run` — Full scan all paths  
`POST /api/v1/ingestion/local?filePath=&sourceType=&processingType=` — Single local file  
`POST /api/v1/ingestion/s3?bucket=&key=&sourceType=` — Single S3 object  
`POST /api/v1/ingestion/paths` — Register new path at runtime  
`GET  /api/v1/ingestion/paths` — List all paths  
`GET  /api/v1/ingestion/status` — Health check (Grafana)  
