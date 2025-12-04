package com.settlement.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 정산 배치 Job 실행 리스너
 * 시작/종료 시간 및 성능 메트릭을 로깅한다
 */
@Slf4j
@Component
public class SettlementJobListener implements JobExecutionListener {

    private long startMemory;
    private long peakMemory;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // GC 실행 후 시작 메모리 측정
        System.gc();
        startMemory = getUsedMemoryMB();
        peakMemory = startMemory;

        log.info("========================================");
        log.info("정산 배치 시작");
        log.info("Job Name: {}", jobExecution.getJobInstance().getJobName());
        log.info("Job Parameters: {}", jobExecution.getJobParameters());
        log.info("시작 시간: {}", LocalDateTime.now());
        log.info("시작 메모리: {} MB", startMemory);
        log.info("최대 힙 메모리: {} MB", getMaxMemoryMB());
        log.info("========================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        Duration duration = Duration.between(startTime, endTime);

        long endMemory = getUsedMemoryMB();

        log.info("========================================");
        log.info("정산 배치 종료");
        log.info("상태: {}", jobExecution.getStatus());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("소요 시간: {}분 {}초 (총 {}ms)",
                    duration.toMinutes(),
                    duration.toSecondsPart(),
                    duration.toMillis());
        } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("배치 실패!");
            jobExecution.getAllFailureExceptions().forEach(e ->
                    log.error("실패 원인: {}", e.getMessage(), e));
        }

        // 메모리 사용량 통계
        log.info("----------------------------------------");
        log.info("[메모리 사용량]");
        log.info("  - 시작 메모리: {} MB", startMemory);
        log.info("  - 종료 메모리: {} MB", endMemory);
        log.info("  - 메모리 증가량: {} MB", endMemory - startMemory);
        log.info("  - 최대 힙 메모리: {} MB", getMaxMemoryMB());

        // Step 별 통계
        jobExecution.getStepExecutions().forEach(stepExecution -> {
            log.info("----------------------------------------");
            log.info("[Step: {}]", stepExecution.getStepName());
            log.info("  - Read Count: {}", stepExecution.getReadCount());
            log.info("  - Write Count: {}", stepExecution.getWriteCount());
            log.info("  - Commit Count: {}", stepExecution.getCommitCount());
            log.info("  - Skip Count: {}", stepExecution.getSkipCount());
        });

        log.info("========================================");
    }

    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}
