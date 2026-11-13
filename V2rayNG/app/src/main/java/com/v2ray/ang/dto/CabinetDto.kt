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

/** `GET /auth/me`. Email-only accounts have no Telegram id/username and vice versa. */
data class CabinetUser(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("telegram_id") val telegramId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("email") val email: String? = null,
)

/** `GET /subscription/trial`. */
data class CabinetTrialInfo(
    @SerializedName("is_available") val isAvailable: Boolean = false,
    @SerializedName("duration_days") val durationDays: Int = 0,
    @SerializedName("requires_payment") val requiresPayment: Boolean = false,
    @SerializedName("price_rubles") val priceRubles: Double = 0.0,
    /** English server text, set only when [isAvailable] is false. */
    @SerializedName("reason_unavailable") val reasonUnavailable: String? = null,
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

    /** Support: the user already has an unclosed ticket (one at a time). */
    TICKET_ALREADY_OPEN,

    /** Support: the ticket is closed, replies are not accepted. */
    TICKET_CLOSED,

    /** Support: tickets are switched off on the server, or the user is blocked from support. */
    TICKETS_UNAVAILABLE,

    /** No saved login: the user must sign in first. */
    NOT_LOGGED_IN,

    /** Trial: already used by this account. */
    TRIAL_USED,

    /** Trial: switched off for this account type, or the account already has a subscription. */
    TRIAL_UNAVAILABLE,

    /** Trial: activation is paid and the balance is too low. */
    INSUFFICIENT_BALANCE,
}

/** Result of loading the account subscription into the server list. */
data class CabinetSyncResult(
    val hasSubscription: Boolean,
    val isActive: Boolean,
    val endDate: String?,
    val configCount: Int,
)

// ---------------------------------------------------------------------------
// Support tickets (`/api/cabinet/tickets`)
// ---------------------------------------------------------------------------

data class CabinetTicketMessage(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("message_text") val text: String = "",
    @SerializedName("is_from_admin") val isFromAdmin: Boolean = false,
    @SerializedName("has_media") val hasMedia: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class CabinetTicket(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("title") val title: String = "",
    /** open | answered | pending | closed */
    @SerializedName("status") val status: String = "",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("messages_count") val messagesCount: Int = 0,
    @SerializedName("last_message") val lastMessage: CabinetTicketMessage? = null,
)

data class CabinetTicketList(
    @SerializedName("items") val items: List<CabinetTicket> = emptyList(),
)

data class CabinetTicketDetail(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("is_reply_blocked") val isReplyBlocked: Boolean = false,
    @SerializedName("messages") val messages: List<CabinetTicketMessage> = emptyList(),
)

// ---------------------------------------------------------------------------
// Devices (`/api/cabinet/subscription/devices`) — Remnawave HWID devices
// ---------------------------------------------------------------------------

data class CabinetDevice(
    @SerializedName("hwid") val hwid: String = "",
    @SerializedName("platform") val platform: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    /** Last activity (Remnawave updatedAt), ISO-8601. */
    @SerializedName("created_at") val lastSeen: String? = null,
    @SerializedName("local_name") val localName: String? = null,
)

data class CabinetDevices(
    @SerializedName("devices") val devices: List<CabinetDevice> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    /** 0 = no limit. */
    @SerializedName("device_limit") val deviceLimit: Int = 0,
)
