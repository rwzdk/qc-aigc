package com.example.agent;

public interface AgentPlugin {
  String id();
  boolean supports(String scene);
  AgentResult execute(AgentContext ctx);
}
