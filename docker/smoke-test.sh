#!/bin/sh
# 镜像冒烟测试：用法 docker/smoke-test.sh <新镜像> [上一个版本的镜像]
#
# 指定上一个版本时会模拟真实升级：先用旧镜像启动并写入数据，
# 再像 Portainer “重建容器”一样沿用旧容器的全部环境变量（包括旧的 PATH、JAVA_OPTS）启动新镜像，
# 检查新版本能否正常启动、旧数据是否还在。
set -eu
NEW=$1
PREV=${2:-}
PORT=${SMOKE_PORT:-18080}
BASE="http://127.0.0.1:$PORT"
DATA=$(mktemp -d)
ENVFILE=$(mktemp)

cleanup() {
  docker rm -f nav-smoke-old nav-smoke >/dev/null 2>&1 || true
  docker run --rm -v "$DATA:/data" --entrypoint sh "$NEW" -c 'rm -rf /data/*' >/dev/null 2>&1 || true
  rm -rf "$DATA" "$ENVFILE"
}
trap cleanup EXIT

fail() {
  echo "❌ $*"
  docker logs --tail 80 nav-smoke 2>&1 || true
  exit 1
}

wait_ready() {
  i=0
  until curl -sf "$BASE/api/all" >/dev/null 2>&1; do  # 旧版本没有 /api/health，用各版本都有的接口判断启动完成
    i=$((i + 1))
    [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ] || fail "$1 已退出"
    [ $i -lt 90 ] || fail "$1 启动超时"
    sleep 1
  done
}

if [ -n "$PREV" ]; then
  echo "▶ 用上一个版本 $PREV 写入数据"
  docker run -d --name nav-smoke-old -p "$PORT:80" -v "$DATA:/app/data" "$PREV" >/dev/null
  wait_ready nav-smoke-old
  curl -sf -X POST "$BASE/api/add" -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
    -d '{"title":"smoke-old","url":"http://127.0.0.1:1"}' >/dev/null || fail "旧版本写入数据失败"
  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' nav-smoke-old | grep . > "$ENVFILE"
  echo "  沿用旧容器的环境变量：$(tr '\n' ' ' < "$ENVFILE")"
  # 与 Portainer“重建容器”一致：先正常停止（SIGTERM，数据库正常关闭），再删除
  docker stop -t 30 nav-smoke-old >/dev/null
  docker rm nav-smoke-old >/dev/null
fi

echo "▶ 启动新镜像 $NEW"
docker run -d --name nav-smoke -p "$PORT:80" -v "$DATA:/app/data" --env-file "$ENVFILE" "$NEW" >/dev/null
wait_ready nav-smoke

echo "▶ 检查页面与接口"
html=$(curl -sf "$BASE/") || fail "首页无法访问"
echo "$html" | grep -q 'id="app"' || fail "首页内容不正确（前端未打包进镜像？）"
asset=$(echo "$html" | grep -oE '/assets/[^"]+\.js' | head -1)
[ -n "$asset" ] && curl -sf -o /dev/null "$BASE$asset" || fail "前端资源无法加载：$asset"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/some/page")" = "200" ] || fail "页面路由回退失败"
curl -sf "$BASE/api/health" | grep -q '"version"' || fail "健康检查接口异常"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/deleteAll")" = "405" ] || fail "修改类接口不应接受 GET"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/deleteAll")" = "403" ] || fail "缺少防护请求头的写请求应被拒绝"
if [ -n "$PREV" ]; then
  curl -sf "$BASE/api/all" | grep -q 'smoke-old' || fail "升级后旧数据丢失"
fi

echo "▶ 等待 Docker 健康检查"
i=0
until [ "$(docker inspect -f '{{.State.Health.Status}}' nav-smoke)" = "healthy" ]; do
  i=$((i + 1))
  [ $i -lt 60 ] || fail "容器健康检查未通过"
  sleep 2
done
echo "✅ 冒烟测试通过：$NEW"
