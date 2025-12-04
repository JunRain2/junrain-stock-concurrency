import redis from 'k6/experimental/redis';

// Redis 연결 설정
const redisClient = new redis.Client('redis://localhost:6379');

export function setup() {
    console.log('Initializing Redis stock data...');

    // product:1 부터 product:10 까지 재고를 100000개로 초기화
    for (let i = 1; i <= 10; i++) {
        const key = `product:${i}`;
        redisClient.set(key, '100000');
        console.log(`Set ${key} = 100000`);
    }

    console.log('Redis stock initialization completed!');

    // 초기화된 값 검증
    console.log('\n=== Verification ===');
    for (let i = 1; i <= 10; i++) {
        const key = `product:${i}`;
        const value = redisClient.get(key);
        console.log(`${key} = ${value}`);
    }
}

export default function() {
    // 아무것도 하지 않음 (setup만 실행)
}

export const options = {
    // 단순히 setup만 실행하고 종료
    vus: 1,
    iterations: 1,
    duration: '1s',
};
