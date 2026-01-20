# Event System Guide

common-core의 이벤트 시스템을 사용하는 방법을 설명합니다.

> 상세 가이드: [CloudEvents 기반 이벤트 시스템 사용 가이드](../../src/main/kotlin/com/koosco/common/core/event/README.md)

## 개요

common-core는 CNCF CloudEvents v1.0 스펙을 준수하는 이벤트 시스템을 제공합니다.

- `CloudEvent<T>`: CloudEvents 스펙 구현
- `DomainEvent`: 도메인 이벤트 인터페이스
- `EventPublisher`: 이벤트 발행 인터페이스
- `EventHandler<T>`: 이벤트 핸들러 인터페이스
- `EventValidator`: 이벤트 검증 유틸리티

## 1. 도메인 이벤트 정의

### AbstractDomainEvent 사용 (권장)

```kotlin
import com.koosco.common.core.event.AbstractDomainEvent
import com.koosco.common.core.event.PublishableDomainEvent
import java.math.BigDecimal

data class OrderCreatedEvent(
    val orderId: String,
    val userId: String,
    val totalAmount: BigDecimal,
    val items: List<OrderItem>
) : AbstractDomainEvent(), PublishableDomainEvent {

    override fun getEventType(): String = "com.koosco.order.created"
    override fun getAggregateId(): String = orderId
}
```

### DomainEvent 직접 구현

```kotlin
import com.koosco.common.core.event.CloudEvent
import com.koosco.common.core.event.DomainEvent
import java.time.Instant

data class PaymentCompletedEvent(
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    override val eventId: String = CloudEvent.generateId(),
    override val occurredAt: Instant = Instant.now()
) : DomainEvent {

    override fun getEventType(): String = "com.koosco.payment.completed"
    override fun getAggregateId(): String = paymentId
}
```

### PublishableDomainEvent

외부 시스템에 발행해야 하는 이벤트를 명시적으로 표시합니다.

```kotlin
// 발행 가능 이벤트만 필터링
if (event is PublishableDomainEvent) {
    eventPublisher.publishDomainEvent(event, source)
}
```

## 2. CloudEvent 직접 생성

### 팩토리 메서드

```kotlin
import com.koosco.common.core.event.CloudEvent

val event = CloudEvent.of(
    source = "urn:koosco:order-service",
    type = "com.koosco.order.created",
    data = OrderData(orderId = "order-123", amount = 10000),
    subject = "order-123"
)
```

### 빌더 패턴

```kotlin
import com.koosco.common.core.event.CloudEventBuilder

val event = CloudEventBuilder.builder<OrderData>()
    .source("urn:koosco:order-service")
    .type("com.koosco.order.created")
    .subject("order-123")
    .data(OrderData(orderId = "order-123", amount = 10000))
    .dataSchema("https://schemas.koosco.com/order/v1")
    .build()
```

### DSL 스타일

```kotlin
import com.koosco.common.core.event.cloudEvent

val event = cloudEvent<OrderData> {
    source("urn:koosco:order-service")
    type("com.koosco.order.created")
    subject("order-123")
    data(OrderData(orderId = "order-123", amount = 10000))
}
```

## 3. 이벤트 발행

### EventPublisher 구현 (Kafka 예제)

```kotlin
import com.koosco.common.core.event.AbstractEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaEventPublisher(
    objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) : AbstractEventPublisher(objectMapper) {

    override fun publishRaw(topic: String, key: String?, payload: String) {
        kafkaTemplate.send(topic, key, payload).get()
    }

    override fun resolveTopic(event: CloudEvent<*>): String {
        // 이벤트 타입에서 토픽 이름 추출
        // com.koosco.order.created -> order-created
        return event.type
            .substringAfter("com.koosco.")
            .replace(".", "-")
    }

    override fun resolveKey(event: CloudEvent<*>): String? {
        // subject를 파티션 키로 사용
        return event.subject
    }
}
```

### 서비스에서 이벤트 발행

```kotlin
@UseCase
class CreateOrderUseCase(
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher
) {
    @Transactional
    fun execute(command: CreateOrderCommand): Order {
        val order = Order.create(command)
        orderRepository.save(order)

        val event = OrderCreatedEvent(
            orderId = order.id,
            userId = order.userId,
            totalAmount = order.totalAmount,
            items = order.items
        )

        eventPublisher.publishDomainEvent(
            event = event,
            source = "urn:koosco:order-service"
        )

        return order
    }
}
```

### 배치 발행

```kotlin
val events = orders.map { order ->
    OrderCreatedEvent(
        orderId = order.id,
        userId = order.userId,
        totalAmount = order.totalAmount,
        items = order.items
    ).toCloudEvent(source = "urn:koosco:order-service")
}

eventPublisher.publishBatch(events)
```

## 4. 이벤트 처리

### EventHandler 구현

```kotlin
import com.koosco.common.core.event.EventHandler
import org.springframework.stereotype.Component

@Component
class OrderCreatedEventHandler(
    private val inventoryService: InventoryService
) : EventHandler<OrderCreatedEvent> {

    override fun handle(event: OrderCreatedEvent) {
        event.items.forEach { item ->
            inventoryService.decreaseStock(item.productId, item.quantity)
        }
    }

    override fun canHandle(eventType: String): Boolean {
        return eventType == "com.koosco.order.created"
    }

    override fun getOrder(): Int = 0  // 핸들러 실행 순서
}
```

### CloudEventHandler 구현

CloudEvent 메타데이터가 필요한 경우:

```kotlin
import com.koosco.common.core.event.CloudEventHandler

@Component
class OrderEventCloudHandler : CloudEventHandler<OrderCreatedEvent> {

    override fun handle(event: CloudEvent<OrderCreatedEvent>) {
        // CloudEvent 메타데이터 활용
        logger.info("Event ID: ${event.id}, Source: ${event.source}, Time: ${event.time}")

        event.data?.let { orderEvent ->
            processOrder(orderEvent)
        }
    }

    override fun canHandle(eventType: String): Boolean {
        return eventType == "com.koosco.order.created"
    }
}
```

### Kafka Consumer 예제

```kotlin
@Component
class OrderEventConsumer(
    private val handlers: List<EventHandler<*>>,
    private val objectMapper: ObjectMapper
) {
    @KafkaListener(topics = ["order-created"])
    fun consume(message: String) {
        val cloudEvent = EventUtils.fromJson<OrderCreatedEvent>(message)
            ?: return

        handlers
            .filter { it.canHandle(cloudEvent.type) }
            .sortedBy { it.getOrder() }
            .forEach { handler ->
                @Suppress("UNCHECKED_CAST")
                (handler as EventHandler<Any>).handle(cloudEvent.data!!)
            }
    }
}
```

## 5. 이벤트 검증

### CloudEvent 검증

```kotlin
import com.koosco.common.core.event.EventValidator

val event = CloudEvent.of(...)
val result = EventValidator.validate(event)

if (result.isValid) {
    eventPublisher.publish(event)
} else {
    logger.error("Validation errors: ${result.errors}")
}

// 예외를 던지는 방식
result.throwIfInvalid()
```

### DomainEvent 검증

```kotlin
val domainEvent = OrderCreatedEvent(...)
val result = EventValidator.validate(domainEvent)
```

### 검증 후 직렬화

```kotlin
import com.koosco.common.core.event.EventUtils

// 검증 후 직렬화
val json = EventUtils.validateAndSerialize(event)

// 역직렬화 후 검증
val event = EventUtils.deserializeAndValidate(json, OrderData::class.java)
```

## 6. 직렬화/역직렬화

```kotlin
import com.koosco.common.core.event.EventUtils

// CloudEvent → JSON
val json = EventUtils.toJson(event)

// JSON → CloudEvent
val event = EventUtils.fromJson<OrderData>(json)

// CloudEvent → Map
val map = EventUtils.toMap(event)

// Map → CloudEvent
val event = EventUtils.fromMap(map, OrderData::class.java)
```

## 7. 베스트 프랙티스

### 이벤트 타입 네이밍

```kotlin
// 권장: 역도메인 표기법
"com.koosco.order.created"
"com.koosco.payment.completed"
"com.koosco.inventory.stock-decreased"

// 지양
"OrderCreated"
"order-created"
"CREATE_ORDER"
```

### Source URI 형식

```kotlin
// URN 형식 (권장)
"urn:koosco:order-service"
"urn:koosco:payment-service"

// HTTP URL 형식
"https://api.koosco.com/orders"
```

### 이벤트 버전 관리

```kotlin
data class OrderCreatedEventV2(
    val orderId: String,
    val userId: String,
    val totalAmount: BigDecimal,
    val shippingAddress: Address  // 새 필드
) : AbstractDomainEvent() {

    override fun getEventType(): String = "com.koosco.order.created"
    override fun getEventVersion(): String = "2.0"
    override fun getAggregateId(): String = orderId
}
```

### 멱등성 보장

```kotlin
@Component
class PaymentEventHandler(
    private val processedEventRepository: ProcessedEventRepository
) : EventHandler<PaymentRequestedEvent> {

    override fun handle(event: PaymentRequestedEvent) {
        // 이미 처리된 이벤트인지 확인
        if (processedEventRepository.existsById(event.eventId)) {
            logger.info("Event ${event.eventId} already processed, skipping")
            return
        }

        // 이벤트 처리
        processPayment(event)

        // 처리 완료 기록
        processedEventRepository.save(ProcessedEvent(event.eventId))
    }
}
```

## 참고 자료

- [CloudEvents 공식 스펙](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md)
- [CNCF CloudEvents](https://cloudevents.io/)
- [상세 가이드](../../src/main/kotlin/com/koosco/common/core/event/README.md)
