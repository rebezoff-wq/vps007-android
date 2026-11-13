package com.v2ray.ang.handler

import com.v2ray.ang.dto.CabinetError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrialActivationErrorTest {

    @Test
    fun mapsTrialSpecificErrors() {
        assertEquals(CabinetError.TRIAL_USED, trialActivationError(400, """{"detail":"Trial already used"}"""))
        assertEquals(CabinetError.INSUFFICIENT_BALANCE, trialActivationError(400, """{"detail":"Insufficient balance. Need 50 RUB"}"""))
        assertEquals(CabinetError.INSUFFICIENT_BALANCE, trialActivationError(402, null))
        assertEquals(
            CabinetError.TRIAL_UNAVAILABLE,
            trialActivationError(400, """{"detail":"Trial is not available for your account type"}"""),
        )
        assertEquals(CabinetError.TRIAL_UNAVAILABLE, trialActivationError(400, null))
    }

    @Test
    fun leavesGenericCodesToTheCaller() {
        assertNull(trialActivationError(401, null))
        assertNull(trialActivationError(409, """{"detail":{"code":"x"}}"""))
        assertNull(trialActivationError(500, null))
    }
}
