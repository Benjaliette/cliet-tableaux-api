# ==========================================
# Étape 1 : Compilation (Build)
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .

RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# ==========================================
# Étape 2 : Production (Run)
# ==========================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]