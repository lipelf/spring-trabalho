# Imagem da API para o deploy (Railway). Duas etapas: compila com o JDK e roda so com o JRE.

# ---------- build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Baixa as dependencias antes de copiar o codigo, para o cache do Docker reaproveitar
# essa camada quando so o codigo mudar.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# o mvnw esta sem permissao de execucao no git e pode vir com CRLF de um checkout no Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# os testes rodam localmente (./mvnw test); aqui so empacota
RUN ./mvnw -B -q package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Usa ate 75% da memoria do container para o heap, em vez do padrao de 25%
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# O Railway informa a porta pela variavel PORT; o application.yml le ela (padrao 8080)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
