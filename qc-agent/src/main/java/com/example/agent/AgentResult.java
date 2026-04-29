package com.example.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AgentResult {
  private final boolean success;
  private final String content;
  private final Map<String, Object> meta;

  private AgentResult(boolean success, String content, Map<String, Object> meta) {
    this.success = success;
    this.content = content;
    this.meta = Collections.unmodifiableMap(new HashMap<>(meta));
  }

  public static AgentResult ok(String content) {
    return new AgentResult(true, content, Map.of());
  }

  public static AgentResult ok(String content, Map<String, Object> meta) {
    return new AgentResult(true, content, meta);
  }

  public static AgentResult fail(String message) {
    return new AgentResult(false, message, Map.of("error", message));
  }

  public boolean success() {
    return success;
  }

  public String content() {
    return content;
  }

  public Map<String, Object> meta() {
    return meta;
  }
}
