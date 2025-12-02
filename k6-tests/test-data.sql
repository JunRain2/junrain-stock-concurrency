-- 기존 데이터 삭제
TRUNCATE TABLE products;
TRUNCATE TABLE members;

-- 테스트용 판매자 생성
INSERT INTO members (id, member_type, member_name, created_at, updated_at)
VALUES (1, 'SELLER', 'TestSeller', NOW(), NOW());

-- 성능 테스트용 상품 데이터
-- Step 1-3: 정상 처리 테스트 (재고 충분)
-- Step 4: 재고 소진 테스트 (높은 VU로 빠른 소진)
INSERT INTO products (id, owner_id, product_code, product_price, product_currency_code, stock, name, created_at, updated_at)
VALUES
    (1, 1, 'PERF_TEST_001', 10000.00, 'KOR', 100000, 'PerfTest1', NOW(), NOW()),
    (2, 1, 'PERF_TEST_002', 20000.00, 'KOR', 100000, 'PerfTest2', NOW(), NOW()),
    (3, 1, 'PERF_TEST_003', 30000.00, 'KOR', 100000, 'PerfTest3', NOW(), NOW()),
    (4, 1, 'PERF_TEST_004', 40000.00, 'KOR', 100000, 'PerfTest4', NOW(), NOW()),
    (5, 1, 'PERF_TEST_005', 50000.00, 'KOR', 100000, 'PerfTest5', NOW(), NOW()),
    (6, 1, 'PERF_TEST_006', 60000.00, 'KOR', 100000, 'PerfTest6', NOW(), NOW()),
    (7, 1, 'PERF_TEST_007', 70000.00, 'KOR', 100000, 'PerfTest7', NOW(), NOW()),
    (8, 1, 'PERF_TEST_008', 80000.00, 'KOR', 100000, 'PerfTest8', NOW(), NOW()),
    (9, 1, 'PERF_TEST_009', 90000.00, 'KOR', 100000, 'PerfTest9', NOW(), NOW()),
    (10, 1, 'PERF_TEST_010', 100000.00, 'KOR', 100000, 'PerfTest10', NOW(), NOW());
