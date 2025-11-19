# Demo Project

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
