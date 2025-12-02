package com.settlement.enums;

import java.math.BigDecimal;

public enum SellerGrade {
    BRONZE(new BigDecimal("0.15")),   // 15% 수수료
    SILVER(new BigDecimal("0.12")),   // 12% 수수료
    GOLD(new BigDecimal("0.10")),     // 10% 수수료
    PLATINUM(new BigDecimal("0.08")); // 8% 수수료

    private final BigDecimal commissionRate;

    SellerGrade(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }
}
