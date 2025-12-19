package com.tiqmo.monitoring.loader.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptionService (AES-256-GCM encryption).
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
class EncryptionServiceTest {

  private EncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    // Generate a test 256-bit key (32 bytes)
    String testKey = Base64.getEncoder().encodeToString(new byte[32]); // All zeros for testing
    encryptionService = new EncryptionService(testKey);
  }

  @Test
  void testEncryptDecrypt_BasicString() {
    String original = "MySecretPassword123!";

    String encrypted = encryptionService.encrypt(original);
    assertNotNull(encrypted);
    assertNotEquals(original, encrypted);

    String decrypted = encryptionService.decrypt(encrypted);
    assertEquals(original, decrypted);
  }

  @Test
  void testEncryptDecrypt_ArabicCharacters() {
    String original = "كلمة المرور السرية";

    String encrypted = encryptionService.encrypt(original);
    assertNotNull(encrypted);
    assertNotEquals(original, encrypted);

    String decrypted = encryptionService.decrypt(encrypted);
    assertEquals(original, decrypted, "Arabic characters should be preserved");
  }

  @Test
  void testEncryptDecrypt_MixedLanguages() {
    String original = "Password كلمة密码@123";

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "Mixed language text should be preserved");
  }

  @Test
  void testEncryptDecrypt_LongSQL() {
    String original = "SELECT * FROM users WHERE name = 'محمد' AND city = 'الرياض' ORDER BY created_at DESC LIMIT 100";

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "Long SQL with Arabic should be preserved");
  }

  @Test
  void testEncryptDecrypt_SpecialCharacters() {
    String original = "P@$$w0rd!#%^&*()[]{}|<>?/\\~`";

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "Special characters should be preserved");
  }

  @Test
  void testEncryptDecrypt_NullValue() {
    String encrypted = encryptionService.encrypt(null);
    assertNull(encrypted, "Encrypting null should return null");

    String decrypted = encryptionService.decrypt(null);
    assertNull(decrypted, "Decrypting null should return null");
  }

  @Test
  void testEncryptDecrypt_EmptyString() {
    String original = "";

    String encrypted = encryptionService.encrypt(original);
    assertNotNull(encrypted);

    String decrypted = encryptionService.decrypt(encrypted);
    assertEquals(original, decrypted, "Empty string should be preserved");
  }

  @Test
  void testEncrypt_ProducesUniqueOutput() {
    String original = "SamePassword";

    String encrypted1 = encryptionService.encrypt(original);
    String encrypted2 = encryptionService.encrypt(original);

    assertNotEquals(encrypted1, encrypted2,
      "Same plaintext should produce different ciphertext (non-deterministic encryption due to random IV)");

    // Both should decrypt to same original
    assertEquals(original, encryptionService.decrypt(encrypted1));
    assertEquals(original, encryptionService.decrypt(encrypted2));
  }

  @Test
  void testDecrypt_InvalidCiphertext() {
    assertThrows(EncryptionException.class, () -> {
      encryptionService.decrypt("InvalidBase64Ciphertext");
    }, "Decrypting invalid ciphertext should throw EncryptionException");
  }

  @Test
  void testDecrypt_WrongKey() {
    // Encrypt with one key
    String original = "SecretData";
    String encrypted = encryptionService.encrypt(original);

    // Try to decrypt with different key
    String differentKey = Base64.getEncoder().encodeToString(new byte[]{
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
      17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
    });
    EncryptionService differentService = new EncryptionService(differentKey);

    assertThrows(EncryptionException.class, () -> {
      differentService.decrypt(encrypted);
    }, "Decrypting with wrong key should throw EncryptionException");
  }

  @Test
  void testIsEncrypted_ValidEncryptedData() {
    String original = "TestData";
    String encrypted = encryptionService.encrypt(original);

    assertTrue(encryptionService.isEncrypted(encrypted),
      "Valid encrypted data should be detected");
  }

  @Test
  void testIsEncrypted_PlaintextData() {
    String plaintext = "PlainPassword123";

    assertFalse(encryptionService.isEncrypted(plaintext),
      "Plaintext should not be detected as encrypted");
  }

  @Test
  void testIsEncrypted_NullOrEmpty() {
    assertFalse(encryptionService.isEncrypted(null), "Null should not be encrypted");
    assertFalse(encryptionService.isEncrypted(""), "Empty string should not be encrypted");
  }

  @Test
  void testConstructor_MissingKey() {
    assertThrows(EncryptionException.class, () -> {
      new EncryptionService(null);
    }, "Missing encryption key should throw EncryptionException");

    assertThrows(EncryptionException.class, () -> {
      new EncryptionService("");
    }, "Empty encryption key should throw EncryptionException");
  }

  @Test
  void testConstructor_InvalidKeySize() {
    String invalidKey = Base64.getEncoder().encodeToString(new byte[16]); // Only 16 bytes (128-bit)

    assertThrows(EncryptionException.class, () -> {
      new EncryptionService(invalidKey);
    }, "Invalid key size should throw EncryptionException with helpful message");
  }

  @Test
  void testConstructor_InvalidBase64() {
    assertThrows(EncryptionException.class, () -> {
      new EncryptionService("NotValidBase64!");
    }, "Invalid Base64 key should throw EncryptionException");
  }

  @Test
  void testEncryptedDataFormat() {
    String original = "Test";
    String encrypted = encryptionService.encrypt(original);

    // Encrypted data should be Base64
    assertDoesNotThrow(() -> {
      Base64.getDecoder().decode(encrypted);
    }, "Encrypted data should be valid Base64");

    // Decoded data should be at least IV (12) + minimal ciphertext + auth tag (16)
    byte[] decoded = Base64.getDecoder().decode(encrypted);
    assertTrue(decoded.length >= 12 + 16,
      "Encrypted data should include IV (12 bytes) + ciphertext + auth tag (16 bytes)");
  }

  @Test
  void testEncryptDecrypt_Unicode() {
    String original = "Hello 世界 مرحبا 🌍 Привет";

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "All Unicode characters should be preserved");
  }

  @Test
  void testEncryptDecrypt_Whitespace() {
    String original = "  Leading and trailing spaces  \n\t";

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "Whitespace should be preserved exactly");
  }

  @Test
  void testEncryptDecrypt_VeryLongText() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10000; i++) {
      sb.append("Long SQL query with Arabic characters: محمد وعلي ");
    }
    String original = sb.toString();

    String encrypted = encryptionService.encrypt(original);
    String decrypted = encryptionService.decrypt(encrypted);

    assertEquals(original, decrypted, "Very long text should be encrypted and decrypted correctly");
  }
}
