# 一条命令完成构建：docker build .
# 前端（node）和后端（maven）在构建机架构上编译，产物与 CPU 架构无关；
# 只有精简 JRE 和运行镜像按目标架构（amd64 / arm64）构建
ARG BASE_IMAGE=alpine:3.22
ARG NODE_IMAGE=node:20-alpine
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
# 可选：国内构建时加速，例如 --build-arg APK_MIRROR=https://mirrors.aliyun.com
ARG APK_MIRROR=
ARG NPM_REGISTRY=

# ---- 前端 ----
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS frontend
ARG NPM_REGISTRY
WORKDIR /src
COPY frontend/package.json frontend/yarn.lock ./
RUN --mount=type=cache,target=/usr/local/share/.cache/yarn \
    if [ -n "$NPM_REGISTRY" ]; then yarn config set registry "$NPM_REGISTRY"; fi \
 && yarn install --frozen-lockfile --network-timeout 600000
COPY frontend/ ./
RUN NAV_DIST=/out yarn build

# ---- 后端 ----
FROM --platform=$BUILDPLATFORM ${MAVEN_IMAGE} AS backend
WORKDIR /src
COPY pom.xml ./
COPY src ./src
# 测试在 CI 的 test 任务中运行，这里只打包；依赖缓存在 BuildKit 缓存目录中，重复构建无需重新下载
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- 用 jlink 裁剪出只包含所需模块的精简 JRE ----
FROM ${BASE_IMAGE} AS jre
ARG APK_MIRROR
RUN if [ -n "$APK_MIRROR" ]; then sed -i "s#https\?://dl-cdn.alpinelinux.org#${APK_MIRROR}#g" /etc/apk/repositories; fi \
 && apk add --no-cache openjdk17-jdk binutils \
 && jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.naming.dns,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output /opt/jre

# ---- 运行镜像 ----
FROM ${BASE_IMAGE}
ARG APK_MIRROR
RUN if [ -n "$APK_MIRROR" ]; then sed -i "s#https\?://dl-cdn.alpinelinux.org#${APK_MIRROR}#g" /etc/apk/repositories; fi \
 && apk add --no-cache nginx tzdata ca-certificates
COPY --from=jre /opt/jre /opt/jre
# 从旧版本容器“重建”时会沿用旧的 PATH，保证 java 在默认 PATH 中也能找到
RUN ln -s /opt/jre/bin/java /usr/bin/java
ENV PATH=/opt/jre/bin:$PATH \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseSerialGC -Xss512k -Xms32m -Xmx192m"
WORKDIR /app/
COPY --from=backend /src/target/navigator.jar /app/navigator.jar
COPY --from=frontend /out/ /app/dist/
COPY docker/entrypoint.sh /app/
COPY docker/default.conf /etc/nginx/http.d/default.conf
RUN chmod +x /app/entrypoint.sh
VOLUME /app/data/
EXPOSE 80
# 通过 nginx 访问后端健康检查接口，同时覆盖 nginx 和 java 两个进程
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/api/health || exit 1
ENTRYPOINT ["./entrypoint.sh"]
