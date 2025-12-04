package com.settlement.batch.processor;

import com.settlement.batch.dto.SellerAggregation;
import com.settlement.domain.Settlement.Settlement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 판매자별 집계 데이터를 Settlement 엔티티로 변환
 * 수수료 계산은 Settlement 생성자에서 수행
 */
@Slf4j
@Component
@StepScope
public class SettlementProcessor implements ItemProcessor<SellerAggregation, Settlement> {

    private final LocalDate settlementDate;

    public SettlementProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        this.settlementDate = targetDate != null
                ? LocalDate.parse(targetDate)
                : LocalDate.now().minusDays(1);
    }

    @Override
    public Settlement process(SellerAggregation aggregation) throws Exception {
        return Settlement.builder()
                .sellerId(aggregation.getSellerId())
                .settlementDate(settlementDate)
                .totalSales(aggregation.getTotalSales())
                .commissionRate(aggregation.getSellerGrade().getCommissionRate())
                .orderCount(aggregation.getOrderCount().intValue())
                .itemCount(aggregation.getItemCount().intValue())
                .build();
    }
}
