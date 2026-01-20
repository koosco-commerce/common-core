# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Module Overview

`common-core` is a shared library providing foundational functionality for all microservices in the commerce platform. It includes error handling, API response standardization, domain event infrastructure, OpenAPI configuration, and common utilities.

**Version**: 0.2.2
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
├── openapi/        # OpenAPI/Swagger auto-configuration
├── response/       # API response wrapper
├── transaction/    # Transaction management utilities
└── util/           # JSON utilities
```

## Key Components

### Error Handling (`error/`, `exception/`)

- `ErrorCode` interface for domain-specific error codes
- `CommonErrorCode` for common HTTP errors (400, 401, 403, 404, 405, 409, 500, 502, 503)
- Exception hierarchy: `BaseException` → `NotFoundException`, `BadRequestException`, etc.
- `GlobalExceptionHandler` auto-configured `@RestControllerAdvice`

**Details**: @docs/exception-handling.md

### API Response (`response/`)

- `ApiResponse<T>` standard wrapper for all API responses
- `ApiResponseAdvice` optional automatic wrapping (disabled by default)
- `@ApiResponseIgnore` to exclude specific endpoints

**Details**: @docs/api-response.md

### Event Infrastructure (`event/`)

- `DomainEvent` interface for domain events
- `CloudEvent<T>` CNCF CloudEvents v1.0 implementation
- `EventPublisher` interface and `AbstractEventPublisher` base class
- `EventHandler<T>` for consuming events
- `EventValidator` for spec compliance validation

**Details**: @docs/event-system.md

### OpenAPI/Swagger (`openapi/`)

Auto-configured OpenAPI documentation with JWT support.

```yaml
common:
  openapi:
    enabled: true
    title: "My Service API"
    version: "v1.0.0"
    jwt-auth-enabled: true
```

### Annotations & Utilities

- `@UseCase` - Marks application layer use case classes
- `@NotBlankIfPresent`, `@EnumIfPresent` - Validation annotations
- `TransactionRunner` - Programmatic transaction management
- `JsonUtils` - Pre-configured Jackson ObjectMapper

**Details**: @docs/utilities.md

## Auto-Configuration

`CommonCoreAutoConfiguration` automatically registers:
- `GlobalExceptionHandler` (enabled by default)
- `ApiResponseAdvice` (disabled by default)
- `ObjectMapper` (if not already defined)
- `TransactionRunner`

```yaml
common:
  core:
    exception-handler:
      enabled: true   # Default: true
    response-advice:
      enabled: false  # Default: false
```

## Documentation

| Document                          | Description |
|-----------------------------------|-------------|
| @./claude/docs/getting-started.md | Quick start guide and setup |
| @./claude/docs/exception-handling.md       | Error codes and exception handling |
| @./claude/docs/api-response.md             | Response format and wrapping |
| @./claude/docs/event-system.md             | CloudEvents-based event system |
| @./claude/docs/utilities.md                | Transaction, JSON, validation annotations |

## Publishing New Versions

1. Update version in `build.gradle.kts`
2. Run `./gradlew spotlessApply`
3. Run `./gradlew test`
4. Commit and push
5. GitHub Actions will publish automatically on push to main
