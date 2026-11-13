package com.v2ray.ang.ui.account

import com.v2ray.ang.dto.CabinetSyncResult
import com.v2ray.ang.dto.CabinetTrialInfo
import com.v2ray.ang.dto.CabinetUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccountDetailsTest {

    private fun sync(active: Boolean, has: Boolean = active) =
        CabinetSyncResult(hasSubscription = has, isActive = active, endDate = null, configCount = 0)

    @Test
    fun identityTelegramUsernameAndEmail() {
        val id = accountIdentity(CabinetUser(telegramId = 1, username = "@nick", email = " a@b.ru "))
        assertEquals(AccountIdentity(telegram = "@nick", email = "a@b.ru"), id)
    }

    @Test
    fun identityEmailOnly() {
        assertEquals(AccountIdentity(null, "a@b.ru"), accountIdentity(CabinetUser(email = "a@b.ru", firstName = "Ann")))
    }

    @Test
    fun identityTelegramWithoutUsernameFallsBackToNameThenId() {
        assertEquals("Ivan Petrov", accountIdentity(CabinetUser(telegramId = 5, firstName = "Ivan", lastName = " Petrov"))?.telegram)
        assertEquals("ID 5", accountIdentity(CabinetUser(telegramId = 5, firstName = " "))?.telegram)
    }

    @Test
    fun identityEmptyGivesNull() {
        assertNull(accountIdentity(CabinetUser(email = " ", username = "")))
    }

    @Test
    fun trialAvailableFreeAndPaid() {
        assertEquals(TrialOffer.Available(3, null), trialOffer(CabinetTrialInfo(isAvailable = true, durationDays = 3), sync(false)))
        assertEquals(
            TrialOffer.Available(3, 50.0),
            trialOffer(CabinetTrialInfo(isAvailable = true, durationDays = 3, requiresPayment = true, priceRubles = 50.0), sync(false, has = true)),
        )
        // requires_payment with a zero price is shown as free.
        assertEquals(
            TrialOffer.Available(3, null),
            trialOffer(CabinetTrialInfo(isAvailable = true, durationDays = 3, requiresPayment = true), null),
        )
    }

    @Test
    fun trialUnavailableReasons() {
        assertEquals(TrialOffer.Used, trialOffer(CabinetTrialInfo(reasonUnavailable = "Trial already used"), sync(false)))
        assertNull(trialOffer(CabinetTrialInfo(reasonUnavailable = "You already have an active subscription"), sync(false)))
        assertEquals(
            TrialOffer.Unavailable,
            trialOffer(CabinetTrialInfo(reasonUnavailable = "Trial is not available for your account type"), sync(false)),
        )
        assertEquals(TrialOffer.Unavailable, trialOffer(CabinetTrialInfo(), sync(false)))
    }

    @Test
    fun trialHiddenWithoutDataOrWithActiveSubscription() {
        assertNull(trialOffer(null, sync(false)))
        assertNull(trialOffer(CabinetTrialInfo(isAvailable = true, durationDays = 3), sync(true)))
    }

    @Test
    fun rublesFormatting() {
        assertEquals("50", formatRubles(50.0))
        assertEquals("49.50", formatRubles(49.5))
    }
}
