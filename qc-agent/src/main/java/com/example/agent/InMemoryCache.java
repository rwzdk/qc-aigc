package com.example.agent;

import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCache implements CacheStore {
  private final ConcurrentHashMap<String, AgentResult> store = new ConcurrentHashMap<>();

  @Override
  public AgentResult get(String key) {
    return store.get(key);
  }

  @Override
  public void put(String key, AgentResult result) {
    store.put(key, result);
  }
}
