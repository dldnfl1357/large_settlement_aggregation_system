package com.settlement._data_generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataGeneratorRunner implements ApplicationRunner {

    private final TestDataGenerator testDataGenerator;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // --generate-data 또는 --generate-data=true 둘 다 지원
        boolean shouldGenerate = args.containsOption("generate-data")
                || args.getNonOptionArgs().contains("generate-data");

        if (shouldGenerate) {
            log.info("========== 데이터 생성 모드로 실행됩니다 ==========");

            boolean shouldClear = args.containsOption("clear")
                    || args.getNonOptionArgs().contains("clear");
            if (shouldClear) {
                testDataGenerator.clearAllData();
            }

            String mode = args.containsOption("mode")
                    ? args.getOptionValues("mode").get(0)
                    : "all";

            switch (mode) {
                case "sellers" -> testDataGenerator.generateSellers();
                case "products" -> testDataGenerator.generateProducts();
                case "orders" -> testDataGenerator.generateOrdersAndItems();
                case "all" -> testDataGenerator.generateAll();
                default -> log.warn("알 수 없는 모드: {}. 사용 가능: sellers, products, orders, all", mode);
            }

            log.info("========== 데이터 생성 완료. 애플리케이션을 종료합니다 ==========");
            System.exit(0);
        }
    }
}
