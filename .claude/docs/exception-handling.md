# Exception Handling Guide

common-core의 예외 처리 시스템을 사용하는 방법을 설명합니다.

## 개요

common-core는 MSA 환경에서 일관된 에러 처리를 위한 다음 기능을 제공합니다:

- `ErrorCode` 인터페이스: 도메인별 에러 코드 정의
- `CommonErrorCode`: 공통 HTTP 에러 코드 (400, 401, 403, 404, 409, 500, 502, 503)
- 예외 계층구조: HTTP 상태 코드별 예외 클래스
- `GlobalExceptionHandler`: 전역 예외 처리 (Auto-Configuration)

## 1. 도메인 에러 코드 정의

각 서비스에서 `ErrorCode` 인터페이스를 구현하여 도메인별 에러 코드를 정의합니다.

```kotlin
import com.koosco.common.core.error.ErrorCode
import org.springframework.http.HttpStatus

enum class OrderErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : ErrorCode {
    // 400 Bad Request
    INVALID_ORDER_STATUS("ORDER-400-001", "유효하지 않은 주문 상태입니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_STOCK("ORDER-400-002", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),

    // 404 Not Found
    ORDER_NOT_FOUND("ORDER-404-001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND("ORDER-404-002", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 409 Conflict
    DUPLICATE_ORDER("ORDER-409-001", "이미 존재하는 주문입니다.", HttpStatus.CONFLICT),
}
```

### 에러 코드 네이밍 규칙

```
{SERVICE}-{HTTP_STATUS}[-{SEQUENCE}]

예시:
- ORDER-400        : 주문 서비스 기본 400 에러
- ORDER-400-001    : 주문 서비스 400 에러 중 첫 번째 세부 에러
- USER-404-001     : 사용자 서비스 404 에러 중 첫 번째 세부 에러
```

## 2. 예외 발생

common-core는 HTTP 상태 코드별로 예외 클래스를 제공합니다.

### 예외 계층구조

```
BaseException (기본 클래스)
├── BadRequestException (400)
│   └── ValidationException (400 - 유효성 검사)
├── UnauthorizedException (401)
├── ForbiddenException (403)
├── NotFoundException (404)
├── ConflictException (409)
├── InternalServerException (500)
├── ExternalServiceException (502)
└── ServiceUnavailableException (503)
```

### 기본 사용법

```kotlin
import com.koosco.common.core.exception.*

// 404 Not Found
throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)

// 400 Bad Request
throw BadRequestException(OrderErrorCode.INVALID_ORDER_STATUS)

// 409 Conflict
throw ConflictException(OrderErrorCode.DUPLICATE_ORDER)

// 커스텀 메시지 추가
throw NotFoundException(
    errorCode = OrderErrorCode.ORDER_NOT_FOUND,
    message = "주문 ID: $orderId 를 찾을 수 없습니다."
)
```

### 공통 에러 코드 사용

도메인 에러 코드 없이 공통 에러 코드를 사용할 수도 있습니다.

```kotlin
import com.koosco.common.core.error.CommonErrorCode

// 기본 공통 에러
throw NotFoundException()  // CommonErrorCode.NOT_FOUND 사용

// 공통 에러 코드 명시
throw BadRequestException(CommonErrorCode.INVALID_INPUT)
throw UnauthorizedException(CommonErrorCode.EXPIRED_TOKEN)
```

### 필드 에러와 함께 사용

유효성 검사 실패 시 필드별 에러 정보를 포함할 수 있습니다.

```kotlin
import com.koosco.common.core.error.ApiError

val fieldErrors = listOf(
    ApiError.FieldError("email", "invalid-email", "이메일 형식이 올바르지 않습니다."),
    ApiError.FieldError("name", "", "이름은 필수입니다.")
)

throw ValidationException(
    message = "입력값 검증에 실패했습니다.",
    fieldErrors = fieldErrors
)
```

## 3. 공통 에러 코드 목록

`CommonErrorCode`에서 제공하는 에러 코드:

| 코드 | HTTP Status | 설명 |
|------|-------------|------|
| `BAD_REQUEST` | 400 | 잘못된 요청 |
| `INVALID_INPUT` | 400 | 잘못된 입력값 |
| `INVALID_TYPE` | 400 | 잘못된 형식의 값 |
| `MISSING_PARAMETER` | 400 | 필수 파라미터 누락 |
| `VALIDATION_ERROR` | 400 | 유효성 검사 실패 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |
| `FORBIDDEN` | 403 | 접근 거부 |
| `ACCESS_DENIED` | 403 | 리소스 접근 불가 |
| `NOT_FOUND` | 404 | 대상을 찾을 수 없음 |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없음 |
| `METHOD_NOT_ALLOWED` | 405 | 허용되지 않은 HTTP 메서드 |
| `CONFLICT` | 409 | 요청 충돌 |
| `DUPLICATE_RESOURCE` | 409 | 이미 존재하는 리소스 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |
| `UNEXPECTED_ERROR` | 500 | 예상치 못한 오류 |
| `BAD_GATEWAY` | 502 | 외부 서비스 연결 문제 |
| `EXTERNAL_SERVICE_ERROR` | 502 | 외부 서비스 오류 |
| `SERVICE_UNAVAILABLE` | 503 | 서비스 이용 불가 |

## 4. GlobalExceptionHandler

`GlobalExceptionHandler`는 Auto-Configuration으로 자동 등록되며, 다음 예외들을 처리합니다.

### 처리되는 예외 유형

#### 1) 애플리케이션 예외 (BaseException)
비즈니스/도메인 레벨의 예외를 처리합니다.

```json
{
  "success": false,
  "error": {
    "code": "ORDER-404-001",
    "message": "주문을 찾을 수 없습니다.",
    "details": "주문 ID: order-123 를 찾을 수 없습니다."
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### 2) 유효성 검사 예외
`@Valid`, `@Validated` 검증 실패 시 자동 처리됩니다.

```json
{
  "success": false,
  "error": {
    "code": "COMMON-400-004",
    "message": "유효성 검사에 실패했습니다.",
    "fieldErrors": [
      {
        "field": "email",
        "value": "invalid-email",
        "reason": "이메일 형식이 올바르지 않습니다."
      }
    ]
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### 3) 요청 매핑 에러
타입 불일치, 누락된 파라미터, 잘못된 JSON 등을 처리합니다.

#### 4) 시스템 레벨 예외
예측 불가능한 오류는 로깅 후 500 에러로 응답합니다.

### 설정

기본적으로 활성화되어 있습니다. 비활성화하려면:

```yaml
common:
  core:
    exception-handler:
      enabled: false
```

## 5. 실전 예제

### 서비스 레이어에서 예외 발생

```kotlin
@UseCase
class GetOrderUseCase(
    private val orderRepository: OrderRepository
) {
    fun execute(orderId: String): Order {
        return orderRepository.findById(orderId)
            ?: throw NotFoundException(
                errorCode = OrderErrorCode.ORDER_NOT_FOUND,
                message = "주문 ID: $orderId 를 찾을 수 없습니다."
            )
    }
}

@UseCase
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService
) {
    fun execute(command: CreateOrderCommand): Order {
        // 재고 확인
        if (!inventoryService.hasStock(command.productId, command.quantity)) {
            throw BadRequestException(OrderErrorCode.INSUFFICIENT_STOCK)
        }

        // 중복 확인
        if (orderRepository.existsByRequestId(command.requestId)) {
            throw ConflictException(OrderErrorCode.DUPLICATE_ORDER)
        }

        return orderRepository.save(Order.create(command))
    }
}
```

### 컨트롤러에서 예외 처리 (수동)

일반적으로 `GlobalExceptionHandler`가 자동 처리하므로 컨트롤러에서 별도 처리가 필요하지 않습니다.
특별한 경우에만 수동 처리합니다.

```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val getOrderUseCase: GetOrderUseCase
) {
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ApiResponse<OrderDto> {
        // GlobalExceptionHandler가 예외를 자동 처리
        val order = getOrderUseCase.execute(id)
        return ApiResponse.success(order.toDto())
    }
}
```

## 6. InvariantViolationException

시스템 불변식 위반 (절대 발생하면 안 되는 상태)을 표현합니다.
버그 또는 동시성 문제를 나타내며, 반드시 조사가 필요합니다.

```kotlin
import com.koosco.common.core.exception.InvariantViolationException

fun processPayment(order: Order) {
    // 결제 완료된 주문은 다시 결제할 수 없음
    if (order.isPaid()) {
        throw InvariantViolationException(
            "Order ${order.id} is already paid. This should never happen."
        )
    }
    // ...
}
```

`GlobalExceptionHandler`에서 ERROR 레벨로 로깅되며, 500 에러로 응답합니다.
