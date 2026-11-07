ARG BASE_IMAGE=alpine:3.22
FROM ${BASE_IMAGE}
RUN apk add --no-cache openjdk17-jre-headless nginx tzdata
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms64m -Xmx256m"
WORKDIR /app/
COPY target/*.jar /app/navigator.jar
COPY docker/entrypoint.sh /app/
COPY docker/dist/ /app/dist/
COPY docker/default.conf /etc/nginx/http.d/default.conf
RUN chmod +x /app/entrypoint.sh
VOLUME /app/data/
EXPOSE 80
ENTRYPOINT ["./entrypoint.sh"]
