package com.example.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class PluginRegistry {
  private final Map<String, AgentPlugin> plugins;

  private PluginRegistry(Map<String, AgentPlugin> plugins) {
    this.plugins = Map.copyOf(plugins);
  }

  public static PluginRegistry load() {
    Map<String, AgentPlugin> map = new HashMap<>();
    ServiceLoader<AgentPlugin> loader = ServiceLoader.load(AgentPlugin.class);
    for (AgentPlugin plugin : loader) {
      map.put(plugin.id(), plugin);
    }
    return new PluginRegistry(map);
  }

  public AgentPlugin get(String id) {
    return plugins.get(id);
  }

  public Map<String, AgentPlugin> all() {
    return plugins;
  }
}
