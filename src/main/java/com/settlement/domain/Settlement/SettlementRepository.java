package com.settlement.domain.Settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findBySellerIdAndSettlementDate(Long sellerId, LocalDate settlementDate);

    boolean existsBySettlementDate(LocalDate settlementDate);

    /**
     * 특정 날짜의 여러 판매자 정산 데이터를 한 번에 조회
     */
    @Query("SELECT s FROM Settlement s WHERE s.settlementDate = :date AND s.sellerId IN :sellerIds")
    List<Settlement> findBySettlementDateAndSellerIdIn(
            @Param("date") LocalDate settlementDate,
            @Param("sellerIds") List<Long> sellerIds);
}
