#!/bin/bash

# Redis 완전 초기화 스크립트
# 모든 데이터를 삭제합니다.

# Redis 연결 정보
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}

echo "Clearing all Redis data..."
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

# 모든 Redis 데이터 삭제
$REDIS_CMD FLUSHALL > /dev/null

if [ $? -eq 0 ]; then
    echo "✓ All Redis data cleared successfully!"
else
    echo "✗ Failed to clear Redis data"
    exit 1
fi

echo ""

# 남은 키 확인
KEY_COUNT=$($REDIS_CMD DBSIZE | grep -oE '[0-9]+')
echo "=== Verification ==="
echo "Remaining keys: $KEY_COUNT"
echo ""
