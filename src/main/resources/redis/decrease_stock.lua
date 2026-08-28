-- 재고 차감. n개 상품이 모두 충분할 때만 전부 차감한다.
--
-- KEYS[i] = product_stock:{productId}
-- ARGV[i] = 차감 수량 (KEYS와 같은 순서)
--
-- 반환 {status, index}
--   status 0=성공, 2=재고부족, 3=상품없음
--   index  실패한 상품의 순번 (1-based). 성공이면 0
--   status 1(이미처리)은 멱등키를 없애면서 사라졌다. 감소/증가 규약을 맞추려고 번호만 비워 둔다
--
-- 멱등키가 없다. 같은 명령이 두 번 도착하면 두 번 차감된다 = 오버셀.
-- Redisson 커넥션 재시도(retryAttempts)를 0으로 묶어 두는 것이 이를 막는 유일한 장치다.
-- RedissonConfig 참고.
--
-- ponytail: 단일 노드 전제. Cluster로 가면 product_stock 키에 hash tag 필요.

-- 전부 검사한 뒤에 차감하므로 부분 차감이 생기지 않는다 (보상 불필요)
for i = 1, #KEYS do
    local current = redis.call('GET', KEYS[i])
    if current == false then
        return {3, i}
    end
    if tonumber(current) < tonumber(ARGV[i]) then
        return {2, i}
    end
end

for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], ARGV[i])
end

return {0, 0}
