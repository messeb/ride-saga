# The boot jar is built by Gradle on the host/CI runner (bytecode is arch-independent);
# this Dockerfile only extracts and layers it, keeping multi-arch builds fast.
FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /workspace
COPY services/driver-matching-service/build/libs/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 1001 app
WORKDIR /app
# dependencies first: this layer only changes when libraries change
COPY --from=extractor --chown=app:app /workspace/extracted/lib lib
COPY --from=extractor --chown=app:app /workspace/extracted/app.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
