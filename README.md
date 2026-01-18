# Common Core

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-6DB33F.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00.svg?logo=openjdk)](https://openjdk.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Commerce 플랫폼의 모든 마이크로서비스를 위한 공통 핵심 라이브러리입니다.

## 개요

`common-core`는 MSA 환경에서 일관된 개발 경험을 제공하기 위한 공통 기능을 포함합니다:

- **에러 처리**: 표준화된 에러 코드 및 예외 처리
- **API 응답**: 일관된 응답 포맷 및 자동 래핑
- **이벤트 시스템**: CloudEvents 스펙 기반 도메인 이벤트 인프라
- **유틸리티**: JSON 직렬화, 트랜잭션 관리, 커스텀 어노테이션

## 설치

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/koosco-commerce/common-core")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GH_USER")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GH_TOKEN")
        }
    }
}

dependencies {
    implementation("com.koosco:common-core:0.2.2")
}
```

### 인증 설정

`~/.gradle/gradle.properties`에 GitHub 인증 정보를 설정합니다:

```properties
gpr.user=your-github-username
gpr.token=your-github-token
```

또는 환경 변수로 설정:
```bash
export GH_USER=your-github-username
export GH_TOKEN=your-github-token
```

## 패키지 구조

```
com.koosco.common.core/
├── annotation/      # 커스텀 어노테이션 (@UseCase, 유효성 검사 어노테이션)
├── config/          # Spring Boot Auto Configuration
├── error/           # 에러 코드 및 API 에러 구조
├── event/           # 도메인 이벤트 및 CloudEvent 인프라
├── exception/       # 예외 계층 및 글로벌 핸들러
├── response/        # API 응답 래퍼
├── transaction/     # 트랜잭션 관리 유틸리티
└── util/            # JSON 유틸리티
```

## 주요 기능

### 1. 에러 처리

#### ErrorCode 인터페이스

서비스별 도메인 에러 코드를 정의할 수 있습니다:

```kotlin
enum class OrderErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : ErrorCode {
    ORDER_NOT_FOUND("ORDER-404", "주문을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("ORDER-400-001", "재고가 부족합니다", HttpStatus.BAD_REQUEST)
}
```

#### 사전 정의된 CommonErrorCode

일반적인 HTTP 에러에 대한 코드가 미리 정의되어 있습니다:

| 코드 | HTTP 상태 | 설명 |
|------|----------|------|
| `BAD_REQUEST` | 400 | 잘못된 요청 |
| `VALIDATION_ERROR` | 400 | 유효성 검사 실패 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 접근 거부 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `CONFLICT` | 409 | 충돌 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 오류 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 외부 서비스 오류 |
| `SERVICE_UNAVAILABLE` | 503 | 서비스 불가 |

#### 예외 계층

```kotlin
// 400 Bad Request
throw BadRequestException(OrderErrorCode.INVALID_ORDER_STATUS)
throw ValidationException("유효성 검사 실패", fieldErrors)

// 401 Unauthorized
throw UnauthorizedException()

// 403 Forbidden
throw ForbiddenException()

// 404 Not Found
throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)

// 409 Conflict
throw ConflictException(OrderErrorCode.DUPLICATE_ORDER)

// 500 Internal Server Error
throw InternalServerException()

// 502 Bad Gateway
throw ExternalServiceException()

// 503 Service Unavailable
throw ServiceUnavailableException()
```

#### GlobalExceptionHandler

Auto Configuration으로 자동 등록되며 다음을 처리합니다:

1. 애플리케이션 예외 (`BaseException` 하위 클래스)
2. 유효성 검사 예외 (`@Valid`, `@Validated`, `ConstraintViolation`)
3. 요청 매핑 에러 (타입 불일치, 파라미터 누락, 잘못된 JSON)
4. 시스템 레벨 예외 (invariant violation, 예상치 못한 오류)

### 2. API 응답

#### ApiResponse

모든 API 응답을 위한 표준 래퍼:

```kotlin
// 데이터가 있는 성공 응답
ApiResponse.success(data)

// 데이터가 없는 성공 응답
ApiResponse.success<Unit>()

// ErrorCode로 에러 응답
ApiResponse.error(CommonErrorCode.NOT_FOUND)

// 필드 유효성 에러가 있는 에러 응답
ApiResponse.error(errorCode, fieldErrors = listOf(...))
```

#### 응답 예시

```json
{
  "success": true,
  "data": {
    "orderId": "order-123",
    "status": "CREATED"
  },
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ORDER-404",
    "message": "주문을 찾을 수 없습니다"
  }
}
```

### 3. 이벤트 시스템

CNCF CloudEvents v1.0 스펙을 준수하는 이벤트 시스템을 제공합니다.

#### DomainEvent 정의

```kotlin
data class OrderCreatedEvent(
    val orderId: String,
    val userId: String,
    val totalAmount: BigDecimal,
) : AbstractDomainEvent() {
    override fun getEventType(): String = "com.koosco.order.created"
    override fun getAggregateId(): String = orderId
}
```

#### CloudEvent 생성

```kotlin
// 팩토리 메서드 사용
val event = CloudEvent.of(
    source = "urn:koosco:order-service",
    type = "com.koosco.order.created",
    data = OrderData(orderId = "order-123", amount = 10000),
    subject = "order-123"
)

// 빌더 패턴 사용
val event = CloudEventBuilder.builder<OrderData>()
    .source("urn:koosco:order-service")
    .type("com.koosco.order.created")
    .subject("order-123")
    .data(OrderData(orderId = "order-123", amount = 10000))
    .build()

// DSL 스타일 사용
val event = cloudEvent<OrderData> {
    source("urn:koosco:order-service")
    type("com.koosco.order.created")
    subject("order-123")
    data(OrderData(orderId = "order-123", amount = 10000))
}
```

#### 이벤트 발행

```kotlin
@Service
class OrderService(
    private val eventPublisher: EventPublisher
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        val order = orderRepository.save(Order.create(request))

        val event = OrderCreatedEvent(
            orderId = order.id,
            userId = order.userId,
            totalAmount = order.totalAmount
        )

        eventPublisher.publishDomainEvent(event, "urn:koosco:order-service")

        return order
    }
}
```

자세한 이벤트 시스템 사용법은 [CloudEvents 사용 가이드](src/main/kotlin/com/koosco/common/core/event/README.md)를 참조하세요.

### 4. 트랜잭션 관리

프로그래밍 방식의 트랜잭션 관리 유틸리티:

```kotlin
@Service
class OrderService(
    private val transactionRunner: TransactionRunner
) {
    // 기본 트랜잭션
    fun createOrder(order: Order) = transactionRunner.run {
        orderRepository.save(order)
    }

    // 읽기 전용 트랜잭션
    fun getOrder(id: String) = transactionRunner.readOnly {
        orderRepository.findById(id)
    }

    // 새 트랜잭션 (REQUIRES_NEW)
    fun logAudit(audit: Audit) = transactionRunner.runNew {
        auditRepository.save(audit)
    }
}
```

### 5. 커스텀 어노테이션

#### @UseCase

애플리케이션 레이어의 유스케이스 클래스를 표시합니다:

```kotlin
@UseCase
class CreateOrderUseCase(
    private val orderRepository: OrderRepository
) {
    fun execute(command: CreateOrderCommand): Order {
        // 비즈니스 로직
    }
}
```

#### @NotBlankIfPresent

nullable 필드에서 값이 있을 때만 not blank 검사:

```kotlin
data class UpdateUserRequest(
    @field:NotBlankIfPresent
    val name: String?,  // null 허용, 값이 있으면 빈 문자열 불가

    @field:NotBlankIfPresent
    val email: String?
)
```

#### @EnumIfPresent

nullable 필드에서 값이 있을 때만 enum 값 검사:

```kotlin
data class UpdateOrderRequest(
    @field:EnumIfPresent(enumClass = OrderStatus::class)
    val status: String?  // null 허용, 값이 있으면 OrderStatus enum 값이어야 함
)
```

### 6. JSON 유틸리티

사전 구성된 Jackson ObjectMapper:

```kotlin
// 직렬화
val json = JsonUtils.toJson(order)

// 역직렬화
val order = JsonUtils.fromJson<Order>(json)

// 타입 변환
val dto = JsonUtils.convertValue<OrderDto>(order)

// JSON 유효성 검사
val isValid = JsonUtils.isValidJson(json)
```

## 설정

### Auto Configuration

`CommonCoreAutoConfiguration`이 자동으로 다음을 등록합니다:

- `GlobalExceptionHandler` (기본 활성화)
- `ApiResponseAdvice` (기본 비활성화)
- `ObjectMapper` (정의되지 않은 경우)
- `TransactionRunner`

### 설정 프로퍼티

```yaml
common:
  core:
    exception-handler:
      enabled: true   # 기본값: true
    response-advice:
      enabled: false  # 기본값: false (활성화하면 컨트롤러 응답 자동 래핑)
```

## 개발

### 빌드

```bash
./gradlew build
```

### 테스트

```bash
./gradlew test
```

### 코드 포맷팅

```bash
./gradlew spotlessApply
```

### 퍼블리시

```bash
./gradlew publish
```

## 버전 히스토리

| 버전 | 변경 사항 |
|------|----------|
| 0.2.2 | 현재 버전 |
| 0.2.1 | 퍼블리싱 이슈 수정 |
| 0.2.0 | 이벤트 퍼블리셔 추가 |

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.
