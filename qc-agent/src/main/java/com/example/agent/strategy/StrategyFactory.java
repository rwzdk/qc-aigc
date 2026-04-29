package com.example.agent.strategy;

import java.util.List;

public final class StrategyFactory {
  private final List<Strategy> strategies;

  public StrategyFactory(List<Strategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  public Strategy select(String scene) {
    for (Strategy strategy : strategies) {
      if (strategy.supports(scene)) {
        return strategy;
      }
    }
    return strategies.get(strategies.size() - 1);
  }
}
