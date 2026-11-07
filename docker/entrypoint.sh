#!/bin/sh
set -e
mkdir -p /app/data/public/images /app/data/db
nginx
# 使用绝对路径启动：从旧版本容器“重建”时会沿用旧的 PATH 环境变量，其中不包含 /opt/jre/bin
exec /opt/jre/bin/java ${JAVA_OPTS} -jar /app/navigator.jar
