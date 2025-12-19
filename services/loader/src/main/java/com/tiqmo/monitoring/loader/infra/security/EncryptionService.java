package com.tiqmo.monitoring.loader.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for database column encryption.
 *
 * <p>Provides military-grade encryption for sensitive data (passwords, SQL queries).
 * Uses AES-256 in GCM mode (Galois/Counter Mode) which provides both confidentiality
 * and authenticity (authenticated encryption).
 *
 * <p><b>Security Properties:</b>
 * <ul>
 *   <li>Algorithm: AES-256-GCM (NIST-approved, FIPS 140-2 compliant)</li>
 *   <li>Key Size: 256 bits (32 bytes)</li>
 *   <li>IV Size: 96 bits (12 bytes) - randomly generated per encryption</li>
 *   <li>Authentication Tag: 128 bits (16 bytes)</li>
 *   <li>Character Encoding: UTF-8 (supports Arabic, Chinese, emoji, etc.)</li>
 * </ul>
 *
 * <p><b>Storage Format:</b> Base64(IV || Ciphertext || Auth_Tag)
 *
 * <p><b>Key Management:</b> 256-bit key loaded from environment variable ENCRYPTION_KEY.
 * Key must be 32 bytes Base64-encoded (generated via: openssl rand -base64 32)
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
public class EncryptionService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;  // 96 bits (recommended for GCM)
  private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

  private final SecretKey secretKey;
  private final SecureRandom secureRandom;

  /**
   * Initializes encryption service with key from environment.
   *
   * @param encryptionKey Base64-encoded 256-bit key from ENCRYPTION_KEY env var
   * @throws EncryptionException if key is missing, invalid, or wrong size
   */
  public EncryptionService(@Value("${encryption.key}") String encryptionKey) {
    if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
      throw new EncryptionException(
        "Encryption key not found. Set ENCRYPTION_KEY environment variable or encryption.key property."
      );
    }

    try {
      byte[] keyBytes = Base64.getDecoder().decode(encryptionKey.trim());

      if (keyBytes.length != 32) {
        throw new EncryptionException(
          "Invalid encryption key size: " + keyBytes.length + " bytes. Expected 32 bytes (256 bits). " +
          "Generate key with: openssl rand -base64 32"
        );
      }

      this.secretKey = new SecretKeySpec(keyBytes, "AES");
      this.secureRandom = new SecureRandom();

      log.info("EncryptionService initialized with AES-256-GCM (key size: {} bits)", keyBytes.length * 8);
    } catch (IllegalArgumentException e) {
      throw new EncryptionException("Invalid Base64-encoded encryption key", e);
    }
  }

  /**
   * Encrypts plaintext using AES-256-GCM.
   *
   * <p>Generates a random IV for each encryption to ensure same plaintext
   * produces different ciphertext (non-deterministic encryption).
   *
   * <p>Supports all Unicode characters including Arabic (UTF-8 encoding).
   *
   * @param plaintext The text to encrypt (supports Arabic, emoji, etc.)
   * @return Base64-encoded string containing IV + encrypted data + auth tag
   * @throws EncryptionException if encryption fails
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }

    try {
      // Generate random IV (12 bytes for GCM)
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      // Initialize cipher for encryption
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

      // Encrypt (UTF-8 supports Arabic, Chinese, emoji, etc.)
      byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
      byte[] ciphertext = cipher.doFinal(plaintextBytes);

      // Combine IV + ciphertext (GCM auth tag is appended by cipher.doFinal)
      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      byteBuffer.put(iv);
      byteBuffer.put(ciphertext);

      // Return Base64 for database storage
      return Base64.getEncoder().encodeToString(byteBuffer.array());

    } catch (Exception e) {
      log.error("Encryption failed", e);
      throw new EncryptionException("Encryption failed", e);
    }
  }

  /**
   * Decrypts ciphertext using AES-256-GCM.
   *
   * <p>Extracts IV from the encrypted data, verifies authentication tag,
   * and decrypts to original plaintext.
   *
   * <p>Returns UTF-8 decoded string (preserves Arabic characters).
   *
   * @param ciphertext Base64-encoded string from database
   * @return Decrypted plaintext (original Unicode string)
   * @throws EncryptionException if decryption fails (wrong key, corrupted data, tampered data)
   */
  public String decrypt(String ciphertext) {
    if (ciphertext == null) {
      return null;
    }

    try {
      // Decode Base64
      byte[] encryptedData = Base64.getDecoder().decode(ciphertext);

      // Extract IV (first 12 bytes) and ciphertext (remaining bytes)
      ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);
      byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(ciphertextBytes);

      // Initialize cipher for decryption
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

      // Decrypt and verify auth tag
      byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);

      // Convert UTF-8 bytes back to String (preserves Arabic)
      return new String(plaintextBytes, StandardCharsets.UTF_8);

    } catch (Exception e) {
      log.error("Decryption failed - possible causes: wrong key, corrupted data, or tampered ciphertext", e);
      throw new EncryptionException("Decryption failed", e);
    }
  }

  /**
   * Checks if the given string appears to be encrypted (Base64 format).
   * Useful for migrating unencrypted legacy data.
   *
   * @param value The value to check
   * @return true if value appears to be Base64-encoded encrypted data
   */
  public boolean isEncrypted(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(value);
      // Encrypted data should be at least IV (12) + some ciphertext + tag (16)
      return decoded.length > GCM_IV_LENGTH + 16;
    } catch (IllegalArgumentException e) {
      return false; // Not valid Base64
    }
  }
}
