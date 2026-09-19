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

# AUDIT_BACKEND.md, finding #14 : exécution en utilisateur non-root plutôt qu'en root par défaut.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=builder /app/target/*.jar app.jar
# logging.file.name=logs/app.log (application.properties) écrit un chemin relatif au répertoire
# de travail : le dossier doit exister et être inscriptible par l'utilisateur non-root avant de
# quitter root, Spring Boot ne le crée pas si le parent (/app) ne lui appartient pas.
RUN mkdir -p logs && chown -R spring:spring /app

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]