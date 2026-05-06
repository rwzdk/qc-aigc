package com.example.agent.strategy;

import com.example.agent.AgentContext;
import java.util.List;
import java.util.Set;

public final class FixedRouteStrategy implements Strategy {
  private final String id;
  private final Set<String> scenes;
  private final List<String> agents;

  public FixedRouteStrategy(String id, Set<String> scenes, List<String> agents) {
    this.id = id;
    this.scenes = Set.copyOf(scenes);
    this.agents = List.copyOf(agents);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public boolean supports(String scene) {
    return scenes.contains(scene);
  }

  @Override
  public List<String> agentIds(AgentContext ctx) {
    return agents;
  }
}
