#!/bin/bash

# Redis 연결 정보
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}

echo "Initializing Redis stock data..."
echo "Redis: $REDIS_HOST:$REDIS_PORT"
echo ""

# Redis CLI 명령 결정 (docker or local)
REDIS_CMD=""
if command -v redis-cli &> /dev/null; then
    # redis-cli가 설치되어 있는 경우
    REDIS_CMD="redis-cli -h $REDIS_HOST -p $REDIS_PORT"
elif command -v docker &> /dev/null && docker ps --format '{{.Names}}' | grep -q redis; then
    # Docker로 Redis 실행 중인 경우
    REDIS_CONTAINER=$(docker ps --format '{{.Names}}' | grep redis | head -1)
    REDIS_CMD="docker exec -i $REDIS_CONTAINER redis-cli"
else
    echo "Error: redis-cli not found and no Redis docker container detected"
    exit 1
fi

echo "Using Redis command: $REDIS_CMD"
echo ""

# product:1 부터 product:10 까지 재고를 100000개로 초기화
for i in {1..10}
do
    KEY="product:$i"
    $REDIS_CMD SET $KEY 100000 > /dev/null
    echo "Set $KEY = 100000"
done

echo ""
echo "Redis stock initialization completed!"
echo ""

# 초기화된 값 검증
echo "=== Verification ==="
for i in {1..10}
do
    KEY="product:$i"
    VALUE=$($REDIS_CMD GET $KEY)
    echo "$KEY = $VALUE"
done
