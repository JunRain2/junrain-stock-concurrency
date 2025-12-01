-- 100만 건의 Product 데이터를 삽입하는 프로시저
-- 사전에 member 데이터가 최소 1개 이상 존재해야 합니다.
-- Member가 없는 경우 자동으로 테스트용 Member를 생성합니다.

DELIMITER $$

DROP PROCEDURE IF EXISTS insert_test_members$$

CREATE PROCEDURE insert_test_members()
BEGIN
    DECLARE member_count INT;

    -- 기존 member 수 확인
    SELECT COUNT(*) INTO member_count FROM members;

    -- member가 없으면 테스트용 member 10개 생성
    IF member_count = 0 THEN
        INSERT INTO members (member_type, member_name, created_at, updated_at, deleted_at)
        VALUES
            ('SELLER', 'TestSeller1', NOW(), NOW(), NULL),
            ('SELLER', 'TestSeller2', NOW(), NOW(), NULL),
            ('SELLER', 'TestSeller3', NOW(), NOW(), NULL),
            ('SELLER', 'TestSeller4', NOW(), NOW(), NULL),
            ('SELLER', 'TestSeller5', NOW(), NOW(), NULL),
            ('BUYER', 'TestBuyer1', NOW(), NOW(), NULL),
            ('BUYER', 'TestBuyer2', NOW(), NOW(), NULL),
            ('BUYER', 'TestBuyer3', NOW(), NOW(), NULL),
            ('BUYER', 'TestBuyer4', NOW(), NOW(), NULL),
            ('BUYER', 'TestBuyer5', NOW(), NOW(), NULL);

        SELECT CONCAT('테스트용 Member 10건 생성 완료') AS result;
    ELSE
        SELECT CONCAT('기존 Member ', member_count, '건 존재, Member 생성 스킵') AS result;
    END IF;
END$$

DROP PROCEDURE IF EXISTS insert_bulk_products$$

CREATE PROCEDURE insert_bulk_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 10000;
    DECLARE total_count INT DEFAULT 1000000;
    DECLARE owner_id BIGINT;
    DECLARE min_member_id BIGINT;
    DECLARE max_member_id BIGINT;
    DECLARE random_member_count INT;

    -- 트랜잭션 시작
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- members 테이블에서 사용 가능한 member_id 범위 조회
    SELECT MIN(id), MAX(id), COUNT(*)
    INTO min_member_id, max_member_id, random_member_count
    FROM members;

    -- member가 없으면 에러 발생
    IF random_member_count = 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'members 테이블에 데이터가 없습니다. 먼저 member 데이터를 삽입해주세요.';
    END IF;

    -- 기존 products 데이터 삭제 (선택사항 - 필요시 주석 해제)
    -- TRUNCATE TABLE products;

    SET autocommit = 0;

    WHILE i <= total_count DO
        START TRANSACTION;

        -- batch_size만큼 INSERT 반복
        INSERT INTO products (
            owner_id,
            product_code,
            product_price,
            product_currency_code,
            stock,
            name,
            created_at,
            updated_at,
            deleted_at
        )
        SELECT
            -- member_id를 랜덤하게 선택 (존재하는 member 중에서)
            (SELECT id FROM members ORDER BY RAND() LIMIT 1),
            -- 고유한 product_code 생성 (PRODUCT-{순번})
            CONCAT('PRODUCT-', LPAD(i + row_num - 1, 10, '0')),
            -- 가격은 1000원 ~ 1000000원 사이 랜덤
            FLOOR(1000 + (RAND() * 999000)),
            -- 통화 코드
            'KOR',
            -- 재고는 0 ~ 10000 사이 랜덤
            FLOOR(RAND() * 10001),
            -- 상품명 (Product {순번})
            CONCAT('Product ', i + row_num - 1),
            -- created_at
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
            -- updated_at
            NOW() - INTERVAL FLOOR(RAND() * 30) DAY,
            -- deleted_at (NULL)
            NULL
        FROM (
            SELECT (@row_num := @row_num + 1) as row_num
            FROM
                (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
                (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
                (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t3,
                (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t4,
                (SELECT @row_num := 0) r
        ) nums
        LIMIT batch_size;

        COMMIT;

        SET i = i + batch_size;

        -- 진행 상황 출력 (10만 건마다)
        IF i % 100000 = 0 THEN
            SELECT CONCAT('진행 상황: ', i, ' / ', total_count, ' 건 완료') AS progress;
        END IF;
    END WHILE;

    SET autocommit = 1;

    SELECT CONCAT('총 ', total_count, '건의 Product 데이터 삽입 완료') AS result;
END$$

DELIMITER ;
