package com.settlement.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 정산 배치 Job 실행 서비스
 * 스케줄러와 API에서 공통으로 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementJobService {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    /**
     * 정산 배치 실행
     *
     * @param targetDate 정산 대상 날짜
     * @return JobExecution 결과
     */
    public JobExecution runSettlementJob(LocalDate targetDate) {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        log.info("정산 배치 실행 시작 - targetDate: {}", targetDate);

        try {
            JobExecution execution = jobLauncher.run(settlementJob, jobParameters);
            log.info("정산 배치 완료 - status: {}, targetDate: {}",
                    execution.getStatus(), targetDate);
            return execution;
        } catch (JobExecutionAlreadyRunningException e) {
            log.error("정산 배치가 이미 실행 중입니다 - targetDate: {}", targetDate);
            throw new IllegalStateException("정산 배치가 이미 실행 중입니다", e);
        } catch (JobRestartException e) {
            log.error("정산 배치 재시작 실패 - targetDate: {}", targetDate, e);
            throw new IllegalStateException("정산 배치 재시작 실패", e);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("해당 날짜의 정산이 이미 완료되었습니다 - targetDate: {}", targetDate);
            throw new IllegalStateException("해당 날짜의 정산이 이미 완료되었습니다", e);
        } catch (JobParametersInvalidException e) {
            log.error("잘못된 Job 파라미터 - targetDate: {}", targetDate, e);
            throw new IllegalArgumentException("잘못된 Job 파라미터", e);
        }
    }

    /**
     * 전날 정산 배치 실행 (스케줄러용)
     */
    public JobExecution runYesterdaySettlement() {
        return runSettlementJob(LocalDate.now().minusDays(1));
    }
}
