package com.example.agent.plugins;

import com.example.agent.AgentContext;
import com.example.agent.AgentPlugin;
import com.example.agent.AgentResult;

public final class AuditAgent implements AgentPlugin {
  @Override
  public String id() {
    return "audit";
  }

  @Override
  public boolean supports(String scene) {
    return "analysis".equals(scene) || "audit".equals(scene);
  }

  @Override
  public AgentResult execute(AgentContext ctx) {
    return AgentResult.ok("AUDIT: ok");
  }
}
