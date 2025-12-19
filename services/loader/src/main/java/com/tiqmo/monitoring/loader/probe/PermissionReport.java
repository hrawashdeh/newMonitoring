// src/main/java/com/tiqmo/monitoring/loader/probe/PermissionReport.java
package com.tiqmo.monitoring.loader.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PermissionReport {
  private final String sourceCode;
  private final boolean readOnly;
  private final List<String> violations;

  public PermissionReport(String sourceCode, boolean readOnly, List<String> violations) {
    this.sourceCode = sourceCode;
    this.readOnly = readOnly;
    this.violations = violations == null ? List.of() : List.copyOf(violations);
  }

  // Explicit getters for final fields
  public String getSourceCode() {
    return sourceCode;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public List<String> getViolations() {
    return violations;
  }

  public static Builder builder(String sourceCode) { return new Builder(sourceCode); }
  public static final class Builder {
    private final String sourceCode;
    private final List<String> violations = new ArrayList<>();
    public Builder(String sourceCode) { this.sourceCode = sourceCode; }
    public Builder addViolation(String v) { if (v != null && !v.isBlank()) violations.add(v); return this; }
    public PermissionReport build() {
      return new PermissionReport(sourceCode, violations.isEmpty(), Collections.unmodifiableList(violations));
    }
  }
}
