FROM alpine:latest
VOLUME /app/data/
RUN apk add --no-cache openjdk17  nginx
COPY target/*.jar docker/entrypoint.sh  /app/
COPY docker/dist/ /app/dist/
COPY docker/default.conf /etc/nginx/http.d/
RUN chmod +x /app/entrypoint.sh
EXPOSE 80
WORKDIR /app/
ENTRYPOINT ["./entrypoint.sh"]