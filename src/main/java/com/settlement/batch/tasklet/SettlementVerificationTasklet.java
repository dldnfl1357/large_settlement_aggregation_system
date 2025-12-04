package com.settlement.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정산 금액 검증 Tasklet
 *
 * OrderItem의 판매자별 합계와 Settlement의 합계를 비교하여
 * 데이터 정합성을 검증한다
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementVerificationTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['targetDate']}")
    private String targetDateStr;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate targetDate = targetDateStr != null
                ? LocalDate.parse(targetDateStr)
                : LocalDate.now().minusDays(1);

        log.info("========================================");
        log.info("정산 금액 검증 시작 - targetDate: {}", targetDate);

        // 1. OrderItem에서 직접 계산한 판매자별 총 판매금액
        BigDecimal orderItemTotal = getOrderItemTotal(targetDate);

        // 2. Settlement 테이블의 총 판매금액
        BigDecimal settlementTotal = getSettlementTotal(targetDate);

        // 3. 검증
        log.info("----------------------------------------");
        log.info("[금액 검증 결과]");
        log.info("  - OrderItem 합계: {}", orderItemTotal);
        log.info("  - Settlement 합계: {}", settlementTotal);

        if (orderItemTotal.compareTo(settlementTotal) == 0) {
            log.info("  - 결과: ✓ 일치 (정확도 100%)");
        } else {
            BigDecimal diff = orderItemTotal.subtract(settlementTotal).abs();
            log.error("  - 결과: ✗ 불일치!");
            log.error("  - 차이: {}", diff);

            // 상세 불일치 내역 조회
            logMismatchDetails(targetDate);

            throw new IllegalStateException("정산 금액 불일치 발생: 차이 = " + diff);
        }

        // 4. 추가 통계
        logSettlementStatistics(targetDate);

        log.info("========================================");
        return RepeatStatus.FINISHED;
    }

    /**
     * OrderItem에서 직접 계산한 총 판매금액
     * (정산 대상 주문만)
     */
    private BigDecimal getOrderItemTotal(LocalDate targetDate) {
        String sql = """
                SELECT COALESCE(SUM(oi.total_price), 0)
                FROM order_items oi
                JOIN orders o ON oi.order_id = o.id
                WHERE o.ordered_at >= ?
                  AND o.ordered_at < ?
                  AND o.status IN ('DELIVERED', 'SHIPPED', 'PAID')
                """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay());
    }

    /**
     * Settlement 테이블의 총 판매금액
     */
    private BigDecimal getSettlementTotal(LocalDate targetDate) {
        String sql = """
                SELECT COALESCE(SUM(total_sales), 0)
                FROM settlements
                WHERE settlement_date = ?
                """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, targetDate);
    }

    /**
     * 판매자별 불일치 내역 상세 로그
     */
    private void logMismatchDetails(LocalDate targetDate) {
        String sql = """
                SELECT
                    oi_agg.seller_id,
                    oi_agg.order_item_total,
                    COALESCE(s.total_sales, 0) as settlement_total,
                    oi_agg.order_item_total - COALESCE(s.total_sales, 0) as diff
                FROM (
                    SELECT oi.seller_id, SUM(oi.total_price) as order_item_total
                    FROM order_items oi
                    JOIN orders o ON oi.order_id = o.id
                    WHERE o.ordered_at >= ?
                      AND o.ordered_at < ?
                      AND o.status IN ('DELIVERED', 'SHIPPED', 'PAID')
                    GROUP BY oi.seller_id
                ) oi_agg
                LEFT JOIN settlements s ON oi_agg.seller_id = s.seller_id AND s.settlement_date = ?
                WHERE oi_agg.order_item_total != COALESCE(s.total_sales, 0)
                LIMIT 10
                """;

        var mismatches = jdbcTemplate.queryForList(sql,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay(),
                targetDate);

        if (!mismatches.isEmpty()) {
            log.error("  - 불일치 판매자 (상위 10건):");
            mismatches.forEach(row ->
                    log.error("    seller_id={}, OrderItem={}, Settlement={}, 차이={}",
                            row.get("seller_id"),
                            row.get("order_item_total"),
                            row.get("settlement_total"),
                            row.get("diff")));
        }
    }

    /**
     * 정산 통계 로그
     */
    private void logSettlementStatistics(LocalDate targetDate) {
        String sql = """
                SELECT
                    COUNT(*) as seller_count,
                    SUM(total_sales) as total_sales,
                    SUM(commission) as total_commission,
                    SUM(net_amount) as total_net_amount,
                    SUM(order_count) as total_orders,
                    SUM(item_count) as total_items
                FROM settlements
                WHERE settlement_date = ?
                """;

        var stats = jdbcTemplate.queryForMap(sql, targetDate);

        log.info("----------------------------------------");
        log.info("[정산 통계]");
        log.info("  - 정산 판매자 수: {}", stats.get("seller_count"));
        log.info("  - 총 판매금액: {}", stats.get("total_sales"));
        log.info("  - 총 수수료: {}", stats.get("total_commission"));
        log.info("  - 총 정산금액: {}", stats.get("total_net_amount"));
        log.info("  - 총 주문 수: {}", stats.get("total_orders"));
        log.info("  - 총 주문상품 수: {}", stats.get("total_items"));
    }
}
