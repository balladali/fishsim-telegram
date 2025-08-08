# syntax=docker/dockerfile:1.7

# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
RUN apt-get update && apt-get install -y git bash && rm -rf /var/lib/apt/lists/*

# обязательно закоммить gradle wrapper: gradlew + gradle/wrapper/*
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew

# кэш зависимостей
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean installDist --no-daemon --stacktrace

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/fishsim-telegram /app
ENV TG_BOT_TOKEN=""
USER 1001
ENTRYPOINT ["./bin/fishsim-telegram"]
