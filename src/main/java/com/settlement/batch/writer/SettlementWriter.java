package com.settlement.batch.writer;

import com.settlement.domain.Settlement.Settlement;
import com.settlement.domain.Settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Settlement 엔티티를 DB에 저장 (JPA 방식 UPSERT)
 *
 * 동일한 seller_id + settlement_date 조합이 이미 존재하면 UPDATE,
 * 없으면 INSERT 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementWriter implements ItemWriter<Settlement> {

    private final SettlementRepository settlementRepository;

    @Override
    public void write(Chunk<? extends Settlement> chunk) throws Exception {
        List<? extends Settlement> newSettlements = chunk.getItems();
        if (newSettlements.isEmpty()) {
            return;
        }

        log.debug("정산 데이터 저장 시작: {} 건", newSettlements.size());

        // 1. chunk 내 모든 sellerId 추출
        List<Long> sellerIds = newSettlements.stream()
                .map(Settlement::getSellerId)
                .collect(Collectors.toList());

        // 2. 해당 날짜의 기존 정산 데이터 한 번에 조회
        LocalDate settlementDate = newSettlements.get(0).getSettlementDate();
        Map<Long, Settlement> existingMap = settlementRepository
                .findBySettlementDateAndSellerIdIn(settlementDate, sellerIds)
                .stream()
                .collect(Collectors.toMap(Settlement::getSellerId, Function.identity()));

        // 3. 신규 INSERT vs 기존 UPDATE 분류
        List<Settlement> toSave = new ArrayList<>();
        int insertCount = 0;
        int updateCount = 0;

        for (Settlement newSettlement : newSettlements) {
            Settlement existing = existingMap.get(newSettlement.getSellerId());

            if (existing != null) {
                // 기존 데이터 업데이트 (Dirty Checking)
                existing.update(
                        newSettlement.getTotalSales(),
                        newSettlement.getCommissionRate(),
                        newSettlement.getOrderCount(),
                        newSettlement.getItemCount()
                );
                toSave.add(existing);
                updateCount++;
            } else {
                // 신규 데이터 삽입
                toSave.add(newSettlement);
                insertCount++;
            }
        }

        // 4. 저장 (JPA의 Dirty Checking으로 UPDATE, 새 엔티티는 INSERT)
        settlementRepository.saveAll(toSave);

        log.info("정산 데이터 저장 완료 - INSERT: {} 건, UPDATE: {} 건", insertCount, updateCount);
    }
}
