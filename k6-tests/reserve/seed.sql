-- 재고 점유 부하테스트 시드
--
-- 상품 100개, 재고 각 500,000.
-- 100개인 이유: 최고 rate 800에서도 행당 8 RPS라 경합이 사실상 없다 (시나리오 B의 기준선 조건).
-- 재고 500,000인 이유: 재고 부족이 측정에 섞이면 안 된다. 워밍업이 사다리 최고 rate로
-- 돌기 때문에 1회 소비량이 사다리에 비례해 늘어나며, 여유를 크게 두어 사다리를 올릴 때마다
-- 재계산하지 않아도 되게 한다.
--
-- 주의: ddl-auto=create 이므로 앱이 기동한 뒤에 적용해야 한다.

TRUNCATE TABLE reservations;
TRUNCATE TABLE products;
TRUNCATE TABLE members;

INSERT INTO members (id, member_type, member_name, created_at, updated_at)
VALUES (1, 'SELLER', 'BenchSeller', NOW(), NOW());

INSERT INTO products (id, owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1
                           UNION ALL
                           SELECT n + 1
                           FROM seq
                           WHERE n < 100)
SELECT n,
       1,
       CONCAT('BENCH_', LPAD(n, 4, '0')),
       10000.00,
       'KOR',
       500000,
       CONCAT('Bench', n),
       NOW(),
       NOW()
FROM seq;
