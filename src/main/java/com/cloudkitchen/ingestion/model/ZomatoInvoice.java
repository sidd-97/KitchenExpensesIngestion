package com.cloudkitchen.ingestion.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps 1:1 to the zomato_invoices table.
 * Wide model — all 61 source columns preserved as typed fields.
 * Intentionally not normalised: every financial column is analytically important.
 */
@Data
@Builder
public class ZomatoInvoice {

    private Long       fileMetadataId;
    private String     fileName;
    private String     fileOrigin;

    // identity
    private Integer    sno;
    private String     orderId;
    private Instant orderDate;               // primary date — used for report range
    private Integer    weekNo;
    private String     restaurantName;
    private String     restaurantId;

    // order info
    private String     discountConstruct;
    private String     modeOfPayment;
    private String     orderStatus;
    private BigDecimal cancellationPolicyPercent;
    private String     cancellationRejectionReason;
    private String     cancelledRejectedState;
    private String     orderType;
    private String     deliveryStateCode;

    // revenue
    private BigDecimal subtotal;
    private BigDecimal packagingCharge;
    private BigDecimal deliveryChargeSelfLogistics;
    private BigDecimal restaurantDiscountPromo;
    private BigDecimal restaurantDiscountOthers;
    private BigDecimal brandPackSubscriptionFee;
    private BigDecimal deliveryChargeDiscountRelisting;
    private BigDecimal totalGstFromCustomers;
    private BigDecimal netOrderValue;

    // commissionable values
    private BigDecimal commissionableSubtotal;
    private BigDecimal commissionablePackagingCharge;
    private BigDecimal commissionableTotalGst;
    private BigDecimal totalCommissionableValue;

    // service fee
    private BigDecimal baseServiceFeePercent;
    private BigDecimal baseServiceFee;
    private BigDecimal actualOrderDistanceKm;
    private BigDecimal longDistanceEnablementFee;
    private BigDecimal discountLongDistanceFee;
    private BigDecimal discountServiceFee30Cap;
    private BigDecimal paymentMechanismFee;
    private BigDecimal serviceFeeAndPaymentMechFee;
    private BigDecimal taxesOnServiceFee;

    // tax
    private BigDecimal applicableAmountTcs;
    private BigDecimal applicableAmount9_5;
    private BigDecimal taxCollectedAtSource;
    private BigDecimal tcsIgstAmount;
    private BigDecimal tds194oAmount;
    private BigDecimal gstPaidByZomato9_5;
    private BigDecimal gstToBePaidByRestaurant;
    private BigDecimal governmentCharges;

    // adjustments
    private BigDecimal customerCompensationRecoupment;
    private BigDecimal deliveryChargesRecovery;
    private BigDecimal amountReceivedCash;
    private BigDecimal creditDebitNoteAdjustment;
    private BigDecimal promoRecoveryAdjustment;
    private BigDecimal extraInventoryAdsDeduction;
    private BigDecimal brandLoyaltyPointsRedemption;
    private BigDecimal expressOrderFee;
    private BigDecimal otherOrderLevelDeductions;

    // settlement
    private BigDecimal netDeductions;
    private BigDecimal netAdditions;
    private BigDecimal orderLevelPayout;
    private String     settlementStatus;
    private Instant  settlementDate;
    private String     bankUtr;
    private BigDecimal unsettledAmount;
    private String     customerId;

    // quality
    @Builder.Default private double       confidenceScore = 1.0;
    @Builder.Default private List<String> reviewFlags     = new ArrayList<>();
}