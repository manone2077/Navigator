ARG BASE_IMAGE=alpine:3.22
# 可选：国内构建时指定 apk 镜像加速，例如 --build-arg APK_MIRROR=https://mirrors.aliyun.com
ARG APK_MIRROR=

# ---- 第一阶段：用 jlink 裁剪出只包含所需模块的精简 JRE ----
FROM ${BASE_IMAGE} AS jre
ARG APK_MIRROR
RUN if [ -n "$APK_MIRROR" ]; then sed -i "s#https\?://dl-cdn.alpinelinux.org#${APK_MIRROR}#g" /etc/apk/repositories; fi \
 && apk add --no-cache openjdk17-jdk binutils \
 && jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.naming.dns,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output /opt/jre

# ---- 第二阶段：运行镜像 ----
FROM ${BASE_IMAGE}
ARG APK_MIRROR
RUN if [ -n "$APK_MIRROR" ]; then sed -i "s#https\?://dl-cdn.alpinelinux.org#${APK_MIRROR}#g" /etc/apk/repositories; fi \
 && apk add --no-cache nginx tzdata ca-certificates
COPY --from=jre /opt/jre /opt/jre
ENV PATH=/opt/jre/bin:$PATH \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseSerialGC -Xss512k -Xms32m -Xmx192m"
WORKDIR /app/
COPY target/*.jar /app/navigator.jar
COPY docker/entrypoint.sh /app/
COPY docker/dist/ /app/dist/
COPY docker/default.conf /etc/nginx/http.d/default.conf
RUN chmod +x /app/entrypoint.sh
VOLUME /app/data/
EXPOSE 80
# 通过 nginx 访问后端健康检查接口，同时覆盖 nginx 和 java 两个进程
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/api/health || exit 1
ENTRYPOINT ["./entrypoint.sh"]
