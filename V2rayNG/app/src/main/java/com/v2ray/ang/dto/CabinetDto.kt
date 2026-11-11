package com.v2ray.ang.dto

import com.google.gson.annotations.SerializedName

/**
 * Wire models for the Bedolaga cabinet API (`/api/cabinet`).
 * Only the fields the app uses are declared; unknown fields are ignored by Gson.
 */

data class CabinetDeepLinkToken(
    @SerializedName("token") val token: String = "",
    @SerializedName("bot_username") val botUsername: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0,
)

data class CabinetTokens(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0,
)

data class CabinetSubscriptionStatus(
    @SerializedName("has_subscription") val hasSubscription: Boolean = false,
    @SerializedName("subscription") val subscription: CabinetSubscription? = null,
)

data class CabinetSubscription(
    @SerializedName("status") val status: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("subscription_url") val subscriptionUrl: String? = null,
)

/** Outcome of a cabinet call, mapped to what the UI needs to show. */
sealed interface CabinetResult<out T> {
    data class Success<T>(val value: T) : CabinetResult<T>

    /** Telegram confirmation not given yet (deep link poll returned 202). */
    data object Pending : CabinetResult<Nothing>

    data class Failure(val error: CabinetError) : CabinetResult<Nothing>
}

enum class CabinetError {
    /** Wrong email/password, or the saved login is no longer valid. */
    INVALID_CREDENTIALS,

    /** Email registered but not confirmed yet. */
    EMAIL_NOT_VERIFIED,

    /** Account blocked/deactivated. */
    ACCOUNT_DISABLED,

    /** Telegram confirmation link expired or was already used. */
    LINK_EXPIRED,

    TOO_MANY_REQUESTS,
    NETWORK,
    SERVER,
}

/** Result of loading the account subscription into the server list. */
data class CabinetSyncResult(
    val hasSubscription: Boolean,
    val isActive: Boolean,
    val endDate: String?,
    val configCount: Int,
)
