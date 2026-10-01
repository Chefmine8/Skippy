package com.skippy.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Microsoft token access.
 *  - "msal": MSAL single-account app, silent refresh (works in the background worker).
 *  - "manual": a token pasted by the user, stored encrypted with an Android Keystore key.
 */
object Auth {
    /** Scopes requested by MSAL. Must match what the Zeus API accepts (see README). */
    val SCOPES: List<String> = listOf("User.Read")

    @Volatile private var pca: ISingleAccountPublicClientApplication? = null

    /** Blocking (reads the config and may touch disk): call from a background thread only. */
    fun app(ctx: Context): ISingleAccountPublicClientApplication =
        pca ?: synchronized(this) {
            pca ?: PublicClientApplication
                .createSingleAccountPublicClientApplication(ctx.applicationContext, R.raw.auth_config)
                .also { pca = it }
        }

    /** A valid access token, or null when signed out / expired / interaction required. */
    suspend fun accessToken(ctx: Context): String? = withContext(Dispatchers.IO) {
        when (Repo.get(ctx).settings().authMode) {
            "msal" -> silent(ctx)
            "manual" -> TokenStore.load(ctx)?.takeIf { !isExpired(it) }
            else -> null
        }
    }

    private fun silent(ctx: Context): String? = try {
        val a = app(ctx)
        val account = a.currentAccount?.currentAccount
        if (account == null) null else {
            val params = AcquireTokenSilentParameters.Builder()
                .withScopes(SCOPES)
                .forAccount(account)
                .fromAuthority(account.authority)
                .build()
            a.acquireTokenSilent(params).accessToken
        }
    } catch (e: Exception) {
        null
    }

    fun saveManual(ctx: Context, rawToken: String) {
        val token = rawToken.trim().removePrefix("Bearer ").trim()
        TokenStore.save(ctx, token)
        Repo.get(ctx).setAuthMode("manual")
    }

    suspend fun signOut(ctx: Context) = withContext(Dispatchers.IO) {
        runCatching { app(ctx).signOut() }
        TokenStore.clear(ctx)
        Repo.get(ctx).setAuthMode("")
    }

    /** JWT "exp" claim, when the token is a JWT. Unknown format = assume valid. */
    private fun isExpired(token: String): Boolean = try {
        val payload = token.split(".")[1]
        val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        json.optLong("exp", Long.MAX_VALUE / 1000) * 1000 < System.currentTimeMillis() + 60_000
    } catch (e: Exception) {
        false
    }
}

/** AES-256-GCM with a non-exportable key kept in the Android Keystore. */
object TokenStore {
    private const val ALIAS = "skippy_token_key"
    private const val PREF = "skippy_secure"
    private const val KEY = "token"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(ctx: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    fun load(ctx: Context): String? = try {
        val stored = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        if (stored == null) null else {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
