package com.example.agent.strategy;

import com.example.agent.AgentContext;
import java.util.List;

public interface Strategy {
  String id();
  boolean supports(String scene);
  List<String> agentIds(AgentContext ctx);
}
