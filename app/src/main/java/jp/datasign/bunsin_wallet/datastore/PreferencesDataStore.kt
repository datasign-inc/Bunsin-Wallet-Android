package jp.datasign.bunsin_wallet.datastore

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import arrow.core.Either
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.utils.BiometricUtil
import jp.datasign.bunsin_wallet.utils.EncryptionHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesDataStore(private val context: Context) {
//    private val Context.dataStore by preferencesDataStore(name = "settings")

    companion object {
        val SEED_KEY = stringPreferencesKey("seed")
        val LAST_BACKUP_AT_KEY = stringPreferencesKey("last_backup_at_key")
        val KEY_SHOULD_LOCK = booleanPreferencesKey("shouldLock")
        val KEY_LAST_UNLOCK_TIME = longPreferencesKey("lastUnlockTime")
        val KEY_LAST_SAFE_ON_STOP_TIME = longPreferencesKey("lastSafeOnStopTime")
    }

    suspend fun saveLastSafeOnStopTime(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_SAFE_ON_STOP_TIME] = value
        }
    }

    suspend fun getLastSafeOnStopTime(): Long {
        val seedFlow: Flow<Long?> = context.dataStore.data
            .map { preferences ->
                preferences[KEY_LAST_SAFE_ON_STOP_TIME]
            }
        val value = seedFlow.first()
        return value?: 0
    }

    suspend fun saveLastUnlockTime(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_UNLOCK_TIME] = value
        }
    }

    suspend fun getLastUnlockTime(): Long {
        val seedFlow: Flow<Long?> = context.dataStore.data
            .map { preferences ->
                preferences[KEY_LAST_UNLOCK_TIME]
            }
        val value = seedFlow.first()
        return value?: 0
    }

    suspend fun saveShouldLock(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOULD_LOCK] = value
        }
    }

    suspend fun getShouldLock(): Boolean {
        val seedFlow: Flow<Boolean?> = context.dataStore.data
            .map { preferences ->
                preferences[KEY_SHOULD_LOCK]
            }
        val value = seedFlow.first()
        return value?: false
    }

    suspend fun saveLastBackupAt(value: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_AT_KEY] = value
        }
    }

    suspend fun getLastBackupAt(): String? {
        val seedFlow: Flow<String?> = context.dataStore.data
            .map { preferences ->
                preferences[LAST_BACKUP_AT_KEY]
            }
        val value = seedFlow.first()
        return value
    }

    suspend fun saveSeed(value: String) {
        context.dataStore.edit { preferences ->
            preferences[SEED_KEY] = EncryptionHelper.encryptStringData(value)
        }
    }

    suspend fun getSeed(fragment: Fragment): Either<Int, String?> {
        val seedFlow: Flow<String?> = context.dataStore.data
            .map { preferences ->
                preferences[SEED_KEY]
            }
        val value = seedFlow.first()
        if (value.isNullOrEmpty()) {
            return Either.Right(value)
        } else {
            val biometricStatus =
                BiometricUtil.checkBiometricAvailability(fragment.requireContext())
            if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                return if (authenticateUser(fragment)) Either.Right(
                    EncryptionHelper.decryptStringData(
                        value
                    )
                ) else Either.Left(
                    biometricStatus
                )
            }
            return Either.Left(biometricStatus)
        }
    }

    private suspend fun authenticateUser(fragment: Fragment): Boolean =
        suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(fragment, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // 認証に失敗した場合の処理
                        Log.e("BiometricAuth", "Error code: $errorCode, Error message: $errString")
                        if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                            Toast.makeText(
                                context,
                                "Too many attempts. Use screen lock instead",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        continuation.resume(false)
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_prompt_title))
                .setSubtitle(context.getString(R.string.biometric_prompt_sub_title))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            // 生体認証のプロンプトを表示
            biometricPrompt.authenticate(promptInfo)
        }
}