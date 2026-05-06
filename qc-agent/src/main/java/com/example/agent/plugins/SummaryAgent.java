package com.example.agent.plugins;

import com.example.agent.AgentContext;
import com.example.agent.AgentPlugin;
import com.example.agent.AgentResult;

public final class SummaryAgent implements AgentPlugin {
  @Override
  public String id() {
    return "summary";
  }

  @Override
  public boolean supports(String scene) {
    return "analysis".equals(scene) || "summary".equals(scene);
  }

  @Override
  public AgentResult execute(AgentContext ctx) {
    String input = ctx.input();
    String content = input.length() <= 60 ? input : input.substring(0, 60) + "...";
    return AgentResult.ok("SUMMARY: " + content);
  }
}
