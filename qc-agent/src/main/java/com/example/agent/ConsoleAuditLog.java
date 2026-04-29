package com.example.agent;

import java.time.Instant;

public final class ConsoleAuditLog implements AuditLog {
  @Override
  public void record(AgentContext ctx, AgentResult result) {
    System.out.println("[AUDIT] " + Instant.now()
        + " scene=" + ctx.scene()
        + " success=" + result.success());
  }
}
