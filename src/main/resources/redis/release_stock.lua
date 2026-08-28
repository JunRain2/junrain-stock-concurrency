-- 만기된 점유 회수. 등재된 예약 하나를 조건 없이 되돌린다.
--
-- KEYS[1] = reservation:{trxId}       되돌릴 수량(재고키 -> 수량)
-- KEYS[2] = reservation_expire        만료 인덱스(ZSET)
-- ARGV[1] = trxId
--
-- 반환 1=되돌림, 0=이미 누군가 가져갔음
--
-- ZREM 성공이 회수 가드다. 같은 누수를 둘이 되돌리면 이중 증가 = 오버셀이라 조회와 복구를
-- 나눌 수 없다. 한 스크립트 안에서 실제로 제거한 호출만 통과하므로 실행자가 몇이든 한 번만 돈다.

if redis.call('ZREM', KEYS[2], ARGV[1]) == 0 then
    return 0
end

-- 필드가 재고 키 자체라 파싱도, MySQL 재조회도 없다
local body = redis.call('HGETALL', KEYS[1])
for i = 1, #body, 2 do
    redis.call('INCRBY', body[i], body[i + 1])
end

-- 점유 기록의 수명은 여기까지다. TTL을 안 걸었으므로 지우는 주체는 이 스크립트 하나뿐이다
redis.call('DEL', KEYS[1])

return 1
