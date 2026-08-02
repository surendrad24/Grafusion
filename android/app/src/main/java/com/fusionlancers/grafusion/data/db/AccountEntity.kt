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
)
