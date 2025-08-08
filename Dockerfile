# ===== Stage 1: build =====
FROM gradle:8.7-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY . .
# Собираем self-contained дистрибутив с запускными скриптами
RUN gradle clean installDist --no-daemon

# ===== Stage 2: runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Копируем готовый дистрибутив (скрипты + все зависимости)
COPY --from=build /home/gradle/project/build/install/fishsim-telegram /app

# Не храним токен внутри образа. Передавай его через переменные окружения.
ENV TG_BOT_TOKEN=""

# (опционально) запускаем под непривилегированным пользователем
RUN addgroup -S app && adduser -S app -G app
USER app

# Запуск скрипта, сгенерированного Gradle Application plugin
ENTRYPOINT ["./bin/fishsim-telegram"]
