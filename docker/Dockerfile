FROM alpine:3.20.3
RUN apk add --no-cache openjdk17  nginx
COPY target/*.jar entrypoint.sh  /myapp/
COPY dist /myapp/dist
COPY default.conf /etc/nginx/http.d/
EXPOSE 80
WORKDIR /myapp/
ENTRYPOINT ["./entrypoint.sh"]