# Getting Started

common-core 라이브러리를 프로젝트에 추가하고 사용하는 방법을 설명합니다.

## 의존성 추가

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/koosco-commerce/common-core")
        credentials {
            username = project.findProperty("gpr.user") as String?
                ?: System.getenv("GH_USER")
            password = project.findProperty("gpr.token") as String?
                ?: System.getenv("GH_TOKEN")
        }
    }
}

dependencies {
    implementation("com.koosco:common-core:0.2.2")
}
```

### 환경변수 설정

GitHub Packages에서 패키지를 가져오려면 Git 자격증명이 필요합니다:

```bash
export GH_USER=your-github-username
export GH_TOKEN=your-github-token
```

또는 `~/.gradle/gradle.properties`에 설정:

```properties
gpr.user=your-github-username
gpr.token=your-github-token
```

## Auto-Configuration

common-core는 Spring Boot Auto-Configuration을 통해 다음 기능을 자동 등록합니다:

| 기능 | 기본 상태 | 설명 |
|------|----------|------|
| `GlobalExceptionHandler` | 활성화 | 전역 예외 처리 |
| `ObjectMapper` | 활성화 | JSON 직렬화 설정 |
| `TransactionRunner` | 활성화 | 트랜잭션 관리 |
| `ApiResponseAdvice` | 비활성화 | 자동 응답 래핑 |

별도 설정 없이 의존성만 추가하면 바로 사용할 수 있습니다.

## Quick Start

### 1. 도메인 에러 코드 정의

```kotlin
import com.koosco.common.core.error.ErrorCode
import org.springframework.http.HttpStatus

enum class OrderErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : ErrorCode {
    ORDER_NOT_FOUND("ORDER-404", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("ORDER-400", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),
}
```

### 2. Use Case 작성

```kotlin
import com.koosco.common.core.annotation.UseCase
import com.koosco.common.core.exception.NotFoundException

@UseCase
class GetOrderUseCase(
    private val orderRepository: OrderRepository
) {
    fun execute(orderId: String): Order {
        return orderRepository.findById(orderId)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
    }
}
```

### 3. 컨트롤러 작성

```kotlin
import com.koosco.common.core.response.ApiResponse

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val getOrderUseCase: GetOrderUseCase
) {
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ApiResponse<OrderDto> {
        val order = getOrderUseCase.execute(id)
        return ApiResponse.success(order.toDto())
    }
}
```

### 4. 결과

**성공 응답:**
```json
{
  "success": true,
  "data": {
    "id": "order-123",
    "status": "PENDING",
    "totalAmount": 10000
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**에러 응답 (주문 없음):**
```json
{
  "success": false,
  "error": {
    "code": "ORDER-404",
    "message": "주문을 찾을 수 없습니다."
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 상세 가이드

기능별 상세 사용법은 다음 문서를 참고하세요:

| 문서 | 설명 |
|------|------|
| [Exception Handling](exception-handling.md) | 예외 처리 및 에러 코드 |
| [API Response](api-response.md) | API 응답 포맷 및 래핑 |
| [Event System](event-system.md) | CloudEvents 기반 이벤트 시스템 |
| [Utilities](utilities.md) | 트랜잭션, JSON, 검증 어노테이션 |

## 설정 옵션

```yaml
common:
  core:
    exception-handler:
      enabled: true       # GlobalExceptionHandler (기본: true)
    response-advice:
      enabled: false      # ApiResponseAdvice (기본: false)
```

## 기술 스펙

- **Kotlin**: 1.9.25
- **Spring Boot**: 3.5.8
- **Java**: 21
- **CloudEvents**: v1.0 스펙 준수
