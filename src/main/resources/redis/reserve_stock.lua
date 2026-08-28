-- 재고 점유. n개 상품이 모두 충분할 때만 전부 차감하고, 되돌릴 근거를 같은 원자 단위 안에 남긴다.
--
-- KEYS[1]   = reservation:{trxId}       예약 본문(재고키 -> 수량) + 멱등 가드
-- KEYS[2]   = reservation_expire        만료 인덱스(ZSET). member=trxId, score=만료 시각(ms)
-- KEYS[i+2] = available_stock:{productId}
-- ARGV[1]   = trxId
-- ARGV[2]   = 만료 시각(epoch milliseconds). 호출자가 계산해서 넘긴다
-- ARGV[i+2] = 차감 수량 (KEYS와 같은 순서)
--
-- 반환 {status, index}
--   status 0=성공, 1=이미처리, 2=재고부족, 3=상품없음
--   index  실패한 상품의 순번 (1-based). 실패가 아니면 0
--
-- 차감과 "되돌릴 근거"가 한 원자 단위에 들어간다. 그래서 이 스크립트가 성공했는지를 사후에 따로
-- 판정할 필요가 없다 - ZSET에 등재됐다는 사실 자체가 차감이 적용됐다는 증거다.
-- 만료 시각이 지나면 스케줄러가 등재된 것을 조건 없이 되돌린다. 정상 종료한 요청이든 응답을
-- 못 받고 죽은 요청이든 처리가 같으므로, "적용됐나?"를 물어볼 이유가 사라진다.
--
-- 단일 노드 전제. Cluster로 가면 세 종류의 키가 같은 슬롯에 있어야 해서 hash tag가 필요하다.

if redis.call('EXISTS', KEYS[1]) == 1 then
    return {1, 0}
end

local n = #KEYS - 2

-- 검사는 전부 앞, 쓰기는 전부 뒤. Lua는 원자적이지만 롤백이 없어서, 첫 쓰기 이후에 실패할 수 있는
-- 연산이 남아 있으면 되돌릴 수 없는 "반쯤 적용된 상태"가 만들어진다
for i = 1, n do
    local current = redis.call('GET', KEYS[i + 2])
    if current == false then
        return {3, i}
    end
    if tonumber(current) < tonumber(ARGV[i + 2]) then
        return {2, i}
    end
end

-- 필드를 재고 키 자체로 둔다. 보상이 productId를 파싱하거나 MySQL을 다시 읽지 않고
-- HGETALL 결과를 그대로 INCRBY에 넘길 수 있다 - 복구 근거가 Redis 안에서 자기완결적이 된다.
-- TTL을 걸지 않는다. 스케줄러보다 먼저 사라지면 되돌릴 수량을 잃고 그 재고는 고아가 된다.
-- 지우는 주체는 만료/확정 스크립트 하나뿐이다.
local body = {}
for i = 1, n do
    redis.call('DECRBY', KEYS[i + 2], ARGV[i + 2])
    body[#body + 1] = KEYS[i + 2]
    body[#body + 1] = ARGV[i + 2]
end
redis.call('HSET', KEYS[1], unpack(body))

-- 만료 시각은 호출자가 계산한 값을 그대로 쓴다. 여기서 redis.call('TIME')으로 다시 찍으면
-- 같은 예약의 만료 시각이 MySQL(앱 시계)과 Redis(서버 시계) 두 값으로 갈린다
redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])

return {0, 0}
