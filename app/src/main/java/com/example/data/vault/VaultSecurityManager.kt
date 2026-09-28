package com.example.data.vault

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinHashPair(
    val hashBase64: String,
    val saltBase64: String
)

data class PinVerificationResult(
    val isSuccess: Boolean,
    val isLockedOut: Boolean = false,
    val remainingLockoutSeconds: Int = 0,
    val failedAttempts: Int = 0,
    val upgradedCredentials: PinHashPair? = null
)

/**
 * Handles secure PIN hashing (PBKDF2WithHmacSHA256 + random salt),
 * brute-force rate limiting (cooldown after 5 failed attempts),
 * and real BiometricPrompt authentication.
 */
object VaultSecurityManager {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 32_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L

    private val secureRandom = SecureRandom()

    @Volatile
    private var failedAttemptsCount: Int = 0

    @Volatile
    private var lockoutUntilTimestampMs: Long = 0L

    /**
     * Generates a random 16-byte salt and derives a 256-bit PBKDF2 hash for the given PIN.
     */
    fun createPinCredentials(pin: String): PinHashPair {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = derivePbkdf2Hash(pin, salt)
        return PinHashPair(hashBase64 = hashBase64, saltBase64 = saltBase64)
    }

    /**
     * Derives PBKDF2-HMAC-SHA256 hash using the provided salt.
     */
    fun derivePbkdf2Hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val hashBytes = skf.generateSecret(spec).encoded
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Verifies a PIN against stored hash and salt, enforcing lockout cooldown after [MAX_FAILED_ATTEMPTS].
     * Also supports transparent migration from legacy unsalted SHA-256 hashes when [storedSalt] is empty.
     */
    fun verifyPin(
        inputPin: String,
        storedHash: String,
        storedSalt: String,
        nowMs: Long = System.currentTimeMillis()
    ): PinVerificationResult {
        if (storedHash.isEmpty()) {
            return PinVerificationResult(isSuccess = false)
        }

        // Check active lockout
        if (nowMs < lockoutUntilTimestampMs) {
            val remainingSec = ((lockoutUntilTimestampMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(1)
            return PinVerificationResult(
                isSuccess = false,
                isLockedOut = true,
                remainingLockoutSeconds = remainingSec,
                failedAttempts = failedAttemptsCount
            )
        }

        val isMatch: Boolean
        var upgraded: PinHashPair? = null

        if (storedSalt.isNotEmpty()) {
            val saltBytes = try {
                Base64.decode(storedSalt, Base64.NO_WRAP)
            } catch (_: Exception) {
                storedSalt.toByteArray(Charsets.UTF_8)
            }
            val candidateHash = derivePbkdf2Hash(inputPin, saltBytes)
            isMatch = MessageDigest.isEqual(
                candidateHash.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        } else {
            // Legacy unsalted SHA-256 check -> upgrade to PBKDF2 on match
            val legacyCandidate = legacySha256Hex(inputPin)
            isMatch = MessageDigest.isEqual(
                legacyCandidate.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
            if (isMatch) {
                upgraded = createPinCredentials(inputPin)
            }
        }

        if (isMatch) {
            resetLockoutState()
            return PinVerificationResult(
                isSuccess = true,
                isLockedOut = false,
                remainingLockoutSeconds = 0,
                failedAttempts = 0,
                upgradedCredentials = upgraded
            )
        } else {
            failedAttemptsCount++
            if (failedAttemptsCount >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilTimestampMs = nowMs + LOCKOUT_DURATION_MS
                val remainingSec = (LOCKOUT_DURATION_MS / 1000L).toInt()
                return PinVerificationResult(
                    isSuccess = false,
                    isLockedOut = true,
                    remainingLockoutSeconds = remainingSec,
                    failedAttempts = failedAttemptsCount
                )
            }
            return PinVerificationResult(
                isSuccess = false,
                isLockedOut = false,
                remainingLockoutSeconds = 0,
                failedAttempts = failedAttemptsCount
            )
        }
    }

    fun getRemainingLockoutSeconds(nowMs: Long = System.currentTimeMillis()): Int {
        if (nowMs >= lockoutUntilTimestampMs) return 0
        return ((lockoutUntilTimestampMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    fun getFailedAttempts(): Int = failedAttemptsCount

    fun resetLockoutState() {
        failedAttemptsCount = 0
        lockoutUntilTimestampMs = 0L
    }

    private fun legacySha256Hex(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks whether the device has biometric hardware and enrolled biometrics.
     */
    fun canUseBiometrics(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the system BiometricPrompt for unlocking the Vault.
     */
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!canUseBiometrics(activity)) {
            onError("La autenticación biométrica no está disponible o configurada en este dispositivo.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                resetLockoutState()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometría no reconocida. Intenta de nuevo o usa tu PIN.")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Bóveda Privada")
            .setSubtitle("Usa tu huella o rostro para acceder a tus archivos cifrados")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
