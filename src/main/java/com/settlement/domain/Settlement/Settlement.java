package com.settlement.domain.Settlement;

import com.settlement.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "total_sales", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSales;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal commission;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Settlement(Long sellerId, LocalDate settlementDate, BigDecimal totalSales,
                      BigDecimal commissionRate, Integer orderCount, Integer itemCount) {
        this.sellerId = sellerId;
        this.settlementDate = settlementDate;
        this.totalSales = totalSales;
        this.commissionRate = commissionRate;
        this.commission = totalSales.multiply(commissionRate);
        this.netAmount = totalSales.subtract(this.commission);
        this.orderCount = orderCount;
        this.itemCount = itemCount;
        this.status = SettlementStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 정산 데이터 업데이트 (재정산 시 사용)
     */
    public void update(BigDecimal totalSales, BigDecimal commissionRate,
                       Integer orderCount, Integer itemCount) {
        this.totalSales = totalSales;
        this.commissionRate = commissionRate;
        this.commission = totalSales.multiply(commissionRate);
        this.netAmount = totalSales.subtract(this.commission);
        this.orderCount = orderCount;
        this.itemCount = itemCount;
        this.status = SettlementStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }
}
