package com.smartexpense.tracker.data.model

import java.util.UUID

data class TrackedItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "",
    val imageUri: String = "",
    val priceEntries: List<PriceEntry> = emptyList(),
    val targetPrice: Double = 0.0,
    val alertEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val currentPrice: Double get() = priceEntries.maxByOrNull { it.timestamp }?.price ?: 0.0
    val lowestPrice: Double get() = priceEntries.minOfOrNull { it.price } ?: 0.0
    val highestPrice: Double get() = priceEntries.maxOfOrNull { it.price } ?: 0.0
    val priceCount: Int get() = priceEntries.size
    val hasPriceDrop: Boolean get() {
        if (priceEntries.size < 2) return false
        val sorted = priceEntries.sortedByDescending { it.timestamp }
        return sorted[0].price < sorted[1].price
    }
}

data class PriceEntry(
    val id: String = UUID.randomUUID().toString(),
    val price: Double,
    val storeName: String = "",
    val location: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val currencyCode: String = "USD",
    val notes: String = "",
    val scannedFromCamera: Boolean = false
)
