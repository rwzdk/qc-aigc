package com.example.agent.strategy;

import com.example.agent.AgentContext;
import java.util.List;

public final class FallbackStrategy implements Strategy {
  private final String id;
  private final List<String> agents;

  public FallbackStrategy(String id, List<String> agents) {
    this.id = id;
    this.agents = List.copyOf(agents);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public boolean supports(String scene) {
    return true;
  }

  @Override
  public List<String> agentIds(AgentContext ctx) {
    return agents;
  }
}
