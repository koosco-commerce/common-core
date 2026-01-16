# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Module Overview

`common-core` is a shared library providing foundational functionality for all microservices in the commerce platform. It includes error handling, API response standardization, domain event infrastructure, and common utilities.

**Version**: 0.2.1
**Java**: 21
**Spring Boot**: 3.5.8

## Package Structure

```
com.koosco.common.core/
├── annotation/     # Custom annotations (@UseCase, validation annotations)
├── config/         # Auto-configuration for Spring Boot
├── error/          # Error codes and API error structure
├── event/          # Domain event and CloudEvent infrastructure
├── exception/      # Exception hierarchy and global handler
├── response/       # API response wrapper
├── transaction/    # Transaction management utilities
└── util/           # JSON utilities
```

## Key Components

### Error Handling (`error/`, `exception/`)

**ErrorCode Interface**: Services extend this to define domain-specific error codes.
```kotlin
enum class OrderErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : ErrorCode {
    ORDER_NOT_FOUND("ORDER-404", "Order not found", HttpStatus.NOT_FOUND)
}
```

**CommonErrorCode**: Pre-defined error codes for common HTTP errors (400, 401, 403, 404, 409, 500, 502, 503).

**Exception Hierarchy**:
- `BaseException` - Base class with `ErrorCode` and `toApiError()` conversion
- `BadRequestException`, `ValidationException` - 400 errors
- `UnauthorizedException` - 401 errors
- `ForbiddenException` - 403 errors
- `NotFoundException` - 404 errors
- `ConflictException` - 409 errors
- `InternalServerException` - 500 errors
- `ExternalServiceException` - 502 errors
- `ServiceUnavailableException` - 503 errors
- `InvariantViolationException` - System-level invariant violations (bugs)

**GlobalExceptionHandler**: Auto-configured `@RestControllerAdvice` that handles:
1. Application exceptions (`BaseException` subclasses)
2. Validation exceptions (`@Valid`, `@Validated`, `ConstraintViolation`)
3. Request mapping errors (type mismatch, missing params, invalid JSON)
4. System-level exceptions (invariant violations, unexpected errors)

### API Response (`response/`)

**ApiResponse**: Standard wrapper for all API responses.
```kotlin
// Success with data
ApiResponse.success(data)

// Success without data
ApiResponse.success<Unit>()

// Error from ErrorCode
ApiResponse.error(CommonErrorCode.NOT_FOUND)

// Error with field validation errors
ApiResponse.error(errorCode, fieldErrors = listOf(...))
```

**ApiResponseAdvice**: Optional automatic wrapping of controller responses. Disabled by default.
- Enable via: `common.core.response-advice.enabled=true`
- Exclude with `@ApiResponseIgnore` annotation
- Automatically excludes actuator and Swagger endpoints

### Event Infrastructure (`event/`)

**DomainEvent Interface**: Base interface for domain events.
```kotlin
data class OrderCreatedEvent(
    val orderId: String,
    val userId: String,
    override val eventId: String = CloudEvent.generateId(),
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {
    override fun getEventType(): String = "com.koosco.order.created"
    override fun getAggregateId(): String = orderId
}
```

**CloudEvent**: CNCF CloudEvents v1.0 specification implementation for Kafka messaging.
- Required fields: `id`, `source`, `specversion`, `type`
- Optional fields: `datacontenttype`, `dataschema`, `subject`, `time`, `data`

**EventPublisher Interface**: Services implement for Kafka publishing.
```kotlin
interface EventPublisher {
    fun publish(event: CloudEvent<*>)
    fun publishDomainEvent(event: DomainEvent, source: String, ...)
    fun publishBatch(events: List<CloudEvent<*>>, ...)
}
```

**EventHandler Interface**: For consuming domain events.
```kotlin
interface EventHandler<T : DomainEvent> {
    fun handle(event: T)
    fun getOrder(): Int = 0
}
```

**EventValidator**: Validates CloudEvents and DomainEvents against spec compliance.

### Annotations (`annotation/`)

- `@UseCase` - Marks application layer use case classes (Spring `@Component`)
- `@NotBlankIfPresent` - Validation: not blank only if value is present (nullable fields)
- `@EnumIfPresent` - Validation: must match enum value only if present

### Utilities

**JsonUtils**: Pre-configured Jackson ObjectMapper with:
- Kotlin module support
- Java 8 time support (ISO-8601 format)
- Lenient deserialization (ignores unknown properties)

```kotlin
JsonUtils.toJson(obj)           // Serialize to JSON
JsonUtils.fromJson<T>(json)     // Deserialize from JSON
JsonUtils.convertValue<T>(obj)  // Convert between types
JsonUtils.isValidJson(json)     // Validate JSON format
```

**TransactionRunner**: Programmatic transaction management.
```kotlin
transactionRunner.run { /* runs in transaction */ }
transactionRunner.readOnly { /* read-only transaction */ }
transactionRunner.runNew { /* new transaction (REQUIRES_NEW) */ }
```

## Auto-Configuration

`CommonCoreAutoConfiguration` automatically registers:
- `GlobalExceptionHandler` (enabled by default)
- `ApiResponseAdvice` (disabled by default)
- `ObjectMapper` (if not already defined)
- `TransactionRunner`

### Configuration Properties

```yaml
common:
  core:
    exception-handler:
      enabled: true   # Default: true
    response-advice:
      enabled: false  # Default: false (enable for auto-wrapping)
```

## Consuming This Module

### Dependency Setup

Add to `build.gradle.kts`:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/koosco-commerce/common-core")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GH_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GH_TOKEN")
        }
    }
}

dependencies {
    implementation("com.koosco:common-core:0.2.1")
}
```

### GitHub Packages Authentication

Set in `~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.token=your-github-token
```

Or environment variables: `GH_ACTOR`, `GH_TOKEN`

## Development Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Code formatting
./gradlew spotlessApply

# Publish to GitHub Packages
./gradlew publish
```

## Publishing New Versions

1. Update version in `build.gradle.kts`
2. Run `./gradlew spotlessApply`
3. Run `./gradlew test`
4. Commit and push
5. GitHub Actions will publish automatically on push to main
