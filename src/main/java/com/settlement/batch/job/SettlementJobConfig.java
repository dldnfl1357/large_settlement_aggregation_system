package com.settlement.batch.job;

import com.settlement.batch.dto.SellerAggregation;
import com.settlement.batch.listener.SettlementJobListener;
import com.settlement.batch.processor.SettlementProcessor;
import com.settlement.batch.tasklet.SettlementVerificationTasklet;
import com.settlement.batch.writer.SettlementWriter;
import com.settlement.domain.Settlement.Settlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 정산 배치 Job 설정
 *
 * 처리 흐름:
 * Step 1. 정산 처리: Reader → Processor → Writer
 * Step 2. 검증: OrderItem 합계와 Settlement 합계 비교
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final SettlementJobListener jobListener;
    private final SettlementProcessor processor;
    private final SettlementWriter writer;
    private final SettlementVerificationTasklet verificationTasklet;

    private static final int CHUNK_SIZE = 100;
    private static final int PAGE_SIZE = 100;

    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .listener(jobListener)
                .start(settlementStep())
                .next(verificationStep())
                .build();
    }

    @Bean
    public Step verificationStep() {
        return new StepBuilder("verificationStep", jobRepository)
                .tasklet(verificationTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
                .<SellerAggregation, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(sellerAggregationReader(null))
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<SellerAggregation> sellerAggregationReader(
            @Value("#{jobParameters['targetDate']}") String targetDate) {

        LocalDate date = targetDate != null
                ? LocalDate.parse(targetDate)
                : LocalDate.now().minusDays(1);

        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("startDate", date.atStartOfDay());
        parameterValues.put("endDate", date.plusDays(1).atStartOfDay());

        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
                s.id as seller_id,
                s.grade as seller_grade,
                SUM(oi.total_price) as total_sales,
                COUNT(DISTINCT oi.order_id) as order_count,
                COUNT(oi.id) as item_count
                """);
        queryProvider.setFromClause("""
                order_items oi
                JOIN orders o ON oi.order_id = o.id
                JOIN sellers s ON oi.seller_id = s.id
                """);
        queryProvider.setWhereClause("""
                o.ordered_at >= :startDate
                AND o.ordered_at < :endDate
                AND o.status IN ('DELIVERED', 'SHIPPED', 'PAID')
                """);
        queryProvider.setGroupClause("s.id, s.grade");
        queryProvider.setSortKeys(Map.of("seller_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<SellerAggregation>()
                .name("sellerAggregationReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(parameterValues)
                .pageSize(PAGE_SIZE)
                .rowMapper((rs, rowNum) -> new SellerAggregation(
                        rs.getLong("seller_id"),
                        rs.getString("seller_grade"),
                        rs.getBigDecimal("total_sales"),
                        rs.getLong("order_count"),
                        rs.getLong("item_count")
                ))
                .build();
    }
}
