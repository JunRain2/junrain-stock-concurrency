-- 상품 등록 부하 테스트용 데이터 초기화

-- 기존 데이터 삭제
TRUNCATE TABLE products;
TRUNCATE TABLE members;

-- 테스트용 판매자(Owner) 10명 생성
-- Step 1: Owner 1명 사용
-- Step 2: Owner 1-5명 사용 (5개 브랜드)
-- Step 3: Owner 1-10명 사용 (10개 브랜드)
INSERT INTO members (id, member_type, member_name, created_at, updated_at)
VALUES
    (1, 'SELLER', 'Brand_1', NOW(), NOW()),
    (2, 'SELLER', 'Brand_2', NOW(), NOW()),
    (3, 'SELLER', 'Brand_3', NOW(), NOW()),
    (4, 'SELLER', 'Brand_4', NOW(), NOW()),
    (5, 'SELLER', 'Brand_5', NOW(), NOW()),
    (6, 'SELLER', 'Brand_6', NOW(), NOW()),
    (7, 'SELLER', 'Brand_7', NOW(), NOW()),
    (8, 'SELLER', 'Brand_8', NOW(), NOW()),
    (9, 'SELLER', 'Brand_9', NOW(), NOW()),
    (10, 'SELLER', 'Brand_10', NOW(), NOW());

-- Step 1-3: 상품 등록 테스트
-- 상품 등록 테스트는 매번 새로운 상품을 등록하므로 초기 상품 데이터는 불필요
-- Owner만 존재하면 됨
