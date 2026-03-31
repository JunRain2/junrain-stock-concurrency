# CLAUDE.md

## 프로젝트 개요
- **목적**: 동시성 환경에서의 재고 관리 시스템 성능 최적화
- **주요 기술**: Kotlin 1.9.25, Spring Boot 3.5.7, Java 21 (Virtual Threads), MySQL, Redis, Kotlin Coroutines
- **구조**: 단일 모듈 모놀리스

## 아키텍처

### 패키지 구조 (Aggregate-First)
```
src/main/kotlin/com/junrain/stock/
├── StockConcurrencyApplication.kt     # 단일 진입점
├── common/                             # 공통 모듈
│   ├── domain/                         # BaseEntity, Money, Address, ErrorCode, BusinessException, LockRepository
│   ├── dto/                            # ApiResponse, CursorPageResponse, BatchResult
│   └── infra/                          # CoroutineConfig, AuditingConfig, QueryDslConfig, RedisLockRepositoryImpl, ErrorLogRepository, ApiControllerAdvice
├── product/                            # 상품/재고 Aggregate
│   ├── domain/                         # Product, ProductRepository, ProductStockService, OwnerValidationService, vo/ProductCode
│   │   └── exception/                  # ProductNotFoundException, ProductOutOfStockException 등
│   ├── application/                    # ProductOrderService, ProductRegisterService, ProductQueryService, ProductQueryRepository
│   │   ├── command/                    # ProductOrderDto, ProductPurchaseDto, ProductRegisterDto
│   │   └── query/                      # ProductDetailResult, ProductPageQuery, ProductPageResult
│   ├── infra/                          # OwnerValidationServiceImpl, ProductRepositoryImpl, ProductStockServiceImpl
│   │   ├── mysql/                      # JdbcProductRepository, JpaProductRepository
│   │   ├── redis/                      # RedisStockRepository
│   │   ├── querydsl/                   # QueryDslProductQueryRepositoryImpl, QueryDslProductSorter
│   │   └── batch/                      # StockConsistencyBatchJob, StockConsistencyBatchScheduler
│   └── controller/                     # ProductCommandController, ProductQueryController, dto/*
├── order/                              # 주문 Aggregate
│   ├── domain/                         # Order, OrderItem, OrderRepository, ProductCatalogService, vo/OrderCode, vo/Orderer
│   │   └── exception/                  # OrderInvalidException, OrderNotFoudException
│   ├── application/                    # OrderPlacementService
│   │   └── command/                    # OrderPlacementDto
│   └── infra/                          # ProductCatalogServiceImpl, event/OrderPaidEventHandler
├── cart/                               # 장바구니 Aggregate
│   ├── domain/                         # CartItem, CartItemRepository, StockAvailabilityService
│   │   └── exception/                  # CartItemNotFoundException
│   ├── application/                    # CartAddProductService, CartItemQuantityUpdateService
│   │   └── command/                    # CartAddProductCommand, CartAddProductResult 등
│   └── infra/                          # StockAvailabilityServiceImpl
└── member/                             # 회원 Aggregate
    ├── domain/                         # Member, MemberRepository, MemberType
    │   └── exception/                  # MemberNotFoundException
    └── application/                    # MemberRoleVerificationService
```

### 레이어드 아키텍처
- **패턴**: Aggregate-First + 4계층 레이어드 아키텍처 + DDD 원칙
- **구조**: `controller → application → domain ← infra`
- **핵심 원칙**: Domain 계층은 순수 비즈니스 로직만 포함 (다른 계층에 의존하지 않음)

## 코딩 컨벤션
- **네이밍**: 클래스 PascalCase, 변수/함수 camelCase, 패키지 lowercase.dotted
- **들여쓰기**: 4칸 (스페이스)
- **에러 메시지**: 한글 사용

## 선호 라이브러리
- **Redisson (3.52.0)** - Redis 분산락
- **QueryDSL (5.0.0) with kapt** - 타입 안전 쿼리
- **Kotlin Coroutines (1.8.1)** - 비동기 처리
- **TestContainers (1.20.4)** - 통합 테스트 (MySQL, Redis, Toxiproxy)
- **Micrometer Prometheus** - 메트릭 수집
- **kotlin-logging (7.0.3)** - 구조화된 로깅
- **Spring Data JPA + Hibernate** - ORM

## 코딩 표준
- **함수**: 단일 책임 원칙, 도메인 검증은 `init {}` 블록에서 수행
- **에러 처리**:
  - `BusinessException` 상속으로 비즈니스 예외 정의
  - `ErrorCode` enum으로 에러 코드/메시지/HTTP 상태 관리
  - `ApiResponse<T>` 래퍼로 통일된 응답 포맷 사용
  - 도메인 검증 실패 시 `require()` 사용 (한글 메시지)
- **비동기**: `suspend` 함수와 Kotlin coroutines 사용
- **동시성**: Virtual Threads 활성화 (`spring.threads.virtual.enabled=true`)
- **Value Objects**: 불변(immutable) 설계, Operator overloading 활용 (예: `Money`)
- **Batch 작업**: Bulk insert/update 시 chunk 단위로 처리

## 워크플로
- **브랜치**: feature/기능명
- **커밋**: `type: 내용` (한글 메시지)
  - 예: `feat: 로그인 기능 추가`, `refactor: 구조 변경`, `test: 테스트 추가`

## 제약 조건
- **성능**:
  - API 응답 p95 < 200ms
  - Bulk 작업: 최대 5000건, chunk 1000건 단위 처리
  - Redis timeout 명시적 처리 필수
  - Batch statement rewriting 활성화 (`rewriteBatchedStatements=true`)
- **동시성**:
  - 분산 환경에서 Redisson 분산락 사용
  - Optimistic/Pessimistic locking 전략 적절히 적용
  - Virtual Threads로 블로킹 I/O 효율적 처리
- **데이터베이스**: MySQL, 단일 DB (`foo`)
- **테스트**: 통합 테스트는 TestContainers로 실제 DB/Redis 환경 검증

## Docker 환경
- **docker-compose.yml**: 앱 + MySQL + Redis
- **서비스 포트**: 앱 8080, MySQL 3306, Redis 6379
- **실행**: `docker-compose up --build`

## Claude 주의사항
1. Domain 계층은 순수 비즈니스 로직만 포함 (Infrastructure 의존 금지)
2. Entity 생성 시 `init {}` 블록에서 도메인 검증 (`require()` 사용)
3. Bulk 작업 시 chunk 단위 처리 (max 5000건, chunk 1000건)
4. 분산 환경을 고려한 동시성 제어 (Redisson 분산락)
5. Value Object는 불변으로 설계하고 operator overloading 활용
6. 테스트는 TestContainers로 실제 DB/Redis 환경에서 검증
7. 에러 메시지는 한글로 작성
8. `ApiResponse<T>` 래퍼로 일관된 API 응답 구조 유지
9. Repository 인터페이스는 Domain 계층에, 구현은 Infrastructure 계층에 배치
