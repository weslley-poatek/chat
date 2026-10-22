package br.com.weslleycampos.chat.core.database

import androidx.room3.withWriteTransaction

/**
 * Runs DAO calls as one write transaction, for a write that spans more than one DAO: everything in
 * the block commits together, or nothing does when it throws.
 */
interface TransactionRunner {
    suspend fun <R> inWriteTransaction(block: suspend () -> R): R
}

/** DAO calls inside the block run on the transaction's connection, which Room keeps in the coroutine context. */
internal class RoomTransactionRunner(private val database: ChatDatabase) : TransactionRunner {
    override suspend fun <R> inWriteTransaction(block: suspend () -> R): R =
        database.withWriteTransaction { block() }
}
