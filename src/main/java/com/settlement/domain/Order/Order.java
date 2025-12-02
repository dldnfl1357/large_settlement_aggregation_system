package com.settlement.domain.Order;

import com.settlement.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "shipping_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "coupon_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Order(Long buyerId, OrderStatus status, BigDecimal shippingFee,
                 BigDecimal couponDiscount, BigDecimal totalAmount, LocalDateTime orderedAt) {
        this.buyerId = buyerId;
        this.status = status;
        this.shippingFee = shippingFee != null ? shippingFee : BigDecimal.ZERO;
        this.couponDiscount = couponDiscount != null ? couponDiscount : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.orderedAt = orderedAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
