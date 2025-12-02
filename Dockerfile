# Multi-stage build for optimized image size
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create logs directory
RUN mkdir -p /app/logs

# Copy built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# 4GB 전용 인스턴스 JVM 설정
# - Xms/Xmx: 힙 메모리 3GB (나머지 1GB는 Metaspace, Native, OS용)
# - G1GC: 대용량 배치 처리에 적합
# - MaxGCPauseMillis: GC 일시정지 목표 200ms
ENV JAVA_OPTS="-Xms2G -Xmx3G \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -XX:+PrintGCDetails \
    -XX:+PrintGCDateStamps \
    -Xloggc:/app/logs/gc.log"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
