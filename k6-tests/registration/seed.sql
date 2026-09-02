-- 상품 등록 부하테스트 시드.
--
-- 등록 시나리오는 매 요청이 새 상품을 만드므로 초기 상품이 필요 없다. 판매자만 있으면 된다.
-- products를 비우는 것이 시드의 본체다 - 이전 실행이 남긴 코드가 있으면
-- 신규 삽입을 재려던 요청이 중복 스킵 측정으로 바뀐다.
--
-- 판매자가 10명인 이유는 시나리오 B가 VU를 브랜드에 나눠 주기 때문이다. 전부 한 owner로 보내면
-- owner_id 인덱스 삽입이 한 값에 몰려 실제로는 없을 핫스팟이 생긴다. 나머지 시나리오는 OWNER_ID만 쓴다.

TRUNCATE TABLE products;
TRUNCATE TABLE members;

INSERT INTO members (id, member_type, member_name, created_at, updated_at)
VALUES (1,  'SELLER', '브랜드1',  NOW(), NOW()),
       (2,  'SELLER', '브랜드2',  NOW(), NOW()),
       (3,  'SELLER', '브랜드3',  NOW(), NOW()),
       (4,  'SELLER', '브랜드4',  NOW(), NOW()),
       (5,  'SELLER', '브랜드5',  NOW(), NOW()),
       (6,  'SELLER', '브랜드6',  NOW(), NOW()),
       (7,  'SELLER', '브랜드7',  NOW(), NOW()),
       (8,  'SELLER', '브랜드8',  NOW(), NOW()),
       (9,  'SELLER', '브랜드9',  NOW(), NOW()),
       (10, 'SELLER', '브랜드10', NOW(), NOW());
