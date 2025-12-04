package com.settlement.batch.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 배치 실행 Runner
 *
 * 실행 방법:
 * ./gradlew bootRun --args="--job=settlement --targetDate=2024-01-15"
 * ./gradlew bootRun --args="--job=generate"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;
    private final com.settlement._data_generator.TestDataGenerator testDataGenerator;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("job")) {
            log.info("실행할 작업이 지정되지 않았습니다. --job=settlement 또는 --job=generate 를 사용하세요.");
            return;
        }

        String job = args.getOptionValues("job").get(0);

        switch (job) {
            case "generate" -> runDataGeneration(args);
            case "settlement" -> runSettlementJob(args);
            default -> log.warn("알 수 없는 작업: {}", job);
        }
    }

    private void runDataGeneration(ApplicationArguments args) {
        boolean clear = args.containsOption("clear");
        if (clear) {
            testDataGenerator.clearAllData();
        }
        testDataGenerator.generateAll();
    }

    private void runSettlementJob(ApplicationArguments args) throws Exception {
        String targetDate = args.containsOption("targetDate")
                ? args.getOptionValues("targetDate").get(0)
                : LocalDate.now().minusDays(1).toString();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        log.info("정산 배치 실행 - targetDate: {}", targetDate);
        jobLauncher.run(settlementJob, jobParameters);
    }
}
