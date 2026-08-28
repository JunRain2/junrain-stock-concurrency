-- 재고 증가(회수). 상한이 없으므로 수량 검사 없이 전부 증가시킨다.
--
-- KEYS[i] = product_stock:{productId}
-- ARGV[i] = 증가 수량 (KEYS와 같은 순서)
--
-- 반환 {status, index}
--   status 0=성공, 3=상품없음
--   index  실패한 상품의 순번 (1-based). 성공이면 0
--   status 1(이미처리) 2(재고부족)는 증가에서 나올 수 없다. 감소와 규약을 맞추려고 번호만 비워 둔다
--
-- 증가는 감소와 달리 절대 되돌리지 않는다. 보상 감소는 그 사이 들어온 정상 점유를 밟아 오버셀을 만든다.
-- 그래서 증가를 부르는 주체는 StockReconciler 하나뿐이어야 한다 — 둘이 되면 이중 증가 = 오버셀.
--
-- ponytail: 단일 노드 전제. Cluster로 가면 product_stock 키에 hash tag 필요.

-- 없는 상품이 섞였는지 먼저 전부 확인한다. 부분 증가가 생기면 되돌릴 방법이 없다
for i = 1, #KEYS do
    if redis.call('EXISTS', KEYS[i]) == 0 then
        return {3, i}
    end
end

for i = 1, #KEYS do
    redis.call('INCRBY', KEYS[i], ARGV[i])
end

return {0, 0}
