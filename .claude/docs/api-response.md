# API Response Guide

common-core의 API 응답 처리 시스템을 사용하는 방법을 설명합니다.

## 개요

common-core는 MSA 환경에서 일관된 API 응답 포맷을 제공합니다.

- `ApiResponse<T>`: 표준 응답 래퍼
- `ApiError`: 에러 응답 모델
- `ApiResponseAdvice`: 자동 응답 래핑 (선택적)

## 1. ApiResponse 구조

### 응답 포맷

모든 API 응답은 다음 형식을 따릅니다:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 에러 응답 포맷

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ORDER-404-001",
    "message": "주문을 찾을 수 없습니다.",
    "details": "주문 ID: order-123 를 찾을 수 없습니다.",
    "fieldErrors": [
      {
        "field": "email",
        "value": "invalid",
        "reason": "이메일 형식이 올바르지 않습니다."
      }
    ]
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 2. 수동 래핑 (권장)

컨트롤러에서 `ApiResponse`를 직접 반환합니다.

### 성공 응답

```kotlin
import com.koosco.common.core.response.ApiResponse

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService
) {
    // 데이터와 함께 성공 응답
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ApiResponse<OrderDto> {
        val order = orderService.getOrder(id)
        return ApiResponse.success(order.toDto())
    }

    // 데이터 없이 성공 응답
    @DeleteMapping("/{id}")
    fun deleteOrder(@PathVariable id: String): ApiResponse<Unit> {
        orderService.deleteOrder(id)
        return ApiResponse.success()
    }

    // 생성 후 응답
    @PostMapping
    fun createOrder(@RequestBody @Valid request: CreateOrderRequest): ApiResponse<OrderDto> {
        val order = orderService.createOrder(request)
        return ApiResponse.success(order.toDto())
    }
}
```

### 에러 응답 (수동)

일반적으로 예외를 throw하면 `GlobalExceptionHandler`가 자동 처리합니다.
특수한 경우에만 수동으로 에러 응답을 생성합니다.

```kotlin
import com.koosco.common.core.response.ApiResponse
import com.koosco.common.core.error.ApiError

// ErrorCode로 에러 응답
return ApiResponse.error<OrderDto>(CommonErrorCode.NOT_FOUND)

// 메시지와 함께 에러 응답
return ApiResponse.error<OrderDto>(
    errorCode = OrderErrorCode.ORDER_NOT_FOUND,
    message = "주문 ID: $id 를 찾을 수 없습니다."
)

// 필드 에러와 함께
val fieldErrors = listOf(
    ApiError.FieldError("email", "invalid", "이메일 형식이 올바르지 않습니다.")
)
return ApiResponse.error<OrderDto>(
    errorCode = CommonErrorCode.VALIDATION_ERROR,
    fieldErrors = fieldErrors
)
```

## 3. 자동 래핑 (선택적)

`ApiResponseAdvice`를 활성화하면 컨트롤러 반환값이 자동으로 `ApiResponse`로 래핑됩니다.

### 활성화

```yaml
# application.yml
common:
  core:
    response-advice:
      enabled: true  # 기본값: false
```

### 사용 예시

```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService
) {
    // 반환값이 자동으로 ApiResponse.success(order)로 래핑됨
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): OrderDto {
        return orderService.getOrder(id).toDto()
    }

    // List도 자동 래핑
    @GetMapping
    fun getOrders(): List<OrderDto> {
        return orderService.getOrders().map { it.toDto() }
    }
}
```

### 자동 제외 대상

다음은 자동으로 래핑에서 제외됩니다:

- 이미 `ApiResponse`인 경우
- Spring Boot Actuator 엔드포인트
- Swagger/OpenAPI 엔드포인트

### 수동 제외

특정 컨트롤러나 메서드를 래핑에서 제외하려면 `@ApiResponseIgnore`를 사용합니다.

```kotlin
import com.koosco.common.core.response.ApiResponseIgnore

// 클래스 레벨 제외
@ApiResponseIgnore
@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")
}

// 메서드 레벨 제외
@RestController
class OrderController {
    @ApiResponseIgnore
    @GetMapping("/raw")
    fun rawResponse(): String = "raw response"
}
```

## 4. ApiError 구조

### FieldError

필드별 유효성 검사 에러 정보:

```kotlin
data class FieldError(
    val field: String,    // 필드명
    val value: Any?,      // 입력값
    val reason: String    // 에러 사유
)
```

### ApiError 생성

```kotlin
import com.koosco.common.core.error.ApiError

// ErrorCode에서 생성
val error = ApiError.of(OrderErrorCode.ORDER_NOT_FOUND)

// 상세 메시지 포함
val error = ApiError.of(
    errorCode = OrderErrorCode.ORDER_NOT_FOUND,
    details = "주문 ID: order-123"
)

// 필드 에러 포함
val error = ApiError.of(
    errorCode = CommonErrorCode.VALIDATION_ERROR,
    details = "입력값 검증 실패",
    fieldErrors = listOf(
        ApiError.FieldError("email", "invalid", "이메일 형식 오류"),
        ApiError.FieldError("name", "", "이름은 필수입니다")
    )
)
```

## 5. 권장 패턴

### 예외 기반 에러 처리 (권장)

예외를 throw하면 `GlobalExceptionHandler`가 자동으로 `ApiResponse.error()`를 생성합니다.

```kotlin
@RestController
class OrderController(
    private val orderService: OrderService
) {
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ApiResponse<OrderDto> {
        // NotFoundException이 발생하면 GlobalExceptionHandler가 처리
        val order = orderService.getOrder(id)
        return ApiResponse.success(order.toDto())
    }
}

@UseCase
class GetOrderUseCase(
    private val orderRepository: OrderRepository
) {
    fun execute(id: String): Order {
        return orderRepository.findById(id)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
    }
}
```

### 페이지네이션 응답

```kotlin
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

@GetMapping
fun getOrders(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int
): ApiResponse<PagedResponse<OrderDto>> {
    val orders = orderService.getOrders(PageRequest.of(page, size))
    return ApiResponse.success(
        PagedResponse(
            content = orders.content.map { it.toDto() },
            page = orders.number,
            size = orders.size,
            totalElements = orders.totalElements,
            totalPages = orders.totalPages
        )
    )
}
```

## 6. 설정 요약

```yaml
common:
  core:
    response-advice:
      enabled: false  # 기본값: false (필요시 true로 변경)
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `response-advice.enabled` | `false` | 자동 응답 래핑 활성화 여부 |
