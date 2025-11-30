package com.koosco.common.core.event

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Utility functions for working with CloudEvents.
 */
object EventUtils {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    /**
     * Serialize a CloudEvent to JSON string.
     *
     * @param event The CloudEvent to serialize
     * @return JSON string representation
     * @throws EventSerializationException if serialization fails
     */
    fun <T> toJson(event: CloudEvent<T>): String {
        return try {
            objectMapper.writeValueAsString(event)
        } catch (e: Exception) {
            throw EventSerializationException("Failed to serialize CloudEvent to JSON", e)
        }
    }

    /**
     * Deserialize a JSON string to CloudEvent.
     *
     * @param json The JSON string
     * @return CloudEvent instance
     * @throws EventSerializationException if deserialization fails
     */
    fun <T> fromJson(json: String, dataType: Class<T>): CloudEvent<T> {
        return try {
            val typeRef = object : TypeReference<CloudEvent<T>>() {}
            objectMapper.readValue(json, typeRef)
        } catch (e: Exception) {
            throw EventSerializationException("Failed to deserialize CloudEvent from JSON", e)
        }
    }

    /**
     * Deserialize a JSON string to CloudEvent with inline type.
     */
    inline fun <reified T> fromJson(json: String): CloudEvent<T> {
        return fromJson(json, T::class.java)
    }

    /**
     * Convert a CloudEvent to a Map for flexible processing.
     */
    fun <T> toMap(event: CloudEvent<T>): Map<String, Any?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.convertValue(event, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            throw EventSerializationException("Failed to convert CloudEvent to Map", e)
        }
    }

    /**
     * Convert a Map to CloudEvent.
     */
    fun <T> fromMap(map: Map<String, Any?>, dataType: Class<T>): CloudEvent<T> {
        return try {
            val typeRef = object : TypeReference<CloudEvent<T>>() {}
            objectMapper.convertValue(map, typeRef)
        } catch (e: Exception) {
            throw EventSerializationException("Failed to convert Map to CloudEvent", e)
        }
    }

    /**
     * Extract data from CloudEvent with type casting.
     *
     * @param event The CloudEvent
     * @param dataType The class of the data type
     * @return The data payload
     * @throws EventSerializationException if type conversion fails
     */
    fun <T, R> extractData(event: CloudEvent<T>, dataType: Class<R>): R? {
        return try {
            event.data?.let { objectMapper.convertValue(it, dataType) }
        } catch (e: Exception) {
            throw EventSerializationException("Failed to extract data from CloudEvent", e)
        }
    }

    /**
     * Create a copy of CloudEvent with different data type.
     * Useful for type conversion between CloudEvent instances.
     */
    fun <T, R> convertData(event: CloudEvent<T>, dataType: Class<R>): CloudEvent<R?> {
        val convertedData = extractData(event, dataType)
        return CloudEvent(
            id = event.id,
            source = event.source,
            specVersion = event.specVersion,
            type = event.type,
            dataContentType = event.dataContentType,
            dataSchema = event.dataSchema,
            subject = event.subject,
            time = event.time,
            data = convertedData,
        )
    }

    /**
     * Validate and serialize a CloudEvent.
     * Combines validation and serialization in one call.
     *
     * @param event The CloudEvent to validate and serialize
     * @return JSON string representation
     * @throws ValidationException if validation fails
     * @throws EventSerializationException if serialization fails
     */
    fun <T> validateAndSerialize(event: CloudEvent<T>): String {
        EventValidator.validate(event).throwIfInvalid()
        return toJson(event)
    }

    /**
     * Deserialize and validate a CloudEvent.
     * Combines deserialization and validation in one call.
     *
     * @param json The JSON string
     * @param dataType The class of the data type
     * @return CloudEvent instance
     * @throws ValidationException if validation fails
     * @throws EventSerializationException if deserialization fails
     */
    fun <T> deserializeAndValidate(json: String, dataType: Class<T>): CloudEvent<T> {
        val event = fromJson(json, dataType)
        EventValidator.validate(event).throwIfInvalid()
        return event
    }
}

/**
 * Exception thrown when event serialization/deserialization fails.
 */
class EventSerializationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
