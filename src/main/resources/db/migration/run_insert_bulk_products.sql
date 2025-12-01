-- ============================================================
-- Product 100만 건 삽입 스크립트
-- ============================================================
--
-- 실행 방법 (프로젝트 루트 디렉토리에서):
--
-- 방법 1) 프로시저 생성과 실행을 한번에:
--    mysql -u root -p1234 foo < src/main/resources/db/migration/insert_bulk_products.sql
--    mysql -u root -p1234 foo < src/main/resources/db/migration/run_insert_bulk_products.sql
--
-- 방법 2) MySQL 클라이언트에서 직접 실행:
--    mysql -u root -p1234 foo
--    mysql> source src/main/resources/db/migration/insert_bulk_products.sql
--    mysql> source src/main/resources/db/migration/run_insert_bulk_products.sql
--
-- 방법 3) MySQL 클라이언트에서 프로시저 직접 호출:
--    mysql -u root -p1234 foo
--    mysql> CALL insert_test_members();
--    mysql> CALL insert_bulk_products();
--
-- 예상 소요 시간: 약 5~10분 (시스템 성능에 따라 다름)
-- ============================================================

-- 1단계: 테스트용 Member 데이터 생성 (member가 없을 경우에만)
CALL insert_test_members();

-- 2단계: Product 100만 건 삽입
CALL insert_bulk_products();

-- 삽입된 데이터 확인
SELECT COUNT(*) as total_products FROM products;

-- 인덱스
CREATE INDEX idx_products_price_id ON products(product_price DESC, id DESC);

-- 샘플 데이터 조회 (상위 10개)
SELECT id, owner_id, product_code, name, product_price, stock, created_at
FROM products
ORDER BY id DESC
LIMIT 10;

SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
    LIMIT 20;

-- first-page --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
    LIMIT 10;

-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
    LIMIT 10 OFFSET 10;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 999984.00 OR (product_price = 999984.00 AND products.id < 673417)
ORDER BY products.product_price DESC, products.id DESC
    LIMIT 10;

-- 100 --
-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10 OFFSET 100;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 999861.00 OR (product_price = 999861.00 AND products.id < 1508913)
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10;

-- 500 --
-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10 OFFSET 500;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 999473.00 OR (product_price = 999473.00 AND products.id < 1507279)
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10;

-- 1000 --
-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10 OFFSET 1000;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 998999.00 OR (product_price = 998999.00 AND products.id < 1072042)
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10;

-- 10000 --
-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10 OFFSET 10000;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 990017.00 OR (product_price = 990017.00 AND products.id < 775901)
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10;

-- 500000 --
-- offset --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10 OFFSET 500000;

-- cursor --
EXPLAIN ANALYZE
SELECT *
FROM products JOIN members ON products.owner_id = members.id
WHERE product_price < 499771.00 OR (product_price = 499771.00 AND products.id < 612222)
ORDER BY products.product_price DESC, products.id DESC
LIMIT 10;