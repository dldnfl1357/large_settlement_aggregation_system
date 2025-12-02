# 대용량 정산 집계 시스템 (Large Settlement Aggregation System)

## 프로젝트 개요

**1,000만 건 이상의 주문 데이터를 집계하여 판매자별 정산 금액을 계산하는 배치 시스템**

### 목표
- 1,000만 건 주문 데이터 정산 처리: **10분 이내**
- 메모리 사용량: **4GB 이하** (OOM 없이 처리)
- 데이터 정합성: **100% 정확한 금액 계산**

### 학습 목표

| 기술 | 학습 내용 |
|------|----------|
| **JPA** | 벌크 연산, @BatchSize, Fetch Join vs EntityGraph, OSIV, N+1 해결, 영속성 컨텍스트 관리 |
| **MySQL** | 파티셔닝, 커버링 인덱스, 실행 계획(EXPLAIN), 슬로우 쿼리 분석, 트랜잭션 격리 수준, 락 |
| **JVM** | GC 튜닝 (G1GC, ZGC), 힙 덤프 분석, 메모리 프로파일링, JVM 옵션 최적화 |
| **Java** | Stream API 대용량 처리, 청크 기반 배치 처리, 멀티스레딩 |

---

## 비즈니스 요구사항

### 도메인 설명

온라인 마켓플레이스에서 **판매자(Seller)**가 상품을 등록하고, **구매자(Buyer)**가 주문합니다.
매일 정해진 시간에 전일 주문 데이터를 집계하여 판매자별 정산 금액을 계산합니다.

### 핵심 기능

#### 1. 일일 정산 배치
- 전일 00:00 ~ 23:59:59 주문 데이터 집계
- 판매자별 총 판매금액, 수수료, 정산금액 계산
- 정산 결과를 Settlement 테이블에 저장

#### 2. 정산 금액 계산 규칙
```
판매금액 = SUM(주문상품가격 * 수량)
플랫폼 수수료 = 판매금액 * 수수료율 (판매자 등급별 상이)
정산금액 = 판매금액 - 플랫폼 수수료
```

#### 3. 판매자 등급별 수수료율
| 등급 | 월 매출 기준 | 수수료율 |
|------|-------------|---------|
| BRONZE | 1,000만원 미만 | 15% |
| SILVER | 1,000만원 ~ 5,000만원 | 12% |
| GOLD | 5,000만원 ~ 1억원 | 10% |
| PLATINUM | 1억원 이상 | 8% |

#### 4. 추가 정산 항목
- 환불 주문 제외 (status = REFUNDED)
- 배송비는 정산 금액에서 제외
- 쿠폰 할인 금액은 플랫폼 부담 (판매자 정산에 영향 없음)

---

## 데이터 모델

### ERD 개요

```
┌─────────────┐       ┌─────────────┐       ┌─────────────────┐
│   Seller    │       │   Product   │       │   OrderItem     │
├─────────────┤       ├─────────────┤       ├─────────────────┤
│ id (PK)     │◄──────│ seller_id   │       │ id (PK)         │
│ name        │       │ id (PK)     │◄──────│ product_id (FK) │
│ grade       │       │ name        │       │ order_id (FK)   │
│ email       │       │ price       │       │ quantity        │
│ created_at  │       │ created_at  │       │ unit_price      │
└─────────────┘       └─────────────┘       │ total_price     │
                                            └─────────────────┘
                                                    │
                                                    ▼
┌─────────────────┐                         ┌─────────────────┐
│   Settlement    │                         │     Order       │
├─────────────────┤                         ├─────────────────┤
│ id (PK)         │                         │ id (PK)         │
│ seller_id (FK)  │                         │ buyer_id        │
│ settlement_date │                         │ status          │
│ total_sales     │                         │ ordered_at      │
│ commission      │                         │ shipping_fee    │
│ commission_rate │                         │ coupon_discount │
│ net_amount      │                         │ total_amount    │
│ order_count     │                         │ created_at      │
│ status          │                         └─────────────────┘
│ created_at      │
└─────────────────┘
```
---

## 기술 스택

### Core
- **Java 17** - LTS 버전
- **Spring Boot 3.2.x** - 메인 프레임워크
- **Spring Batch 5.x** - 배치 처리 프레임워크
- **Gradle 8.x** - 빌드 도구

### Database & ORM
- **MySQL 8.0** - 메인 데이터베이스
- **Spring Data JPA 3.2.x** - 데이터 액세스
- **Hibernate 6.x** - JPA 구현체
- **QueryDSL 5.x** - 타입 안전 동적 쿼리
- **Flyway** - 스키마 마이그레이션

### Monitoring & Profiling
- **Spring Actuator** - 애플리케이션 메트릭
- **Micrometer** - 메트릭 수집
- **p6spy** - SQL 로깅 및 분석

### Testing
- **JUnit 5** - 테스트 프레임워크
- **Testcontainers** - 통합 테스트용 MySQL 컨테이너
- **JMH** - 마이크로벤치마크

---

## 시스템 아키텍처

### 배치 처리 플로우

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Settlement Batch Job                            │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 1: 주문 데이터 집계                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐              │
│  │   Reader    │───▶│  Processor  │───▶│   Writer    │              │
│  │ (Chunk 1000)│    │  (집계 계산) │    │ (벌크 저장)  │              │
│  └─────────────┘    └─────────────┘    └─────────────┘              │
│        │                                                             │
│        ▼                                                             │
│  JpaPagingItemReader / JpaCursorItemReader                          │
│  - 청크 단위 조회 (1000건)                                            │
│  - 영속성 컨텍스트 청크마다 클리어                                      │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 2: 정산 결과 검증                                               │
│  - 총 판매금액 vs 개별 합계 일치 확인                                   │
│  - 음수 금액 검증                                                     │
│  - 중복 정산 검증                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 3: 리포트 생성                                                  │
│  - 정산 요약 통계                                                     │
│  - 처리 시간 및 성능 메트릭                                            │
└─────────────────────────────────────────────────────────────────────┘
```

### 청크 기반 처리 상세

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Chunk-Oriented Processing                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Transaction 1                                                       │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ Read(1) → Read(2) → ... → Read(1000)                       │     │
│  │     ↓                                                       │     │
│  │ Process(1) → Process(2) → ... → Process(1000)              │     │
│  │     ↓                                                       │     │
│  │ Write([1...1000])  ← Bulk Insert/Update                    │     │
│  │     ↓                                                       │     │
│  │ EntityManager.clear()  ← 메모리 해제                        │     │
│  └────────────────────────────────────────────────────────────┘     │
│                              ↓                                       │
│  Transaction 2                                                       │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │ Read(1001) → Read(1002) → ... → Read(2000)                 │     │
│  │     ...                                                     │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 단계별 구현 계획

### Phase 1: 기본 구조 및 도메인 설계
- [ ] 프로젝트 초기 설정 (Spring Boot, Gradle)
- [ ] 도메인 엔티티 설계 및 JPA 매핑
- [ ] Flyway 마이그레이션 스크립트 작성
- [ ] 테스트 데이터 생성기 구현 (1,000만 건)

### Phase 2: 기본 배치 구현
- [ ] Spring Batch Job 구성
- [ ] JpaPagingItemReader로 청크 단위 조회
- [ ] 정산 로직 Processor 구현
- [ ] JpaItemWriter로 결과 저장

### Phase 3: JPA 최적화
- [ ] N+1 문제 분석 및 해결 (Fetch Join, EntityGraph, @BatchSize)
- [ ] 벌크 연산 적용 (JPQL UPDATE, Native Query)
- [ ] 영속성 컨텍스트 관리 최적화
- [ ] OSIV 설정 및 영향 분석

### Phase 4: MySQL 최적화
- [ ] 실행 계획 분석 (EXPLAIN ANALYZE)
- [ ] 인덱스 최적화 (커버링 인덱스, 복합 인덱스)
- [ ] 파티셔닝 적용 (ordered_at 기준 RANGE 파티션)
- [ ] 슬로우 쿼리 분석 및 개선

### Phase 5: JVM 최적화
- [ ] GC 로그 분석 및 튜닝
- [ ] 힙 덤프 분석 (MAT, VisualVM)
- [ ] JVM 옵션 최적화 (-Xmx, -XX:+UseG1GC 등)
- [ ] 메모리 누수 탐지 및 해결

### Phase 6: 고급 최적화
- [ ] 병렬 처리 (Partitioning, Multi-threaded Step)
- [ ] JpaCursorItemReader vs JpaPagingItemReader 비교
- [ ] 배치 크기 최적화 (chunk size, fetch size)
- [ ] 커넥션 풀 튜닝 (HikariCP)

---

## 성능 측정 기준

### 기본 측정 항목
| 항목 | 목표 | 측정 방법 |
|------|------|----------|
| 총 처리 시간 | < 10분 | Spring Batch Job 실행 시간 |
| 메모리 사용량 | < 4GB | JVM 힙 사용량 모니터링 |
| GC 일시정지 | < 200ms | GC 로그 분석 |
| 쿼리 실행 시간 | < 100ms (단위 쿼리) | p6spy 로깅 |

### 단계별 벤치마크
```
Phase 2 (기본 구현):     예상 30분+ (최적화 전 베이스라인)
Phase 3 (JPA 최적화):    예상 20분
Phase 4 (MySQL 최적화):  예상 15분
Phase 5 (JVM 최적화):    예상 12분
Phase 6 (고급 최적화):   목표 10분 이내
```

---

## 프로젝트 구조

```
src/main/java/settlement/
├── SettlementApplication.java
├── batch/
│   ├── job/
│   │   └── SettlementJobConfig.java
│   ├── reader/
│   │   └── OrderItemReader.java
│   ├── processor/
│   │   └── SettlementProcessor.java
│   ├── writer/
│   │   └── SettlementWriter.java
│   └── listener/
│       └── JobExecutionListener.java
├── domain/
│   ├── entity/
│   │   ├── Seller.java
│   │   ├── Product.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── Settlement.java
│   ├── enums/
│   │   ├── SellerGrade.java
│   │   ├── OrderStatus.java
│   │   └── SettlementStatus.java
│   └── repository/
│       ├── SellerRepository.java
│       ├── OrderRepository.java
│       ├── OrderItemRepository.java
│       └── SettlementRepository.java
├── service/
│   ├── SettlementService.java
│   └── CommissionCalculator.java
├── config/
│   ├── BatchConfig.java
│   ├── JpaConfig.java
│   └── DataSourceConfig.java
└── util/
    └── TestDataGenerator.java

src/main/resources/
├── application.yml
├── application-dev.yml
├── application-prod.yml
└── db/migration/
    ├── V1__init_schema.sql
    └── V2__add_indexes.sql
```

---

## 개발 환경 설정

### Docker Compose (MySQL)
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: settlement-mysql
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: settlement
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --innodb-buffer-pool-size=2G
      - --innodb-log-file-size=512M
      - --slow-query-log=1
      - --slow-query-log-file=/var/lib/mysql/slow.log
      - --long-query-time=1
```

### JVM 옵션 (개발용)
```
-Xms2G -Xmx4G
-XX:+UseG1GC
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:logs/gc.log
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=logs/heapdump.hprof
```

---

## 참고 자료

### JPA & Hibernate
- [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/6.2/userguide/html_single/Hibernate_User_Guide.html)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)

### Spring Batch
- [Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)
- [Spring Batch 성능 최적화](https://docs.spring.io/spring-batch/reference/scalability.html)

### MySQL
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [High Performance MySQL](https://www.oreilly.com/library/view/high-performance-mysql/9781492080503/)

### JVM
- [JVM Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/)
- [G1GC Tuning](https://www.oracle.com/technical-resources/articles/java/g1gc.html)
