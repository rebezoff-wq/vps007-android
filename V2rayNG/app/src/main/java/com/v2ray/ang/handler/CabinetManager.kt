package com.v2ray.ang.handler

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CabinetDeepLinkToken
import com.v2ray.ang.dto.CabinetError
import com.v2ray.ang.dto.CabinetResult
import com.v2ray.ang.dto.CabinetSubscriptionStatus
import com.v2ray.ang.dto.CabinetSyncResult
import com.v2ray.ang.dto.CabinetTicket
import com.v2ray.ang.dto.CabinetTicketDetail
import com.v2ray.ang.dto.CabinetTicketList
import com.v2ray.ang.dto.CabinetTokens
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * VPS 007 account: login against the Bedolaga cabinet API and keep the account subscription
 * in the server list.
 *
 * Owns its own MMKV store (via [MmkvManager] cabinet region) because its lifetime is the login
 * session, not app settings. Tokens are encrypted with a non-exportable Android Keystore key, so
 * an MMKV backup restored on another device contains nothing usable (the user just logs in again).
 */
object CabinetManager {

    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_SUB_ID = "sub_id"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Serializes token refresh so parallel calls do not burn the refresh token twice. */
    private val refreshMutex = Mutex()

    private data class HttpResult(val code: Int, val body: String?)

    // ---------------------------------------------------------------------
    // Session state
    // ---------------------------------------------------------------------

    fun isLoggedIn(): Boolean = !readSecret(KEY_REFRESH).isNullOrEmpty()

    /** Subscription id (MMKV SUB key) that this account manages, if any. */
    fun getLinkedSubscriptionId(): String? = MmkvManager.decodeCabinetString(KEY_SUB_ID)

    // ---------------------------------------------------------------------
    // Login: Telegram (bot deep link)
    // ---------------------------------------------------------------------

    /** Step 1: get a one-time token; the UI opens t.me/<bot>?start=webauth_<token>. */
    suspend fun requestTelegramLogin(): CabinetResult<CabinetDeepLinkToken> = withContext(Dispatchers.IO) {
        when (val r = post("/auth/deeplink/request", "{}", null)) {
            null -> CabinetResult.Failure(CabinetError.NETWORK)
            else -> if (r.code == 200) {
                val token = r.body?.let { JsonUtil.fromJsonSafe(it, CabinetDeepLinkToken::class.java) }
                if (token == null || token.token.isEmpty() || token.botUsername.isEmpty()) {
                    CabinetResult.Failure(CabinetError.SERVER)
                } else {
                    CabinetResult.Success(token)
                }
            } else {
                CabinetResult.Failure(mapError(r.code, r.body))
            }
        }
    }

    fun telegramLoginUrl(token: CabinetDeepLinkToken): String =
        "https://t.me/${token.botUsername}?start=webauth_${token.token}"

    /** Step 2: poll until the user taps «Да, войти» in the bot. Saves tokens on success. */
    suspend fun pollTelegramLogin(token: String): CabinetResult<Unit> = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("token" to token))
        when (val r = post("/auth/deeplink/poll", body, null)) {
            null -> CabinetResult.Failure(CabinetError.NETWORK)
            else -> when (r.code) {
                200 -> saveTokensFrom(r.body)
                202 -> CabinetResult.Pending
                410 -> CabinetResult.Failure(CabinetError.LINK_EXPIRED)
                else -> CabinetResult.Failure(mapError(r.code, r.body))
            }
        }
    }

    // ---------------------------------------------------------------------
    // Login: email + password
    // ---------------------------------------------------------------------

    suspend fun loginWithEmail(email: String, password: String): CabinetResult<Unit> = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("email" to email.trim(), "password" to password))
        when (val r = post("/auth/email/login", body, null)) {
            null -> CabinetResult.Failure(CabinetError.NETWORK)
            else -> if (r.code == 200) saveTokensFrom(r.body) else CabinetResult.Failure(mapError(r.code, r.body))
        }
    }

    // ---------------------------------------------------------------------
    // Subscription
    // ---------------------------------------------------------------------

    /**
     * Loads the account subscription URL from the cabinet, stores it as the app-managed
     * subscription (auto-updated in background) and downloads its servers.
     */
    suspend fun syncSubscription(): CabinetResult<CabinetSyncResult> = withContext(Dispatchers.IO) {
        val r = authorizedGet("/subscription")
            ?: return@withContext CabinetResult.Failure(CabinetError.NETWORK)
        if (r.code != 200) {
            if (r.code == 401) logoutLocal()
            return@withContext CabinetResult.Failure(mapError(r.code, r.body))
        }
        val status = r.body?.let { JsonUtil.fromJsonSafe(it, CabinetSubscriptionStatus::class.java) }
            ?: return@withContext CabinetResult.Failure(CabinetError.SERVER)

        val sub = status.subscription
        val url = sub?.subscriptionUrl
        if (!status.hasSubscription || url.isNullOrBlank()) {
            return@withContext CabinetResult.Success(
                CabinetSyncResult(hasSubscription = false, isActive = false, endDate = sub?.endDate, configCount = 0)
            )
        }

        val subId = upsertSubscription(url)
        val update = try {
            val item = MmkvManager.decodeSubscription(subId)
            if (item != null) AngConfigManager.updateConfigViaSub(SubscriptionCache(subId, item)) else null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cabinet: failed to load servers for subscription $subId", e)
            null
        }
        SettingsChangeManager.makeSetupGroupTab()
        CabinetResult.Success(
            CabinetSyncResult(
                hasSubscription = true,
                isActive = sub?.isActive == true,
                endDate = sub?.endDate,
                configCount = update?.configCount ?: 0,
            )
        )
    }

    /**
     * Creates or updates the subscription entry for the account. Reuses an existing entry with
     * the same URL (e.g. imported manually earlier) so servers are not duplicated.
     */
    private fun upsertSubscription(url: String): String {
        val linkedId = getLinkedSubscriptionId()
        val existing = MmkvManager.decodeSubscriptions().let { subs ->
            subs.firstOrNull { it.guid == linkedId } ?: subs.firstOrNull { it.subscription.url == url }
        }
        val subId = existing?.guid ?: Utils.getUuid()
        val item = (existing?.subscription ?: SubscriptionItem(remarks = AppConfig.CABINET_SUB_REMARKS)).apply {
            this.url = url
            if (remarks.isBlank()) remarks = AppConfig.CABINET_SUB_REMARKS
            enabled = true
            autoUpdate = true
            updateInterval = AppConfig.CABINET_SUB_UPDATE_INTERVAL_MINUTES
        }
        MmkvManager.encodeSubscription(subId, item)
        MmkvManager.encodeCabinetString(KEY_SUB_ID, subId)
        removeEmptyDefaultGroup()
        SubscriptionUpdater.syncOne(subId = subId)
        return subId
    }

    /** The placeholder «Default» group (no URL, no servers) only confuses users once the account group exists. */
    private fun removeEmptyDefaultGroup() {
        val defaultId = AppConfig.DEFAULT_SUBSCRIPTION_ID
        val sub = MmkvManager.decodeSubscription(defaultId) ?: return
        if (sub.url.isBlank() && MmkvManager.decodeServerList(defaultId).isEmpty()) {
            MmkvManager.removeSubscription(defaultId)
        }
    }

    // ---------------------------------------------------------------------
    // Support tickets
    // ---------------------------------------------------------------------

    suspend fun listTickets(): CabinetResult<List<CabinetTicket>> = withContext(Dispatchers.IO) {
        val r = authorized("/tickets?page=1&per_page=50", null)
            ?: return@withContext CabinetResult.Failure(CabinetError.NETWORK)
        if (r.code != 200) return@withContext CabinetResult.Failure(mapTicketError(r.code, r.body))
        val list = r.body?.let { JsonUtil.fromJsonSafe(it, CabinetTicketList::class.java) }
            ?: return@withContext CabinetResult.Failure(CabinetError.SERVER)
        CabinetResult.Success(list.items)
    }

    suspend fun getTicket(id: Long): CabinetResult<CabinetTicketDetail> = withContext(Dispatchers.IO) {
        val r = authorized("/tickets/$id", null) ?: return@withContext CabinetResult.Failure(CabinetError.NETWORK)
        parseTicketDetail(r)
    }

    suspend fun createTicket(title: String, message: String): CabinetResult<CabinetTicketDetail> =
        withContext(Dispatchers.IO) {
            val body = JsonUtil.toJson(mapOf("title" to title.trim(), "message" to message.trim()))
            val r = authorized("/tickets", body) ?: return@withContext CabinetResult.Failure(CabinetError.NETWORK)
            parseTicketDetail(r)
        }

    suspend fun replyTicket(id: Long, message: String): CabinetResult<Unit> = withContext(Dispatchers.IO) {
        val body = JsonUtil.toJson(mapOf("message" to message.trim()))
        val r = authorized("/tickets/$id/messages", body) ?: return@withContext CabinetResult.Failure(CabinetError.NETWORK)
        if (r.code == 200) CabinetResult.Success(Unit) else CabinetResult.Failure(mapTicketError(r.code, r.body))
    }

    private fun parseTicketDetail(r: HttpResult): CabinetResult<CabinetTicketDetail> {
        if (r.code != 200) return CabinetResult.Failure(mapTicketError(r.code, r.body))
        val detail = r.body?.let { JsonUtil.fromJsonSafe(it, CabinetTicketDetail::class.java) }
            ?: return CabinetResult.Failure(CabinetError.SERVER)
        return CabinetResult.Success(detail)
    }

    private fun mapTicketError(code: Int, body: String?): CabinetError = when (code) {
        401 -> CabinetError.NOT_LOGGED_IN
        409 -> CabinetError.TICKET_ALREADY_OPEN
        400 -> if (body?.contains("closed", ignoreCase = true) == true) CabinetError.TICKET_CLOSED else CabinetError.SERVER
        403 -> CabinetError.TICKETS_UNAVAILABLE
        else -> mapError(code, body)
    }

    // ---------------------------------------------------------------------
    // Logout
    // ---------------------------------------------------------------------

    /** Revokes the session on the server (best effort) and removes the account subscription. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        readSecret(KEY_REFRESH)?.let { refresh ->
            post("/auth/logout", JsonUtil.toJson(mapOf("refresh_token" to refresh)), readSecret(KEY_ACCESS))
        }
        getLinkedSubscriptionId()?.let { subId ->
            if (MmkvManager.decodeSubscription(subId) != null) {
                SettingsManager.removeSubscriptionWithDefault(subId)
                SettingsChangeManager.makeSetupGroupTab()
            }
        }
        logoutLocal()
    }

    private fun logoutLocal() {
        MmkvManager.clearCabinet()
    }

    // ---------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------

    private fun saveTokensFrom(body: String?): CabinetResult<Unit> {
        val tokens = body?.let { JsonUtil.fromJsonSafe(it, CabinetTokens::class.java) }
        if (tokens == null || tokens.accessToken.isEmpty() || tokens.refreshToken.isEmpty()) {
            return CabinetResult.Failure(CabinetError.SERVER)
        }
        writeSecret(KEY_ACCESS, tokens.accessToken)
        writeSecret(KEY_REFRESH, tokens.refreshToken)
        return CabinetResult.Success(Unit)
    }

    /** GET with the access token; on 401 refreshes the token once and retries. */
    private suspend fun authorizedGet(path: String): HttpResult? = authorized(path, null)

    /** GET (jsonBody == null) or POST with the access token; on 401 refreshes once and retries. */
    private suspend fun authorized(path: String, jsonBody: String?): HttpResult? {
        val access = readSecret(KEY_ACCESS) ?: return HttpResult(401, null)
        val send: (String?) -> HttpResult? = { token ->
            if (jsonBody == null) get(path, token) else post(path, jsonBody, token)
        }
        val first = send(access) ?: return null
        if (first.code != 401) return first
        if (!refreshTokens(access)) return HttpResult(401, null)
        return send(readSecret(KEY_ACCESS))
    }

    private suspend fun refreshTokens(staleAccess: String): Boolean = refreshMutex.withLock {
        // Another caller already refreshed while we waited.
        if (readSecret(KEY_ACCESS) != staleAccess) return@withLock true
        val refresh = readSecret(KEY_REFRESH) ?: return@withLock false
        val r = post("/auth/refresh", JsonUtil.toJson(mapOf("refresh_token" to refresh)), null)
            ?: return@withLock false
        r.code == 200 && saveTokensFrom(r.body) is CabinetResult.Success
    }

    private fun get(path: String, bearer: String?): HttpResult? =
        execute(Request.Builder().url(AppConfig.CABINET_API_URL + path).get(), bearer)

    private fun post(path: String, json: String, bearer: String?): HttpResult? =
        execute(Request.Builder().url(AppConfig.CABINET_API_URL + path).post(json.toRequestBody(JSON)), bearer)

    private fun execute(builder: Request.Builder, bearer: String?): HttpResult? {
        builder.header("Accept", "application/json")
        if (!bearer.isNullOrEmpty()) builder.header("Authorization", "Bearer $bearer")
        return try {
            client.newCall(builder.build()).execute().use { HttpResult(it.code, it.body?.string()) }
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "Cabinet request failed", e)
            null
        }
    }

    private fun mapError(code: Int, body: String?): CabinetError = when (code) {
        401 -> CabinetError.INVALID_CREDENTIALS
        403 -> if (body?.contains("verify", ignoreCase = true) == true) {
            CabinetError.EMAIL_NOT_VERIFIED
        } else {
            CabinetError.ACCOUNT_DISABLED
        }
        410 -> CabinetError.LINK_EXPIRED
        422 -> CabinetError.INVALID_CREDENTIALS
        429 -> CabinetError.TOO_MANY_REQUESTS
        else -> CabinetError.SERVER
    }

    // ---------------------------------------------------------------------
    // Token encryption (Android Keystore, AES-GCM)
    // ---------------------------------------------------------------------

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "vps007_cabinet_tokens"
    private const val GCM_IV_BYTES = 12

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun writeSecret(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            MmkvManager.encodeCabinetString(key, Base64.encodeToString(data, Base64.NO_WRAP))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cabinet: failed to store $key", e)
        }
    }

    private fun readSecret(key: String): String? {
        val stored = MmkvManager.decodeCabinetString(key) ?: return null
        return try {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, GCM_IV_BYTES))
            String(cipher.doFinal(data, GCM_IV_BYTES, data.size - GCM_IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // Restored from a backup made on another device, or the key was wiped: treat as logged out.
            LogUtil.w(AppConfig.TAG, "Cabinet: stored $key is unreadable, clearing session", e)
            MmkvManager.encodeCabinetString(key, null)
            null
        }
    }
}
