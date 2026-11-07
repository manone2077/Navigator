#!/bin/sh
set -e
mkdir -p /app/data/public/images /app/data/db
nginx
exec java ${JAVA_OPTS} -jar /app/navigator.jar
