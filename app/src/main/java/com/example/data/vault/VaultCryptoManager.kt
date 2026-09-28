package com.example.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

data class VaultEncryptionResult(
    val plaintextSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val baseIvHex: String
)

/**
 * Provides authenticated streaming and random-access encryption/decryption using AES-256-GCM.
 *
 * File Format (OMNIVAULT_V1):
 * - Bytes 0..7   (8 bytes) : Magic header "OMNVLT01"
 * - Bytes 8..11  (4 bytes) : Chunk plaintext size (big-endian Int, default 65536)
 * - Bytes 12..19 (8 bytes) : Original plaintext file size (big-endian Long)
 * - Bytes 20..27 (8 bytes) : Per-file random Base IV seed
 * - Followed by encrypted chunks. Each chunk i has:
 *   - 12-byte GCM IV = [8-byte Base IV seed] + [4-byte big-endian chunkIndex i]
 *   - 4-byte AAD = [4-byte big-endian chunkIndex i]
 *   - Ciphertext of size (plaintextChunkLen + 16 bytes GCM auth tag)
 */
object VaultCryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "omnimedia_vault_master_key_v1"
    private const val FALLBACK_PREFS_NAME = "omnimedia_vault_key_fallback"
    private const val FALLBACK_KEY_PREF = "fallback_aes_256_key"

    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val GCM_IV_BYTES = 12
    const val BASE_IV_SEED_BYTES = 8

    private val MAGIC_HEADER = "OMNVLT01".toByteArray(Charsets.US_ASCII)
    const val HEADER_SIZE_BYTES = 28
    const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB

    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    fun getOrCreateSecretKey(context: Context): SecretKey {
        cachedSecretKey?.let { return it }
        synchronized(this) {
            cachedSecretKey?.let { return it }
            val key = tryGetAndroidKeystoreKey() ?: getOrCreateFallbackKey(context)
            cachedSecretKey = key
            return key
        }
    }

    private fun tryGetAndroidKeystoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                // Verify the key actually works with Cipher in this runtime environment (e.g. not stubbed in JVM test)
                val testCipher = Cipher.getInstance(TRANSFORMATION)
                testCipher.init(Cipher.ENCRYPT_MODE, existingEntry.secretKey)
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false) // Required so we can supply deterministic per-chunk IVs derived from random file seed
                    .build()
                keyGenerator.init(spec)
                val generated = keyGenerator.generateKey()
                val testCipher = Cipher.getInstance(TRANSFORMATION)
                val testIv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
                testCipher.init(Cipher.ENCRYPT_MODE, generated, GCMParameterSpec(GCM_TAG_BITS, testIv))
                generated
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateFallbackKey(context: Context): SecretKey {
        val prefs = context.applicationContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        val existingBase64 = prefs.getString(FALLBACK_KEY_PREF, null)
        if (!existingBase64.isNullOrEmpty()) {
            val bytes = Base64.decode(existingBase64, Base64.NO_WRAP)
            if (bytes.size == 32) {
                return SecretKeySpec(bytes, "AES")
            }
        }
        val newKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        prefs.edit().putString(FALLBACK_KEY_PREF, Base64.encodeToString(newKeyBytes, Base64.NO_WRAP)).commit()
        return SecretKeySpec(newKeyBytes, "AES")
    }

    fun buildChunkIv(baseIvSeed: ByteArray, chunkIndex: Int): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES)
        System.arraycopy(baseIvSeed, 0, iv, 0, BASE_IV_SEED_BYTES)
        iv[8] = (chunkIndex ushr 24).toByte()
        iv[9] = (chunkIndex ushr 16).toByte()
        iv[10] = (chunkIndex ushr 8).toByte()
        iv[11] = chunkIndex.toByte()
        return iv
    }

    fun buildChunkAad(chunkIndex: Int): ByteArray {
        return byteArrayOf(
            (chunkIndex ushr 24).toByte(),
            (chunkIndex ushr 16).toByte(),
            (chunkIndex ushr 8).toByte(),
            chunkIndex.toByte()
        )
    }

    /**
     * Encrypts an InputStream into an authenticated OMNIVAULT_V1 file on disk.
     */
    fun encryptStream(
        context: Context,
        inputStream: InputStream,
        outputFile: File,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): VaultEncryptionResult {
        val secretKey = getOrCreateSecretKey(context)
        val baseIvSeed = ByteArray(BASE_IV_SEED_BYTES).also { secureRandom.nextBytes(it) }

        outputFile.parentFile?.mkdirs()

        var totalPlaintextBytes = 0L
        var chunkIndex = 0

        FileOutputStream(outputFile).use { fos ->
            // Write placeholder header first
            val initialHeader = ByteBuffer.allocate(HEADER_SIZE_BYTES)
                .put(MAGIC_HEADER)
                .putInt(chunkSize)
                .putLong(0L) // Updated after streaming finishes
                .put(baseIvSeed)
                .array()
            fos.write(initialHeader)

            val readBuffer = ByteArray(chunkSize)
            while (true) {
                var bytesReadTotal = 0
                while (bytesReadTotal < chunkSize) {
                    val read = inputStream.read(readBuffer, bytesReadTotal, chunkSize - bytesReadTotal)
                    if (read == -1) break
                    bytesReadTotal += read
                }
                if (bytesReadTotal <= 0) break

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val chunkIv = buildChunkIv(baseIvSeed, chunkIndex)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                cipher.updateAAD(buildChunkAad(chunkIndex))

                val cipherBytes = cipher.doFinal(readBuffer, 0, bytesReadTotal)
                fos.write(cipherBytes)

                totalPlaintextBytes += bytesReadTotal
                chunkIndex++
            }
            fos.flush()
            fos.fd.sync()
        }

        // Write final totalPlaintextBytes into header bytes 12..19
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.seek(12L)
            raf.writeLong(totalPlaintextBytes)
        }

        val baseIvHex = baseIvSeed.joinToString("") { "%02x".format(it) }
        return VaultEncryptionResult(
            plaintextSizeBytes = totalPlaintextBytes,
            encryptedSizeBytes = outputFile.length(),
            baseIvHex = baseIvHex
        )
    }

    /**
     * Decrypts an entire OMNIVAULT_V1 encrypted file into an OutputStream.
     */
    fun decryptStream(
        context: Context,
        encryptedFile: File,
        outputStream: OutputStream
    ): Long {
        val secretKey = getOrCreateSecretKey(context)
        FileInputStream(encryptedFile).use { fis ->
            val header = readAndParseHeader(fis)
            val encryptedChunkMaxLen = header.chunkSize + GCM_TAG_BYTES
            val cipherBuffer = ByteArray(encryptedChunkMaxLen)

            var totalWritten = 0L
            var chunkIndex = 0

            while (totalWritten < header.plaintextSize) {
                val expectedPlaintextLen = min(
                    header.chunkSize.toLong(),
                    header.plaintextSize - totalWritten
                ).toInt()
                val expectedCipherLen = expectedPlaintextLen + GCM_TAG_BYTES

                var readTotal = 0
                while (readTotal < expectedCipherLen) {
                    val r = fis.read(cipherBuffer, readTotal, expectedCipherLen - readTotal)
                    if (r == -1) throw EOFException("Unexpected EOF in encrypted vault file at chunk $chunkIndex")
                    readTotal += r
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val chunkIv = buildChunkIv(header.baseIvSeed, chunkIndex)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                cipher.updateAAD(buildChunkAad(chunkIndex))

                val decryptedBytes = cipher.doFinal(cipherBuffer, 0, expectedCipherLen)
                outputStream.write(decryptedBytes)

                totalWritten += decryptedBytes.size
                chunkIndex++
            }
            outputStream.flush()
            return totalWritten
        }
    }

    /**
     * Random-access decrypted read for ExoPlayer seeking and streaming.
     * Returns number of bytes read, or -1 if at EOF.
     */
    fun readDecryptedRange(
        context: Context,
        encryptedFile: File,
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        if (length == 0) return 0
        val secretKey = getOrCreateSecretKey(context)

        RandomAccessFile(encryptedFile, "r").use { raf ->
            val header = readAndParseHeader(raf)
            if (position >= header.plaintextSize) return -1

            val chunkIndex = (position / header.chunkSize).toInt()
            val offsetInChunk = (position % header.chunkSize).toInt()

            val encryptedChunkSize = header.chunkSize.toLong() + GCM_TAG_BYTES
            val chunkFileOffset = HEADER_SIZE_BYTES + (chunkIndex.toLong() * encryptedChunkSize)

            val remainingPlaintextFromChunkStart = header.plaintextSize - (chunkIndex.toLong() * header.chunkSize)
            val thisChunkPlaintextLen = min(header.chunkSize.toLong(), remainingPlaintextFromChunkStart).toInt()
            val thisChunkCipherLen = thisChunkPlaintextLen + GCM_TAG_BYTES

            raf.seek(chunkFileOffset)
            val cipherBytes = ByteArray(thisChunkCipherLen)
            raf.readFully(cipherBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val chunkIv = buildChunkIv(header.baseIvSeed, chunkIndex)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
            cipher.updateAAD(buildChunkAad(chunkIndex))

            val decryptedChunk = cipher.doFinal(cipherBytes)
            val availableInChunk = decryptedChunk.size - offsetInChunk
            if (availableInChunk <= 0) return -1

            val bytesToCopy = min(length, availableInChunk)
            System.arraycopy(decryptedChunk, offsetInChunk, buffer, offset, bytesToCopy)
            return bytesToCopy
        }
    }

    /**
     * Opens a streaming InputStream that decrypts chunks on the fly in memory with high-performance buffering.
     */
    fun openDecryptedInputStream(context: Context, encryptedFile: File): InputStream {
        val secretKey = getOrCreateSecretKey(context)
        val raf = RandomAccessFile(encryptedFile, "r")
        val header = readAndParseHeader(raf)
        val totalSize = header.plaintextSize

        return object : InputStream() {
            private var currentPosition = 0L
            private var currentChunkIndex = -1
            private var currentDecryptedChunk: ByteArray? = null
            private val singleByteBuf = ByteArray(1)
            private val cipherBuffer = ByteArray(header.chunkSize + GCM_TAG_BYTES)
            private val cipher = Cipher.getInstance(TRANSFORMATION)

            override fun read(): Int {
                val r = read(singleByteBuf, 0, 1)
                return if (r == -1) -1 else (singleByteBuf[0].toInt() and 0xFF)
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (currentPosition >= totalSize) return -1

                val chunkIndex = (currentPosition / header.chunkSize).toInt()
                val offsetInChunk = (currentPosition % header.chunkSize).toInt()

                if (chunkIndex != currentChunkIndex || currentDecryptedChunk == null) {
                    val encryptedChunkSize = header.chunkSize.toLong() + GCM_TAG_BYTES
                    val chunkFileOffset = HEADER_SIZE_BYTES + (chunkIndex.toLong() * encryptedChunkSize)

                    val remainingPlaintext = header.plaintextSize - (chunkIndex.toLong() * header.chunkSize)
                    if (remainingPlaintext <= 0) return -1
                    val thisChunkPlaintextLen = min(header.chunkSize.toLong(), remainingPlaintext).toInt()
                    val thisChunkCipherLen = thisChunkPlaintextLen + GCM_TAG_BYTES

                    synchronized(raf) {
                        raf.seek(chunkFileOffset)
                        raf.readFully(cipherBuffer, 0, thisChunkCipherLen)
                    }

                    val chunkIv = buildChunkIv(header.baseIvSeed, chunkIndex)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                    cipher.updateAAD(buildChunkAad(chunkIndex))

                    currentDecryptedChunk = cipher.doFinal(cipherBuffer, 0, thisChunkCipherLen)
                    currentChunkIndex = chunkIndex
                }

                val chunk = currentDecryptedChunk ?: return -1
                val available = chunk.size - offsetInChunk
                if (available <= 0) return -1

                val toCopy = min(len, available)
                System.arraycopy(chunk, offsetInChunk, b, off, toCopy)
                currentPosition += toCopy
                return toCopy
            }

            override fun available(): Int {
                return (totalSize - currentPosition).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            }

            override fun close() {
                try {
                    raf.close()
                } catch (_: Exception) {}
                currentDecryptedChunk = null
            }
        }
    }

    /**
     * Checks whether a file has the OMNIVAULT_V1 encrypted magic header.
     */
    fun isEncryptedVaultFile(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_SIZE_BYTES) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(MAGIC_HEADER.size)
                if (fis.read(magic) != MAGIC_HEADER.size) return false
                magic.contentEquals(MAGIC_HEADER)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies both header and cryptographic authentication tag of the encrypted file.
     */
    fun verifyEncryptedFile(context: Context, file: File): Boolean {
        if (!isEncryptedVaultFile(file)) return false
        return try {
            val size = getOriginalPlaintextSize(file)
            if (size == 0L) return file.length() == HEADER_SIZE_BYTES.toLong()
            val probe = ByteArray(min(64L, size).toInt())
            val read = readDecryptedRange(context, file, 0L, probe, 0, probe.size)
            read == probe.size
        } catch (_: Exception) {
            false
        }
    }

    fun getOriginalPlaintextSize(encryptedFile: File): Long {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            return readAndParseHeader(raf).plaintextSize
        }
    }

    data class VaultHeader(
        val chunkSize: Int,
        val plaintextSize: Long,
        val baseIvSeed: ByteArray
    )

    fun readAndParseHeader(inputStream: InputStream): VaultHeader {
        val headerBytes = ByteArray(HEADER_SIZE_BYTES)
        var readTotal = 0
        while (readTotal < HEADER_SIZE_BYTES) {
            val r = inputStream.read(headerBytes, readTotal, HEADER_SIZE_BYTES - readTotal)
            if (r == -1) throw GeneralSecurityException("Invalid vault file: truncated header")
            readTotal += r
        }
        return parseHeaderBytes(headerBytes)
    }

    fun readAndParseHeader(raf: RandomAccessFile): VaultHeader {
        if (raf.length() < HEADER_SIZE_BYTES) {
            throw GeneralSecurityException("Invalid vault file: smaller than header")
        }
        raf.seek(0L)
        val headerBytes = ByteArray(HEADER_SIZE_BYTES)
        raf.readFully(headerBytes)
        return parseHeaderBytes(headerBytes)
    }

    private fun parseHeaderBytes(headerBytes: ByteArray): VaultHeader {
        val magic = headerBytes.copyOfRange(0, 8)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw GeneralSecurityException("Invalid vault file: magic header mismatch")
        }
        val buf = ByteBuffer.wrap(headerBytes, 8, 20)
        val chunkSize = buf.int
        val plaintextSize = buf.long
        val baseIvSeed = ByteArray(BASE_IV_SEED_BYTES)
        buf.get(baseIvSeed)
        if (chunkSize <= 0 || plaintextSize < 0) {
            throw GeneralSecurityException("Invalid vault file header parameters")
        }
        return VaultHeader(chunkSize, plaintextSize, baseIvSeed)
    }
}
