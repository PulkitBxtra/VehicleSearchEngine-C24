# Build stage: cache dependencies separately from source so code edits do not
# re-download the world on every deploy.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
# Sized for a 512MB container, which is the smallest target this runs on.
#
# The measured live set is ~70MB heap and ~56MB metaspace, so 55% (about 280MB)
# is generous headroom while leaving room for metaspace, code cache and thread
# stacks underneath the container limit -- the JVM counts only the heap against
# MaxRAMPercentage, and it is the other regions that push a container into an
# OOM kill.
#
# SerialGC because a single small heap on one or two cores gains nothing from a
# concurrent collector and pays for it in threads and footprint.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
