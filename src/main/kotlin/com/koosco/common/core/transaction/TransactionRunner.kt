package com.koosco.common.core.transaction

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface TransactionRunner {

    fun <T> run(func: () -> T): T

    fun <T> readOnly(func: () -> T): T

    fun <T> runNew(func: () -> T): T
}

@Component
class TransactionRunnerImpl : TransactionRunner {

    @Transactional
    override fun <T> run(func: () -> T): T {
        return func()
    }

    @Transactional(readOnly = true)
    override fun <T> readOnly(func: () -> T): T {
        return func()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun <T> runNew(func: () -> T): T {
        return func()
    }
}
