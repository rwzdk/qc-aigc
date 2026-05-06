package com.example.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AgentContext {
  private final String scene;
  private final String input;
  private final Map<String, Object> attrs;

  public AgentContext(String scene, String input, Map<String, Object> attrs) {
    this.scene = Objects.requireNonNull(scene, "scene");
    this.input = Objects.requireNonNull(input, "input");
    Map<String, Object> copy = new HashMap<>();
    if (attrs != null) {
      copy.putAll(attrs);
    }
    this.attrs = Collections.unmodifiableMap(copy);
  }

  public String scene() {
    return scene;
  }

  public String input() {
    return input;
  }

  public Map<String, Object> attrs() {
    return attrs;
  }
}
