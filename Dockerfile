# syntax=docker/dockerfile:1.7

############################
# Build stage
############################
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Иногда gradle-плагины дергают git, а shell-скрипты требуют bash
RUN apk add --no-cache bash git

# Кладём только файлы сборки — чтобы прогревать кэш зависимостей
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew

# Прогреваем зависимости с кэшем BuildKit
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --stacktrace dependencies || true

# Теперь уже исходники
COPY src ./src

# Сборка дистрибутива (скрипты запуска + все зависимости)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean installDist --no-daemon --stacktrace

############################
# Runtime stage
############################
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем готовый дистрибутив Gradle Application plugin
COPY --from=build /app/build/install/fishsim-telegram /app

ENV TG_BOT_TOKEN=""
# (опционально) непривилегированный пользователь
RUN addgroup -S app && adduser -S app -G app
USER app

ENTRYPOINT ["./bin/fishsim-telegram"]
