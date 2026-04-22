package com.cloudkitchen.ingestion.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ZomatoOrder {

    private Long       fileMetadataId;
    private String     fileName;
    private String     fileOrigin;

    // restaurant
    private String     restaurantId;
    private String     restaurantName;
    private String     subzone;
    private String     city;

    // order core
    private String     orderId;
    private Instant    orderPlacedAt;           // primary date — used for report range
    private String     orderStatus;
    private String     deliveryType;
    private BigDecimal distanceKm;

    // items
    private String     itemsInOrder;
    private String     instructions;

    // financials
    private String     discountConstruct;
    private BigDecimal billSubtotal;
    private BigDecimal packagingCharges;
    private BigDecimal restaurantDiscountPromo;
    private BigDecimal restaurantDiscountOthers;
    private BigDecimal goldDiscount;
    private BigDecimal brandPackDiscount;
    private BigDecimal total;

    // cancellation
    private String     cancellationRejectionReason;
    private BigDecimal restaurantCompensationCancellation;
    private BigDecimal restaurantPenaltyRejection;

    // operational
    private Integer    kptDurationMinutes;
    private Integer    riderWaitTimeMinutes;
    private String     orderReadyMarked;

    // customer
    private BigDecimal rating;
    private String     review;
    private String     customerComplaintTag;
    private String     customerId;
    private String     customerPhone;

    // quality
    @Builder.Default private double       confidenceScore = 1.0;
    @Builder.Default private List<String> reviewFlags     = new ArrayList<>();
}