package com.example.agent.plugins;

import com.example.agent.AgentContext;
import com.example.agent.AgentPlugin;
import com.example.agent.AgentResult;

public final class EchoAgent implements AgentPlugin {
  @Override
  public String id() {
    return "echo";
  }

  @Override
  public boolean supports(String scene) {
    return "chat".equals(scene) || "echo".equals(scene);
  }

  @Override
  public AgentResult execute(AgentContext ctx) {
    return AgentResult.ok("ECHO: " + ctx.input());
  }
}
