package com.koosco.common.core.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EventUtilsTest {

    data class OrderData(
        val orderId: String,
        val amount: Int,
    )

    @Test
    fun `should serialize CloudEvent to JSON`() {
        // given
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
            subject = "order-123",
        )

        // when
        val json = EventUtils.toJson(event)

        // then
        assertNotNull(json)
        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"source\""))
        assertTrue(json.contains("\"type\""))
        assertTrue(json.contains("order-123"))
    }

    @Test
    fun `should deserialize JSON to CloudEvent`() {
        // given
        val originalEvent = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )
        val json = EventUtils.toJson(originalEvent)

        // when
        val deserializedEvent = EventUtils.fromJson<OrderData>(json)

        // then
        assertEquals(originalEvent.id, deserializedEvent.id)
        assertEquals(originalEvent.source, deserializedEvent.source)
        assertEquals(originalEvent.type, deserializedEvent.type)
    }

    @Test
    fun `should convert CloudEvent to Map`() {
        // given
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )

        // when
        val map = EventUtils.toMap(event)

        // then
        assertEquals(event.id, map["id"])
        assertEquals(event.source, map["source"])
        assertEquals(event.type, map["type"])
        assertNotNull(map["data"])
    }

    @Test
    fun `should convert Map to CloudEvent`() {
        // given
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )
        val map = EventUtils.toMap(event)

        // when
        val convertedEvent = EventUtils.fromMap(map, OrderData::class.java)

        // then
        assertEquals(event.id, convertedEvent.id)
        assertEquals(event.source, convertedEvent.source)
        assertEquals(event.type, convertedEvent.type)
    }

    @Test
    fun `should extract data from CloudEvent`() {
        // given
        val orderData = OrderData("order-123", 10000)
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = orderData,
        )

        // when
        val extractedData = EventUtils.extractData(event, OrderData::class.java)

        // then
        assertNotNull(extractedData)
        assertEquals(orderData.orderId, extractedData?.orderId)
        assertEquals(orderData.amount, extractedData?.amount)
    }

    @Test
    fun `should convert data between CloudEvent instances`() {
        // given
        data class SourceData(val value: String)
        data class TargetData(val value: String)

        val sourceEvent = CloudEvent.of(
            source = "urn:koosco:service",
            type = "test.event",
            data = SourceData("test"),
        )

        // when
        val targetEvent = EventUtils.convertData(sourceEvent, TargetData::class.java)

        // then
        assertEquals(sourceEvent.id, targetEvent.id)
        assertEquals(sourceEvent.source, targetEvent.source)
        assertEquals(sourceEvent.type, targetEvent.type)
        assertNotNull(targetEvent.data)
        assertEquals("test", targetEvent.data?.value)
    }

    @Test
    fun `should validate and serialize CloudEvent`() {
        // given
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )

        // when
        val json = EventUtils.validateAndSerialize(event)

        // then
        assertNotNull(json)
        assertTrue(json.contains("order-123"))
    }

    @Test
    fun `should fail validate and serialize for invalid CloudEvent`() {
        // given
        val event = CloudEvent<OrderData>(
            id = " ",
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
        )

        // when & then
        assertThrows(ValidationException::class.java) {
            EventUtils.validateAndSerialize(event)
        }
    }

    @Test
    fun `should deserialize and validate CloudEvent`() {
        // given
        val originalEvent = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )
        val json = EventUtils.toJson(originalEvent)

        // when
        val event = EventUtils.deserializeAndValidate(json, OrderData::class.java)

        // then
        assertEquals(originalEvent.id, event.id)
        assertEquals(originalEvent.source, event.source)
        assertEquals(originalEvent.type, event.type)
    }

    @Test
    fun `should handle null data in CloudEvent`() {
        // given
        val event = CloudEvent.of<OrderData?>(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = null,
        )

        // when
        val json = EventUtils.toJson(event)
        val deserializedEvent = EventUtils.fromJson<OrderData?>(json)

        // then
        assertEquals(event.id, deserializedEvent.id)
        assertNull(deserializedEvent.data)
    }

    @Test
    fun `should serialize and deserialize with time field`() {
        // given
        val event = CloudEvent.of(
            source = "urn:koosco:order-service",
            type = "com.koosco.order.created",
            data = OrderData("order-123", 10000),
        )

        // when
        val json = EventUtils.toJson(event)
        val deserializedEvent = EventUtils.fromJson<OrderData>(json)

        // then
        assertNotNull(deserializedEvent.time)
    }
}
