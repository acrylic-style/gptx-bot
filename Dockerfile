FROM eclipse-temurin:17 AS builder

COPY . .

RUN ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:17 AS runner

WORKDIR /app

COPY --from=builder build/libs/SpicyAzisabaBot.jar .

CMD ["java", "-jar", "SpicyAzisabaBot.jar"]
