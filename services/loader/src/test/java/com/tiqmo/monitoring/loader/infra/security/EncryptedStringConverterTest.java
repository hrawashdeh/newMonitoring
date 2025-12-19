package com.tiqmo.monitoring.loader.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EncryptedStringConverter} JPA AttributeConverter.
 */
class EncryptedStringConverterTest {

  @Mock
  private EncryptionService encryptionService;

  private EncryptedStringConverter converter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    converter = new EncryptedStringConverter(encryptionService);
  }

  @Test
  @DisplayName("Should encrypt plaintext when converting to database column")
  void testConvertToDatabaseColumn() {
    // Given
    String plaintext = "MyPassword123";
    String encrypted = "xK9mP2j8L+vQ4wN7tR5eA1cF6dH3gJ2kL9mN8pQ7sT4=";

    when(encryptionService.encrypt(plaintext)).thenReturn(encrypted);

    // When
    String result = converter.convertToDatabaseColumn(plaintext);

    // Then
    assertEquals(encrypted, result);
    verify(encryptionService, times(1)).encrypt(plaintext);
  }

  @Test
  @DisplayName("Should decrypt ciphertext when converting to entity attribute")
  void testConvertToEntityAttribute() {
    // Given
    String ciphertext = "xK9mP2j8L+vQ4wN7tR5eA1cF6dH3gJ2kL9mN8pQ7sT4=";
    String decrypted = "MyPassword123";

    when(encryptionService.decrypt(ciphertext)).thenReturn(decrypted);

    // When
    String result = converter.convertToEntityAttribute(ciphertext);

    // Then
    assertEquals(decrypted, result);
    verify(encryptionService, times(1)).decrypt(ciphertext);
  }

  @Test
  @DisplayName("Should handle null plaintext")
  void testConvertNullPlaintext() {
    // When
    String result = converter.convertToDatabaseColumn(null);

    // Then
    assertNull(result);
    verify(encryptionService, never()).encrypt(any());
  }

  @Test
  @DisplayName("Should handle null ciphertext")
  void testConvertNullCiphertext() {
    // When
    String result = converter.convertToEntityAttribute(null);

    // Then
    assertNull(result);
    verify(encryptionService, never()).decrypt(any());
  }

  @Test
  @DisplayName("Should propagate EncryptionException on encryption failure")
  void testEncryptionFailure() {
    // Given
    String plaintext = "Password";
    when(encryptionService.encrypt(plaintext))
      .thenThrow(new EncryptionException("Encryption failed"));

    // When/Then
    assertThrows(EncryptionException.class, () ->
      converter.convertToDatabaseColumn(plaintext)
    );
  }

  @Test
  @DisplayName("Should propagate EncryptionException on decryption failure")
  void testDecryptionFailure() {
    // Given
    String ciphertext = "invalid-ciphertext";
    when(encryptionService.decrypt(ciphertext))
      .thenThrow(new EncryptionException("Decryption failed"));

    // When/Then
    assertThrows(EncryptionException.class, () ->
      converter.convertToEntityAttribute(ciphertext)
    );
  }

  @Test
  @DisplayName("Should encrypt Arabic passwords")
  void testArabicPassword() {
    // Given
    String arabicPassword = "كلمة المرور";
    String encrypted = "encrypted-arabic-password";

    when(encryptionService.encrypt(arabicPassword)).thenReturn(encrypted);

    // When
    String result = converter.convertToDatabaseColumn(arabicPassword);

    // Then
    assertEquals(encrypted, result);
    verify(encryptionService, times(1)).encrypt(arabicPassword);
  }
}
