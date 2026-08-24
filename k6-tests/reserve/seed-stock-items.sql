-- SKIP LOCKED 전략 전용 시드. 다른 전략에서는 적용하지 않는다.
--
-- stock_items는 products.stock과 달리 재고 1개당 물리 행이라 100개 x 500,000행으로 채우면
-- 시드 자체가 느려진다. 시나리오 A가 항상 상품 1만 때리므로 상품 1만 500,000행을 채우고,
-- 나머지는 시나리오 B가 쓸 만큼(여유 포함)만 채운다.
--
-- 주의: seed.sql이 먼저 돌아야 한다(products TRUNCATE + stock_items TRUNCATE).

SET SESSION cte_max_recursion_depth = 1000000;

-- 상품 1(시나리오 A 전용): 500,000행.
INSERT INTO stock_items (product_id, status)
WITH RECURSIVE seq (n) AS (SELECT 1
                           UNION ALL
                           SELECT n + 1
                           FROM seq
                           WHERE n < 500000)
SELECT 1, 'AVAILABLE'
FROM seq;

-- 나머지 99개(시나리오 B용): 20,000행씩. 최고 rate 1000에서 40초 도는 각 구간이
-- 상품당 400건 남짓 쓰므로 사다리 전체를 몇 번 반복해도 남을 여유다.
INSERT INTO stock_items (product_id, status)
WITH RECURSIVE seq (n) AS (SELECT 1
                           UNION ALL
                           SELECT n + 1
                           FROM seq
                           WHERE n < 20000),
               prod (p) AS (SELECT 2
                            UNION ALL
                            SELECT p + 1
                            FROM prod
                            WHERE p < 100)
SELECT p, 'AVAILABLE'
FROM prod
         CROSS JOIN seq;
