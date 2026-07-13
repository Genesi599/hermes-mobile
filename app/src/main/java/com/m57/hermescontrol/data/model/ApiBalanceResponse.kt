package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiBalanceResponse(
    val updated_at: String,
    val cache_seconds: Int = 300,
    val accounts: List<ApiBalanceAccount> = emptyList(),
)

@Serializable
data class ApiBalanceAccount(
    val provider: String,
    val account: String,
    val balance: String,
    val usage: String,
    val expires: String,
    val status: String,
    val low: Boolean = false,
    val summary: Boolean = false,
)
