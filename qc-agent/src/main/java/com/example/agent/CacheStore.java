package com.example.agent;

public interface CacheStore {
  AgentResult get(String key);
  void put(String key, AgentResult result);
}
