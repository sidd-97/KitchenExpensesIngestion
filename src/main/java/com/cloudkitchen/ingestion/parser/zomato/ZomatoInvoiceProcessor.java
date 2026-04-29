package com.cloudkitchen.ingestion.parser.zomato;

import com.cloudkitchen.ingestion.filename.ParsedFileName;
import com.cloudkitchen.ingestion.model.ZomatoInvoice;
import com.cloudkitchen.ingestion.model.enums.ProcessingType;
import com.cloudkitchen.ingestion.model.enums.SourceType;
import com.cloudkitchen.ingestion.parser.AbstractFileProcessor;
import com.cloudkitchen.ingestion.repository.ZomatoInvoiceRepository;
import com.cloudkitchen.ingestion.service.ConfidenceScorer;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class ZomatoInvoiceProcessor extends AbstractFileProcessor<ZomatoInvoice> {

    private static final String TARGET_SHEET = "Order Level";
    private static final String DATE_COLUMN  = "Order date";

    private final ZomatoInvoiceRepository repository;

    public ZomatoInvoiceProcessor(ConfidenceScorer confidenceScorer,
                                  PlatformTransactionManager transactionManager,
                                  ZomatoInvoiceRepository repository) {
        super(confidenceScorer, transactionManager);
        this.repository = repository;
    }

    @Override public SourceType     supportedSource() { return SourceType.ZOMATO; }
    @Override public ProcessingType supportedType()   { return ProcessingType.INVOICE; }

    @Override
    protected List<Map<String, String>> extractRows(byte[] content, ParsedFileName meta) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheet(TARGET_SHEET);
            if (sheet == null) {
                throw new com.cloudkitchen.ingestion.exception.IngestionException(
                        "Sheet '" + TARGET_SHEET + "' not found in: " + meta.getOriginalFileName());
            }
            Row header = sheet.getRow(0);
            if (header == null) return rows;

            // Build column index: colIndex → header name
            Map<Integer, String> colIndex = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell c = header.getCell(i);
                if (c != null) colIndex.put(i, c.getStringCellValue().trim());
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> map = new HashMap<>();
                colIndex.forEach((col, headerName) -> {
                    Cell cell = row.getCell(col);
                    map.put(headerName, cell != null ? cellToString(cell) : "");
                });
                // skip fully empty rows
                if (map.values().stream().allMatch(String::isBlank)) continue;
                rows.add(map);
            }
        }
        return rows;
    }

    @Override
    protected ZomatoInvoice mapRow(Map<String, String> row, Long fileMetadataId,
                                   String fileName, String fileOrigin) {
        return ZomatoInvoice.builder()
                .fileMetadataId(fileMetadataId)
                .fileName(fileName)
                .fileOrigin(fileOrigin)
                .sno(intVal(row, "S.no."))
                .orderId(str(row, "Order ID"))
                .orderDate(parseDate(str(row, "Order date")))
                .weekNo(intVal(row, "Week no."))
                .restaurantName(str(row, "Res. name"))
                .restaurantId(str(row, "Res. ID"))
                .discountConstruct(str(row, "Discount construct"))
                .modeOfPayment(str(row, "Mode of payment"))
                .orderStatus(str(row, "Order status (Delivered/ Cancelled/ Rejected)"))
                .cancellationPolicyPercent(dec(row, "Cancellation policy \n(% Amount refunded back to restaurant partner)"))
                .cancellationRejectionReason(str(row, "Cancellation/ Rejection reason"))
                .cancelledRejectedState(str(row, "Cancelled/ Rejected state \n(Order status at the time it was cancelled/ rejected)"))
                .orderType(str(row, "Order type"))
                .deliveryStateCode(str(row, "Delivery state code"))
                .subtotal(dec(row, "Subtotal (items total)"))
                .packagingCharge(dec(row, "Packaging charge"))
                .deliveryChargeSelfLogistics(dec(row, "Delivery charge for restaurants on self logistics"))
                .restaurantDiscountPromo(dec(row, "Restaurant discount (Promo)"))
                .restaurantDiscountOthers(dec(row, "Restaurant discount (BOGO, Freebies, Gold, Brand pack & others)"))
                .brandPackSubscriptionFee(dec(row, "Brand pack subscription fee"))
                .deliveryChargeDiscountRelisting(dec(row, "Delivery charge discount/ Relisting discount"))
                .totalGstFromCustomers(dec(row, "Total GST collected from customers"))
                .netOrderValue(dec(row, "Net order value\n[(1) + (2) + (3) - (4) - (5) + (6) - (7) + (8)]"))
                .commissionableSubtotal(dec(row, "Commissionable value of Subtotal excluding restuarant discounts \n[(1) - (4) - (5)]"))
                .commissionablePackagingCharge(dec(row, "Commissionable value of Packaging charge"))
                .commissionableTotalGst(dec(row, "Commissionable value of Total GST collected from customers"))
                .totalCommissionableValue(dec(row, "Total commissionable value\n[(9) + (10) + (11)]"))
                .baseServiceFeePercent(dec(row, "Base service fee %"))
                .baseServiceFee(dec(row, "Base service fee\n[(12)% * (B)]"))
                .actualOrderDistanceKm(dec(row, "Actual order distance (km)"))
                .longDistanceEnablementFee(dec(row, "Long distance enablement fee"))
                .discountLongDistanceFee(dec(row, "Discount on long distance enablement fee"))
                .discountServiceFee30Cap(dec(row, "Discount on service fee due to 30% capping\n\nService fees capped at 30% of commissionable value (B)"))
                .paymentMechanismFee(dec(row, "Payment mechanism fee"))
                .serviceFeeAndPaymentMechFee(dec(row, "Service fee & payment mechanism fee\n[(13) + (15) - (16) - (17) + (18)]"))
                .taxesOnServiceFee(dec(row, "Taxes on service fee & payment mechanism fee\n[(C)*18%]"))
                .applicableAmountTcs(dec(row, "Applicable amount for TCS"))
                .applicableAmount9_5(dec(row, "Applicable amount for 9(5)"))
                .taxCollectedAtSource(dec(row, "Tax collected at source"))
                .tcsIgstAmount(dec(row, "TCS IGST amount"))
                .tds194oAmount(dec(row, "TDS 194O amount"))
                .gstPaidByZomato9_5(dec(row, "GST paid by Zomato on behalf of restaurant - under section 9(5)"))
                .gstToBePaidByRestaurant(dec(row, "GST to be paid by Restaurant partner to Govt"))
                .governmentCharges(dec(row, "Government charges\n[(19) + (22) + (23) + (24) + (25)]"))
                .customerCompensationRecoupment(dec(row, "Customer compensation/ recoupment"))
                .deliveryChargesRecovery(dec(row, "Delivery charges recovery"))
                .amountReceivedCash(dec(row, "Amount received in cash (on self delivery orders)"))
                .creditDebitNoteAdjustment(dec(row, "Credit note/ (Debit note) adjustment"))
                .promoRecoveryAdjustment(dec(row, "Promo recovery adjustment"))
                .extraInventoryAdsDeduction(dec(row, "Extra inventory ads (order level deduction)"))
                .brandLoyaltyPointsRedemption(dec(row, "Brand loyalty points redemption"))
                .expressOrderFee(dec(row, "Express order fee"))
                .otherOrderLevelDeductions(dec(row, "Other order-level deductions\n[(27) + (28) + (29) + (30) + (31) + (32) + (33) + (34)]"))
                .netDeductions(dec(row, "Net Deductions\n[(C) + (D) + (E)]"))
                .netAdditions(dec(row, "Net Additions  \n(cancellation refund for cancelled orders/ tip for kitchen staff for delivered orders)"))
                .orderLevelPayout(dec(row, "Order level Payout\n(A) - (F) + (G)"))
                .settlementStatus(str(row, "Settlement status"))
                .settlementDate(parseDate(str(row, "Settlement date")))
                .bankUtr(str(row, "Bank UTR"))
                .unsettledAmount(dec(row, "Unsettled amount"))
                .customerId(str(row, "Customer ID"))
                .build();
    }

    @Override
    protected String extractDateValue(Map<String, String> row) {
        return row.get(DATE_COLUMN);
    }

    @Override
    protected LocalDate parseToLocalDate(String rawValue) {
        return LocalDate.from(parseDate(rawValue));
    }

    @Override
    protected void scoreRecord(ZomatoInvoice record) {
        confidenceScorer.scoreZomatoInvoice(record);
    }

    @Override
    protected void persistBatch(List<ZomatoInvoice> records, Long fileMetadataId) {
        repository.batchInsert(records);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String cellToString(Cell c) {
        return switch (c.getCellType()) {
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf(c.getNumericCellValue());
            case STRING  -> c.getStringCellValue().trim();
            case FORMULA -> {
                try { yield String.valueOf(c.getNumericCellValue()); }
                catch (Exception e) { yield c.getStringCellValue().trim(); }
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default      -> "";
        };
    }

    private String str(Map<String, String> row, String key) {
        String v = row.get(key);
        return (v == null || v.isBlank() || v.equals("-")) ? null : v.trim();
    }

    private BigDecimal dec(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        try { return new BigDecimal(v.replace(",","").replace("₹","").trim())
                .setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer intVal(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        try {
            // Excel sometimes returns "1.0" for integers
            return (int) Double.parseDouble(v);
        } catch (NumberFormatException e) { return null; }
    }

    public LocalDateTime parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH), // Handles date with time
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("d/M/yyyy"))) {
            try { return LocalDateTime.parse(v.trim(), fmt); }
            catch (Exception ignored) {
                log.warn("Could not parse timestamp: {}", v);
            }
        }
        return null;
    }
}