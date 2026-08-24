-- 재고 차감. n개 상품이 모두 충분할 때만 전부 차감한다.
--
-- KEYS[1]      = stock:op:{opId}
-- KEYS[2..n+1] = product_stock:{productId}
-- ARGV[1]      = op 키 TTL(초)
-- ARGV[2..n+1] = 차감 수량 (KEYS와 같은 순서)
--
-- 반환 {status, index}
--   status 0=성공, 1=이미처리(멱등), 2=재고부족, 3=상품없음
--   index  실패한 상품의 순번 (1-based, 상품만 셈). 성공/이미처리면 0
--
-- ponytail: 단일 노드 전제. Cluster로 가면 product_stock 키에 hash tag 필요.

if redis.call('EXISTS', KEYS[1]) == 1 then
    return {1, 0}
end

-- 전부 검사한 뒤에 차감하므로 부분 차감이 생기지 않는다 (보상 불필요)
for i = 2, #KEYS do
    local current = redis.call('GET', KEYS[i])
    if current == false then
        return {3, i - 1}
    end
    if tonumber(current) < tonumber(ARGV[i]) then
        return {2, i - 1}
    end
end

for i = 2, #KEYS do
    redis.call('DECRBY', KEYS[i], ARGV[i])
end

redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
return {0, 0}
