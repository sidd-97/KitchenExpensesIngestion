package com.cloudkitchen.ingestion.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SwiggyOrder {

    private Long       fileMetadataId;
    private String     fileName;
    private String     fileOrigin;

    // order core
    private String     orderId;
    private String     orderStatus;
    private Instant    orderRelayTime;          // primary date — used for report range
    private Instant    orderAcceptanceTime;
    private Instant    orderDeliveryTime;
    private Instant    orderCancellationTime;

    // financials
    private BigDecimal totalBillAmount;
    private BigDecimal taxRestaurant;
    private BigDecimal packingCharge;
    private BigDecimal restaurantTradeDiscount;
    private BigDecimal restaurantCouponDiscountShare;
    private BigDecimal restaurantBear;

    // item GST
    private BigDecimal itemSgst;
    private BigDecimal itemCgst;
    private BigDecimal itemIgst;
    private BigDecimal itemGstInclusive;

    // packaging GST
    private BigDecimal packagingChargeSgst;
    private BigDecimal packagingChargeCgst;
    private BigDecimal packagingChargeIgst;
    private BigDecimal packagingGstInclusive;

    // service charge GST
    private BigDecimal serviceChargeSgst;
    private BigDecimal serviceChargeCgst;
    private BigDecimal serviceChargeIgst;
    private BigDecimal serviceChargeGstInclusive;

    // order metadata
    private Boolean    foodPrepared;
    private String     editedStatus;
    private Integer    itemCount;
    private String     mouType;
    private String     cancelledReason;
    private String     cancellationResponsibleEntity;
    private String     itemsDetail;             // raw composite column stored as text

    // quality
    @Builder.Default private double       confidenceScore = 1.0;
    @Builder.Default private List<String> reviewFlags     = new ArrayList<>();
}