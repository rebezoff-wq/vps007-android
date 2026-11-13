package com.v2ray.ang.ui.account

import com.v2ray.ang.dto.CabinetSyncResult
import com.v2ray.ang.dto.CabinetTrialInfo
import com.v2ray.ang.dto.CabinetUser
import java.util.Locale

/** Who is signed in, ready to display. A null part is not shown. */
data class AccountIdentity(
    /** «@username», else the Telegram name, else «ID 123»; null for email-only accounts. */
    val telegram: String?,
    val email: String?,
)

/** Trial block of the account screen. */
sealed interface TrialOffer {
    /** [priceRubles] is null when the trial is free. */
    data class Available(val days: Int, val priceRubles: Double?) : TrialOffer
    data object Used : TrialOffer
    data object Unavailable : TrialOffer
}

internal fun accountIdentity(user: CabinetUser): AccountIdentity? {
    val email = user.email?.trim()?.takeIf { it.isNotEmpty() }
    val username = user.username?.trim()?.removePrefix("@")?.takeIf { it.isNotEmpty() }
    val telegram = when {
        username != null -> "@$username"
        user.telegramId == null -> null
        else -> listOfNotNull(user.firstName, user.lastName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }
            ?: "ID ${user.telegramId}"
    }
    return if (telegram == null && email == null) null else AccountIdentity(telegram, email)
}

/**
 * What to show about the trial. Null hides the block: no data yet, the subscription is active,
 * or the server says the account already has a subscription.
 */
internal fun trialOffer(info: CabinetTrialInfo?, sync: CabinetSyncResult?): TrialOffer? {
    if (info == null || sync?.isActive == true) return null
    if (info.isAvailable) {
        val price = info.priceRubles.takeIf { info.requiresPayment && it > 0.0 }
        return TrialOffer.Available(info.durationDays, price)
    }
    // reason_unavailable is English server text (bot `subscription_modules/purchase.py`).
    val reason = info.reasonUnavailable.orEmpty()
    return when {
        reason.contains("already used", ignoreCase = true) -> TrialOffer.Used
        reason.contains("active subscription", ignoreCase = true) -> null
        else -> TrialOffer.Unavailable
    }
}

/** 50.0 -> «50», 49.5 -> «49.50». */
internal fun formatRubles(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ROOT, "%.2f", value)
