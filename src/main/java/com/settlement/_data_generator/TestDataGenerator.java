package com.settlement._data_generator;

import com.settlement.enums.OrderStatus;
import com.settlement.enums.ProductStatus;
import com.settlement.enums.SellerGrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    private static final int SELLER_COUNT = 1_000;
    private static final int PRODUCTS_PER_SELLER = 50;
    private static final int TOTAL_PRODUCTS = SELLER_COUNT * PRODUCTS_PER_SELLER; // 50,000
    private static final int TOTAL_ORDER_ITEMS = 10_000_000;
    private static final int ITEMS_PER_ORDER = 4; // 평균
    private static final int TOTAL_ORDERS = TOTAL_ORDER_ITEMS / ITEMS_PER_ORDER; // 2,500,000

    private static final int BATCH_SIZE = 5_000;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Random random = new Random(42); // 재현 가능한 시드

    public void generateAll() {
        log.info("========== 테스트 데이터 생성 시작 ==========");
        long startTime = System.currentTimeMillis();

        generateSellers();
        generateProducts();
        generateOrdersAndItems();

        long endTime = System.currentTimeMillis();
        log.info("========== 테스트 데이터 생성 완료 (소요시간: {}초) ==========",
                (endTime - startTime) / 1000);
    }

    public void generateSellers() {
        log.info("판매자 데이터 생성 시작: {} 건", SELLER_COUNT);
        long startTime = System.currentTimeMillis();

        String sql = "INSERT INTO sellers (name, email, grade, business_number, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        SellerGrade[] grades = SellerGrade.values();
        // 등급 분포: BRONZE 40%, SILVER 30%, GOLD 20%, PLATINUM 10%
        int[] gradeDistribution = {40, 30, 20, 10};

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            LocalDateTime now = LocalDateTime.now();
            String nowStr = now.format(DATETIME_FORMATTER);

            for (int i = 1; i <= SELLER_COUNT; i++) {
                ps.setString(1, "판매자_" + i);
                ps.setString(2, "seller" + i + "@example.com");
                ps.setString(3, getRandomGrade(grades, gradeDistribution, i).name());
                ps.setString(4, String.format("%03d-%02d-%05d",
                        100 + (i / 10000), (i / 100) % 100, i % 100000));
                ps.setString(5, nowStr);
                ps.setString(6, nowStr);
                ps.addBatch();

                if (i % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                    log.info("판매자 {} 건 저장 완료", i);
                }
            }
            ps.executeBatch();
            conn.commit();

        } catch (Exception e) {
            throw new RuntimeException("판매자 데이터 생성 실패", e);
        }

        log.info("판매자 데이터 생성 완료 (소요시간: {}ms)", System.currentTimeMillis() - startTime);
    }

    public void generateProducts() {
        log.info("상품 데이터 생성 시작: {} 건", TOTAL_PRODUCTS);
        long startTime = System.currentTimeMillis();

        String sql = "INSERT INTO products (seller_id, name, price, stock, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            LocalDateTime now = LocalDateTime.now();
            String nowStr = now.format(DATETIME_FORMATTER);

            int productId = 0;
            for (int sellerId = 1; sellerId <= SELLER_COUNT; sellerId++) {
                for (int p = 1; p <= PRODUCTS_PER_SELLER; p++) {
                    productId++;
                    BigDecimal price = BigDecimal.valueOf(1000 + random.nextInt(99000))
                            .setScale(2, RoundingMode.HALF_UP);

                    ps.setLong(1, sellerId);
                    ps.setString(2, "상품_" + sellerId + "_" + p);
                    ps.setBigDecimal(3, price);
                    ps.setInt(4, 100 + random.nextInt(900));
                    ps.setString(5, ProductStatus.ACTIVE.name());
                    ps.setString(6, nowStr);
                    ps.setString(7, nowStr);
                    ps.addBatch();

                    if (productId % BATCH_SIZE == 0) {
                        ps.executeBatch();
                        conn.commit();
                        log.info("상품 {} 건 저장 완료", productId);
                    }
                }
            }
            ps.executeBatch();
            conn.commit();

        } catch (Exception e) {
            throw new RuntimeException("상품 데이터 생성 실패", e);
        }

        log.info("상품 데이터 생성 완료 (소요시간: {}ms)", System.currentTimeMillis() - startTime);
    }

    public void generateOrdersAndItems() {
        log.info("주문/주문상품 데이터 생성 시작: 주문 {} 건, 주문상품 {} 건",
                TOTAL_ORDERS, TOTAL_ORDER_ITEMS);
        long startTime = System.currentTimeMillis();

        // 상품 정보 로드 (product_id -> {seller_id, price})
        List<ProductInfo> products = loadProductInfo();
        log.info("상품 정보 로드 완료: {} 건", products.size());

        String orderSql = "INSERT INTO orders (buyer_id, status, shipping_fee, coupon_discount, " +
                "total_amount, ordered_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, seller_id, quantity, " +
                "unit_price, total_price, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        // 주문 상태 분포: DELIVERED 60%, SHIPPED 15%, PAID 10%, REFUNDED 10%, CANCELLED 5%
        OrderStatus[] statuses = {
                OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.DELIVERED,
                OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.DELIVERED,
                OrderStatus.SHIPPED, OrderStatus.SHIPPED,
                OrderStatus.PAID,
                OrderStatus.REFUNDED,
                OrderStatus.CANCELLED
        };

        // 정산 대상 날짜: 어제
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement itemPs = conn.prepareStatement(itemSql)) {

            conn.setAutoCommit(false);
            LocalDateTime now = LocalDateTime.now();
            String nowStr = now.format(DATETIME_FORMATTER);

            int totalItemCount = 0;
            List<Long> orderIds = new ArrayList<>(BATCH_SIZE);

            for (int orderId = 1; orderId <= TOTAL_ORDERS; orderId++) {
                // 주문 시간: 어제 00:00 ~ 23:59:59 랜덤
                LocalDateTime orderedAt = yesterday.plusSeconds(random.nextInt(86400));
                String orderedAtStr = orderedAt.format(DATETIME_FORMATTER);

                // 주문당 아이템 수: 정확히 4개
                int itemCount = 4;

                // 주문 아이템 생성 준비
                BigDecimal totalAmount = BigDecimal.ZERO;
                List<OrderItemData> items = new ArrayList<>(itemCount);

                for (int i = 0; i < itemCount; i++) {
                    ProductInfo product = products.get(random.nextInt(products.size()));
                    int quantity = 1 + random.nextInt(3);
                    BigDecimal itemTotal = product.price.multiply(BigDecimal.valueOf(quantity));
                    totalAmount = totalAmount.add(itemTotal);

                    items.add(new OrderItemData(product.productId, product.sellerId,
                            quantity, product.price, itemTotal));
                }

                // 배송비, 쿠폰
                BigDecimal shippingFee = totalAmount.compareTo(BigDecimal.valueOf(50000)) >= 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(3000);
                BigDecimal couponDiscount = random.nextInt(10) == 0
                        ? totalAmount.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                OrderStatus status = statuses[random.nextInt(statuses.length)];

                // 주문 저장
                orderPs.setLong(1, 1000000 + random.nextInt(9000000)); // buyer_id
                orderPs.setString(2, status.name());
                orderPs.setBigDecimal(3, shippingFee);
                orderPs.setBigDecimal(4, couponDiscount);
                orderPs.setBigDecimal(5, totalAmount.add(shippingFee).subtract(couponDiscount));
                orderPs.setString(6, orderedAtStr);
                orderPs.setString(7, nowStr);
                orderPs.setString(8, nowStr);
                orderPs.addBatch();

                // 주문상품 저장 (order_id는 나중에 설정)
                for (OrderItemData item : items) {
                    itemPs.setLong(1, orderId); // order_id
                    itemPs.setLong(2, item.productId);
                    itemPs.setLong(3, item.sellerId);
                    itemPs.setInt(4, item.quantity);
                    itemPs.setBigDecimal(5, item.unitPrice);
                    itemPs.setBigDecimal(6, item.totalPrice);
                    itemPs.setString(7, nowStr);
                    itemPs.addBatch();
                    totalItemCount++;
                }

                if (orderId % BATCH_SIZE == 0) {
                    orderPs.executeBatch();
                    itemPs.executeBatch();
                    conn.commit();
                    log.info("주문 {} 건, 주문상품 {} 건 저장 완료", orderId, totalItemCount);
                }
            }

            orderPs.executeBatch();
            itemPs.executeBatch();
            conn.commit();

            log.info("주문/주문상품 데이터 생성 완료 - 주문: {} 건, 주문상품: {} 건 (소요시간: {}초)",
                    TOTAL_ORDERS, totalItemCount, (System.currentTimeMillis() - startTime) / 1000);

        } catch (Exception e) {
            throw new RuntimeException("주문 데이터 생성 실패", e);
        }
    }

    private List<ProductInfo> loadProductInfo() {
        String sql = "SELECT id, seller_id, price FROM products";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new ProductInfo(rs.getLong("id"), rs.getLong("seller_id"), rs.getBigDecimal("price")));
    }

    private SellerGrade getRandomGrade(SellerGrade[] grades, int[] distribution, int index) {
        int rand = index % 100;
        int cumulative = 0;
        for (int i = 0; i < distribution.length; i++) {
            cumulative += distribution[i];
            if (rand < cumulative) {
                return grades[i];
            }
        }
        return grades[0];
    }

    public void clearAllData() {
        log.info("모든 데이터 삭제 시작...");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE settlements");
        jdbcTemplate.execute("TRUNCATE TABLE order_items");
        jdbcTemplate.execute("TRUNCATE TABLE orders");
        jdbcTemplate.execute("TRUNCATE TABLE products");
        jdbcTemplate.execute("TRUNCATE TABLE sellers");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("모든 데이터 삭제 완료");
    }

    private record ProductInfo(Long productId, Long sellerId, BigDecimal price) {}
    private record OrderItemData(Long productId, Long sellerId, int quantity,
                                  BigDecimal unitPrice, BigDecimal totalPrice) {}
}
