package com.koosco.common.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.koosco.common.core.exception.GlobalExceptionHandler
import com.koosco.common.core.response.ApiResponseAdvice
import com.koosco.common.core.util.JsonUtils
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Auto-configuration for common-core library.
 * Automatically configures exception handling and response wrapping for Spring Web applications.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CommonCoreAutoConfiguration {

    /**
     * Register GlobalExceptionHandler for consistent error handling.
     * Can be disabled via property: common.core.exception-handler.enabled=false
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler::class)
    @ConditionalOnProperty(
        prefix = "common.core.exception-handler",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun globalExceptionHandler(): GlobalExceptionHandler = GlobalExceptionHandler()

    /**
     * Register ApiResponseAdvice for automatic response wrapping.
     * Disabled by default. Enable via property: common.core.response-advice.enabled=true
     */
    @Bean
    @ConditionalOnMissingBean(ApiResponseAdvice::class)
    @ConditionalOnProperty(
        prefix = "common.core.response-advice",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun apiResponseAdvice(): ApiResponseAdvice = ApiResponseAdvice()

    /**
     * Provide pre-configured ObjectMapper.
     * Only created if no other ObjectMapper is defined.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun objectMapper(): ObjectMapper = JsonUtils.objectMapper
}
