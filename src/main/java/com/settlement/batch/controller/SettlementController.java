package com.settlement.batch.controller;

import com.settlement.batch.service.SettlementJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 정산 배치 REST API 컨트롤러
 *
 * API:
 * - POST /api/settlements/run?targetDate=2024-01-15 : 특정 날짜 정산 실행
 * - POST /api/settlements/run                        : 전날 정산 실행
 */
@Slf4j
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementJobService settlementJobService;

    /**
     * 정산 배치 수동 실행
     *
     * @param targetDate 정산 대상 날짜 (기본값: 전날)
     * @return 배치 실행 결과
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runSettlement(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        LocalDate date = targetDate != null ? targetDate : LocalDate.now().minusDays(1);
        log.info("정산 배치 API 호출 - targetDate: {}", date);

        try {
            JobExecution execution = settlementJobService.runSettlementJob(date);
            return ResponseEntity.ok(buildSuccessResponse(execution, date));
        } catch (IllegalStateException e) {
            log.warn("정산 배치 실행 불가 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage(), date));
        } catch (Exception e) {
            log.error("정산 배치 실행 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse("정산 배치 실행 중 오류가 발생했습니다", date));
        }
    }

    /**
     * 기간 정산 배치 실행 (시작일 ~ 종료일)
     *
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 배치 실행 결과
     */
    @PostMapping("/run/range")
    public ResponseEntity<Map<String, Object>> runSettlementRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("기간 정산 배치 API 호출 - {} ~ {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("시작일이 종료일보다 클 수 없습니다", null));
        }

        Map<String, Object> results = new HashMap<>();
        int successCount = 0;
        int failCount = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            try {
                JobExecution execution = settlementJobService.runSettlementJob(date);
                if (execution.getStatus() == BatchStatus.COMPLETED) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.warn("날짜 {} 정산 실패: {}", date, e.getMessage());
                failCount++;
            }
        }

        results.put("startDate", startDate.toString());
        results.put("endDate", endDate.toString());
        results.put("totalDays", startDate.until(endDate).getDays() + 1);
        results.put("successCount", successCount);
        results.put("failCount", failCount);

        return ResponseEntity.ok(results);
    }

    private Map<String, Object> buildSuccessResponse(JobExecution execution, LocalDate targetDate) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("jobExecutionId", execution.getId());
        response.put("batchStatus", execution.getStatus().toString());
        response.put("targetDate", targetDate.toString());
        response.put("startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : null);
        response.put("endTime", execution.getEndTime() != null ? execution.getEndTime().toString() : null);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message, LocalDate targetDate) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        if (targetDate != null) {
            response.put("targetDate", targetDate.toString());
        }
        return response;
    }
}
