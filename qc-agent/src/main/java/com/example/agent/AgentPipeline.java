package com.example.agent;

import com.example.agent.strategy.Strategy;
import com.example.agent.strategy.StrategyFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentPipeline {
  private final PluginRegistry registry;
  private final StrategyFactory strategyFactory;
  private final CacheStore cache;
  private final AuditLog audit;

  public AgentPipeline(PluginRegistry registry, StrategyFactory strategyFactory,
                       CacheStore cache, AuditLog audit) {
    this.registry = registry;
    this.strategyFactory = strategyFactory;
    this.cache = cache;
    this.audit = audit;
  }

  public AgentResult run(AgentContext ctx) {
    String cacheKey = ctx.scene() + ":" + ctx.input().hashCode();
    AgentResult cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    Strategy strategy = strategyFactory.select(ctx.scene());
    List<String> ids = strategy.agentIds(ctx);
    StringBuilder combined = new StringBuilder();
    Map<String, Object> meta = new HashMap<>();
    int executed = 0;

    for (String id : ids) {
      AgentPlugin plugin = registry.get(id);
      if (plugin == null || !plugin.supports(ctx.scene())) {
        continue;
      }
      AgentResult result = plugin.execute(ctx);
      executed++;
      combined.append(result.content()).append("\n");
      meta.put(id, result.meta());
    }

    AgentResult finalResult = executed == 0
        ? AgentResult.fail("No agents executed for scene: " + ctx.scene())
        : AgentResult.ok(combined.toString().trim(), meta);

    cache.put(cacheKey, finalResult);
    audit.record(ctx, finalResult);
    return finalResult;
  }
}
