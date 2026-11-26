FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

ARG JAR_FILE=target/kemaless-bank-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-jammy

RUN addgroup --system spring && adduser --system --ingroup spring spring

WORKDIR /app
RUN chown spring:spring /app

USER spring:spring

COPY --from=builder /build/dependencies/ .
COPY --from=builder /build/spring-boot-loader/ .
COPY --from=builder /build/snapshot-dependencies/ .
COPY --from=builder /build/application/ .

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]