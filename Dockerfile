FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 --create-home application
WORKDIR /application
COPY --from=builder /build/target/offboarding-0.1.0.jar application.jar
USER application
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]