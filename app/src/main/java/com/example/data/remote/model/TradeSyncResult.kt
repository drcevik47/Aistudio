package com.example.data.remote.model

/**
 * Borsadan çekilen işlemlerin veritabanı ile karşılaştırılması ve senkronizasyon sonucu.
 */
data class TradeSyncResult(
    val totalFetched: Int,
    val existingInDb: Int,
    val newlyAddedCount: Int,
    val totalInDb: Int,
    val newlyAddedTrades: List<BybitExecutionDto> = emptyList(),
    val analysis: TradeAnalysisResult? = null
)
