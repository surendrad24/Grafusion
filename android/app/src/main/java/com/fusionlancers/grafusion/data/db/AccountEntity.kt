package com.fusionlancers.grafusion.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType { BASIC, BEARER }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val grafanaUrl: String,
    val userId: Long,
    val login: String,
    val displayName: String,
    val active: Boolean = false,
    /** "BASIC" (vault stores full Basic header) or "BEARER" (vault stores raw token). */
    val authType: String = AuthType.BASIC.name,
    /**
     * Reference key into TokenVault (EncryptedSharedPreferences).
     * The token itself is NEVER persisted in Room.
     */
    val tokenVaultKey: String,
    /**
     * Optional SPKI SHA-256 pin (base64) of the server's leaf certificate. When set,
     * the OkHttp client refuses handshakes whose cert chain does not include this pin -
     * a defensive TOFU (trust-on-first-use) guard for self-hosted HTTPS instances
     * that don't chain to a public CA. Null means default system-trust validation.
     */
    val certPinSha256: String? = null,
)
