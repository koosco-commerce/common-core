package com.koosco.common.core.event

/**
 * Interface for handling domain events.
 * Implementations should be registered as Spring beans and will be auto-discovered.
 *
 * Example:
 * ```
 * @Component
 * class OrderCreatedEventHandler : EventHandler<OrderCreatedEvent> {
 *     override fun handle(event: OrderCreatedEvent) {
 *         // Handle order created event
 *         log.info("Order created: ${event.orderId}")
 *     }
 *
 *     override fun canHandle(eventType: String): Boolean {
 *         return eventType == "com.koosco.order.created"
 *     }
 * }
 * ```
 */
interface EventHandler<T : DomainEvent> {
    /**
     * Handle the domain event.
     *
     * @param event The domain event to handle
     * @throws EventHandlingException if handling fails
     */
    fun handle(event: T)

    /**
     * Check if this handler can handle the given event type.
     *
     * @param eventType The event type to check
     * @return true if this handler can handle the event type
     */
    fun canHandle(eventType: String): Boolean

    /**
     * Get the order of this handler.
     * Lower values have higher priority.
     * Default is 0.
     */
    fun getOrder(): Int = 0
}

/**
 * Interface for handling CloudEvents directly.
 * Use this when you need to work with the CloudEvent wrapper.
 */
interface CloudEventHandler<T> {
    /**
     * Handle the CloudEvent.
     *
     * @param event The CloudEvent to handle
     * @throws EventHandlingException if handling fails
     */
    fun handle(event: CloudEvent<T>)

    /**
     * Check if this handler can handle the given event type.
     *
     * @param eventType The CloudEvent type to check
     * @return true if this handler can handle the event type
     */
    fun canHandle(eventType: String): Boolean

    /**
     * Get the order of this handler.
     * Lower values have higher priority.
     * Default is 0.
     */
    fun getOrder(): Int = 0
}

/**
 * Exception thrown when event handling fails.
 */
class EventHandlingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Annotation for marking event handler methods.
 * Can be used with Spring's component scanning.
 *
 * Example:
 * ```
 * @Component
 * class OrderEventHandlers {
 *     @EventListener(eventType = "com.koosco.order.created")
 *     fun handleOrderCreated(event: OrderCreatedEvent) {
 *         // Handle event
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventListener(
    /**
     * The event type this listener handles.
     */
    val eventType: String = "",
    /**
     * The order of execution. Lower values execute first.
     */
    val order: Int = 0,
)
