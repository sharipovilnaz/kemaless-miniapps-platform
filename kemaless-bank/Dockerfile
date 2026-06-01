FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM kemaless/miniapp-base-image:1.0

WORKDIR /app

COPY --from=builder /build/dependencies/ .
COPY --from=builder /build/spring-boot-loader/ .
COPY --from=builder /build/snapshot-dependencies/ .
COPY --from=builder /build/application/ .

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]