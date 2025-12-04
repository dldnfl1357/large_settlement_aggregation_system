package com.settlement.batch.dto;

import com.settlement.enums.SellerGrade;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 판매자별 주문 집계 결과 DTO
 * DB에서 GROUP BY로 집계된 결과를 담는다
 */
@Getter
public class SellerAggregation {

    private final Long sellerId;
    private final SellerGrade sellerGrade;
    private final BigDecimal totalSales;
    private final Long orderCount;
    private final Long itemCount;

    public SellerAggregation(Long sellerId, String sellerGrade, BigDecimal totalSales,
                             Long orderCount, Long itemCount) {
        this.sellerId = sellerId;
        this.sellerGrade = SellerGrade.valueOf(sellerGrade);
        this.totalSales = totalSales;
        this.orderCount = orderCount;
        this.itemCount = itemCount;
    }
}
