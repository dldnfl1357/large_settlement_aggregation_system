package com.settlement.batch.scheduler;

import com.settlement.batch.service.SettlementJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 배치 스케줄러
 * 매일 새벽 3시에 전날 정산 배치를 자동 실행
 *
 * 활성화: application.yml에서 settlement.scheduler.enabled=true 설정
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "settlement.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SettlementScheduler {

    private final SettlementJobService settlementJobService;

    /**
     * 매일 새벽 3시에 전날 정산 실행
     * Cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "${settlement.scheduler.cron:0 0 3 * * *}")
    public void runDailySettlement() {
        log.info("=== 일일 정산 스케줄러 시작 ===");

        try {
            JobExecution execution = settlementJobService.runYesterdaySettlement();
            log.info("=== 일일 정산 스케줄러 완료 - status: {} ===", execution.getStatus());
        } catch (Exception e) {
            log.error("=== 일일 정산 스케줄러 실패 ===", e);
        }
    }
}
