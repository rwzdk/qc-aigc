package com.example.agent;

public interface AuditLog {
  void record(AgentContext ctx, AgentResult result);
}
