# Stock Concurrency Project

동시성 환경에서의 재고 관리 시스템 성능 최적화 프로젝트

## 기술적 성과

- [슈뢰딩거의 재고: RedisTimeoutException과 불확실성 해결](https://velog.io/@junrain2/%EC%8A%88%EB%A2%B0%EB%94%A9%EA%B1%B0%EC%9D%98-%EC%9E%AC%EA%B3%A0-RedisTimeoutException%EA%B3%BC-%EB%B6%88%ED%99%95%EC%8B%A4%EC%84%B1-%ED%95%B4%EA%B2%B0)
- [대량 데이터 처리 성능 개선](https://velog.io/@junrain2/%EB%8C%80%EB%9F%89-%EB%8D%B0%EC%9D%B4%ED%84%B0)
- [네트워크 장애 환경 구현하기 - Toxiproxy](https://velog.io/@junrain2/%EB%84%A4%ED%8A%B8%EC%9B%8C%ED%81%AC-%EC%9E%A5%EC%95%A0-%ED%99%98%EA%B2%BD-%EA%B5%AC%ED%98%84%ED%95%98%EA%B8%B0-Toxiproxy)
- [동시성 환경에서 재고관리: MySQL과 Redis 성능 비교](https://velog.io/@junrain2/%EB%8F%99%EC%8B%9C%EC%84%B1-%ED%99%98%EA%B2%BD%EC%97%90%EC%84%9C-%EC%9E%AC%EA%B3%A0%EA%B4%80%EB%A6%AC-MySQL%EA%B3%BC-Redis-%EC%84%B1%EB%8A%A5-%EB%B9%84%EA%B5%90)
- [페이지네이션: Cursor vs Offset](https://velog.io/@junrain2/%ED%8E%98%EC%9D%B4%EC%A7%80%EB%84%A4%EC%9D%B4%EC%85%98-cursor-vs-offset)

## 아키텍처

### Layered Architecture

프로젝트는 4개의 계층으로 구성되어 있습니다:

```
┌─────────────────────────────────┐
│   UI (Presentation Layer)       │  - REST API 엔드포인트
│   - Controller                   │  - Request/Response DTO
│   - DTO                          │
└────────────┬────────────────────┘
             │ 의존
┌────────────▼────────────────────┐
│   Application Layer              │  - 유스케이스 구현
│   - Service                      │  - 트랜잭션 관리
│   - Exception                    │  - 비즈니스 로직 조율
└────────────┬────────────────────┘
             │ 의존
┌────────────▼────────────────────┐
│   Domain Layer                   │  - 핵심 비즈니스 로직
│   - Entity                       │  - 도메인 규칙 검증
│   - Repository (Interface)       │  - Value Object
│   - Value Object                 │
└────────────▲────────────────────┘
             │ 구현
┌────────────┴────────────────────┐
│   Infrastructure Layer           │  - 외부 시스템 연동
│   - Repository (Implementation)  │  - 데이터베이스 접근
└─────────────────────────────────┘
```

### 계층별 역할

#### UI Layer (Presentation)
- HTTP 요청/응답 처리
- 데이터 검증 (Validation)
- DTO 변환

#### Application Layer
- 비즈니스 유스케이스 구현
- 유스케이스별로 서비스 분리 (1 UseCase = 1 Service)
- 트랜잭션 경계 설정
- 여러 도메인 객체 조율

#### Domain Layer
- 핵심 비즈니스 로직
- 도메인 규칙 및 제약사항 검증
- 엔티티와 Value Object 정의
- 리포지토리 인터페이스 정의
- 도메인과 관련된 외부 API 인터페이스 정의

#### Infrastructure Layer
- 데이터베이스 접근 구현
- 외부 API 연동
- Domain Layer의 인터페이스 구현

### 의존성 규칙

- **상위 계층 → 하위 계층**: UI → Application → Domain
- **Infrastructure → Domain**: 인터페이스 구현을 통한 의존성 역전 (DIP)
- **Domain Layer는 다른 계층에 의존하지 않음** (순수한 비즈니스 로직)
