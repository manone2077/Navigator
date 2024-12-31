FROM alpine:latest
RUN apk add --no-cache openjdk17  nginx
COPY target/*.jar docker/entrypoint.sh  /app/
COPY docker/dist /myapp/dist
COPY docker/default.conf /etc/nginx/http.d/
EXPOSE 80
WORKDIR /app/
ENTRYPOINT ["./entrypoint.sh"]