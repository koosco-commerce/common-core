# Utilities Guide

common-core에서 제공하는 유틸리티 기능을 사용하는 방법을 설명합니다.

## 개요

common-core는 다음 유틸리티를 제공합니다:

- `TransactionRunner`: 프로그래매틱 트랜잭션 관리
- `JsonUtils`: JSON 직렬화/역직렬화
- 커스텀 검증 어노테이션: `@UseCase`, `@NotBlankIfPresent`, `@EnumIfPresent`

## 1. TransactionRunner

### 개요

`TransactionRunner`는 프로그래매틱하게 트랜잭션을 관리할 수 있는 인터페이스입니다.
Auto-Configuration으로 자동 등록됩니다.

### 기본 사용법

```kotlin
import com.koosco.common.core.transaction.TransactionRunner

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val transactionRunner: TransactionRunner
) {
    // 기본 트랜잭션 (REQUIRED)
    fun createOrder(command: CreateOrderCommand): Order {
        return transactionRunner.run {
            val order = Order.create(command)
            orderRepository.save(order)
        }
    }

    // 읽기 전용 트랜잭션 (성능 최적화)
    fun getOrder(id: String): Order? {
        return transactionRunner.readOnly {
            orderRepository.findById(id)
        }
    }

    // 새 트랜잭션 (REQUIRES_NEW)
    fun logAudit(audit: Audit) {
        transactionRunner.runNew {
            auditRepository.save(audit)
        }
    }
}
```

### 트랜잭션 종류

| 메서드 | Propagation | 설명 |
|--------|-------------|------|
| `run { }` | REQUIRED | 기존 트랜잭션 참여 또는 새로 생성 |
| `readOnly { }` | REQUIRED + readOnly | 읽기 전용 최적화 |
| `runNew { }` | REQUIRES_NEW | 항상 새 트랜잭션 생성 |

### 활용 예시

```kotlin
@UseCase
class ProcessPaymentUseCase(
    private val transactionRunner: TransactionRunner,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val auditService: AuditService
) {
    fun execute(command: ProcessPaymentCommand): Payment {
        return transactionRunner.run {
            // 1. 결제 처리
            val payment = Payment.create(command)
            paymentRepository.save(payment)

            // 2. 주문 상태 업데이트
            val order = orderRepository.findById(command.orderId)
                ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
            order.markAsPaid()
            orderRepository.save(order)

            // 3. 감사 로그 (별도 트랜잭션)
            transactionRunner.runNew {
                auditService.log(AuditEvent.PAYMENT_PROCESSED, payment.id)
            }

            payment
        }
    }
}
```

## 2. JsonUtils

### 개요

`JsonUtils`는 Jackson ObjectMapper 기반의 JSON 유틸리티입니다.
다음 설정이 기본 적용됩니다:

- Kotlin 모듈 지원
- Java 8 Time 지원 (ISO-8601 형식)
- 알 수 없는 속성 무시 (`FAIL_ON_UNKNOWN_PROPERTIES = false`)

### 직렬화

```kotlin
import com.koosco.common.core.util.JsonUtils

data class Order(val id: String, val amount: BigDecimal)

val order = Order("order-123", BigDecimal("10000"))

// 기본 직렬화 (실패 시 null)
val json: String? = JsonUtils.toJson(order)

// Pretty 출력
val prettyJson: String? = JsonUtils.toPrettyJson(order)

// 실패 시 예외 발생
val json: String = JsonUtils.toJsonOrThrow(order)
```

### 역직렬화

```kotlin
val json = """{"id":"order-123","amount":10000}"""

// reified 타입 사용 (권장)
val order: Order? = JsonUtils.fromJson<Order>(json)

// Class 타입 사용
val order: Order? = JsonUtils.fromJson(json, Order::class.java)

// TypeReference 사용 (제네릭 타입)
val orders: List<Order>? = JsonUtils.fromJson(
    json,
    object : TypeReference<List<Order>>() {}
)

// 실패 시 예외 발생
val order: Order = JsonUtils.fromJsonOrThrow<Order>(json)
```

### 타입 변환

```kotlin
// Map → DTO 변환
val map = mapOf("id" to "order-123", "amount" to 10000)
val order: Order? = JsonUtils.convertValue<Order>(map)

// TypeReference 사용
val orders: List<Order>? = JsonUtils.convertValue(
    listOfMaps,
    object : TypeReference<List<Order>>() {}
)
```

### JSON 검증

```kotlin
val isValid: Boolean = JsonUtils.isValidJson("""{"key": "value"}""")  // true
val isInvalid: Boolean = JsonUtils.isValidJson("not json")            // false
```

### JsonNode 파싱

```kotlin
val json = """{"order": {"id": "123", "items": [1, 2, 3]}}"""
val node: JsonNode? = JsonUtils.parseJson(json)

node?.let {
    val orderId = it.path("order").path("id").asText()
    val items = it.path("order").path("items")
}
```

### ObjectMapper 직접 사용

```kotlin
// 공유 ObjectMapper 인스턴스
val objectMapper: ObjectMapper = JsonUtils.objectMapper

// 커스텀 설정이 필요한 경우
val customMapper = objectMapper.copy().apply {
    enable(SerializationFeature.INDENT_OUTPUT)
}
```

## 3. 커스텀 검증 어노테이션

### @UseCase

애플리케이션 레이어의 Use Case 클래스를 마킹합니다. Spring `@Component`를 포함합니다.

```kotlin
import com.koosco.common.core.annotation.UseCase

@UseCase
class CreateOrderUseCase(
    private val orderRepository: OrderRepository
) {
    fun execute(command: CreateOrderCommand): Order {
        // 비즈니스 로직
    }
}
```

### @NotBlankIfPresent

nullable 필드가 값이 있을 때만 blank가 아니어야 함을 검증합니다.

```kotlin
import com.koosco.common.core.annotation.NotBlankIfPresent

data class UpdateUserRequest(
    @field:NotBlankIfPresent
    val name: String?,        // null 허용, 값이 있으면 not blank

    @field:NotBlankIfPresent
    val email: String?,       // null 허용, 값이 있으면 not blank

    val age: Int?             // 검증 없음
)
```

검증 동작:
| 값 | 결과 |
|----|------|
| `null` | ✅ 유효 |
| `"John"` | ✅ 유효 |
| `""` | ❌ 무효 |
| `"   "` | ❌ 무효 |

### @EnumIfPresent

nullable String 필드가 지정된 enum 값과 일치하는지 검증합니다.

```kotlin
import com.koosco.common.core.annotation.EnumIfPresent

enum class OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}

data class UpdateOrderRequest(
    @field:EnumIfPresent(enumClass = OrderStatus::class)
    val status: String?,      // null 허용, 값이 있으면 enum과 일치해야 함

    val note: String?
)
```

검증 동작:
| 값 | 결과 |
|----|------|
| `null` | ✅ 유효 |
| `"PENDING"` | ✅ 유효 |
| `"CONFIRMED"` | ✅ 유효 |
| `"pending"` | ❌ 무효 (대소문자 구분) |
| `"UNKNOWN"` | ❌ 무효 |

### 검증 어노테이션 조합

```kotlin
data class UpdateProductRequest(
    @field:NotBlankIfPresent
    val name: String?,

    @field:EnumIfPresent(enumClass = ProductStatus::class)
    val status: String?,

    @field:Min(0)
    val price: BigDecimal?,

    @field:Size(max = 1000)
    @field:NotBlankIfPresent
    val description: String?
)
```

## 4. Auto-Configuration 설정

### 기본 설정

```yaml
common:
  core:
    exception-handler:
      enabled: true       # GlobalExceptionHandler (기본: true)
    response-advice:
      enabled: false      # ApiResponseAdvice (기본: false)
```

### 자동 등록되는 빈

| 빈 | 조건 | 기본값 |
|----|------|--------|
| `GlobalExceptionHandler` | Servlet 웹앱 | 활성화 |
| `ApiResponseAdvice` | Servlet 웹앱 | 비활성화 |
| `ObjectMapper` | 미정의 시 | 활성화 |
| `TransactionRunner` | 미정의 시 | 활성화 |

### 커스텀 구현 제공

Auto-Configuration은 `@ConditionalOnMissingBean`을 사용하므로,
커스텀 구현을 제공하면 자동 등록이 비활성화됩니다.

```kotlin
@Configuration
class CustomConfig {
    // 커스텀 TransactionRunner
    @Bean
    fun transactionRunner(): TransactionRunner {
        return CustomTransactionRunner()
    }

    // 커스텀 ObjectMapper
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper().apply {
            // 커스텀 설정
        }
    }
}
```
