package com.tiqmo.monitoring.initializer.infra.security;

/**
 * Custom exception for encryption/decryption failures.
 * Thrown when encryption operations fail due to missing keys, invalid data, or cipher errors.
 */
public class EncryptionException extends RuntimeException {

  public EncryptionException(String message) {
    super(message);
  }

  public EncryptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
