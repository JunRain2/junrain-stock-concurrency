-- 상품 등록 부하 테스트용 데이터 초기화

-- 기존 데이터 삭제
TRUNCATE TABLE products;
TRUNCATE TABLE members;

-- 테스트용 판매자(Owner) 생성
INSERT INTO members (id, member_type, member_name, created_at, updated_at)
VALUES (1, 'SELLER', 'BulkTestSeller', NOW(), NOW());

-- Phase 1-4: 상품 등록 테스트
-- 상품 등록 테스트는 매번 새로운 상품을 등록하므로 초기 상품 데이터는 불필요
-- Owner만 존재하면 됨
