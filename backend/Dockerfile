FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml ./
COPY .mvn .mvn
RUN mvn -B -q dependency:go-offline -Dmaven.test.skip=true
COPY src src
RUN mvn -B -q package -Dmaven.test.skip=true && cp target/*.jar /build/app.jar

FROM eclipse-temurin:26-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
