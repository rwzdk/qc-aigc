package com.example.agent;

import com.example.agent.strategy.FixedRouteStrategy;
import com.example.agent.strategy.FallbackStrategy;
import com.example.agent.strategy.StrategyFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DemoApp {
  public static void main(String[] args) {
    PluginRegistry registry = PluginRegistry.load();
    StrategyFactory strategyFactory = new StrategyFactory(List.of(
        new FixedRouteStrategy("analysis-route", Set.of("analysis"), List.of("summary", "audit")),
        new FixedRouteStrategy("chat-route", Set.of("chat"), List.of("echo")),
        new FallbackStrategy("fallback", List.of("echo"))
    ));

    AgentPipeline pipeline = new AgentPipeline(
        registry, strategyFactory, new InMemoryCache(), new ConsoleAuditLog()
    );

    AgentContext ctx = new AgentContext(
        "analysis",
        "This is a long text that needs to be summarized and audited by agents.",
        Map.of("user", "u1")
    );

    AgentResult result = pipeline.run(ctx);
    System.out.println(result.content());
  }
}
