package com.euptron.billingcompat.core.utils;

import java.util.Map;

public class Compatibility {

  public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
    V value = map.get(key);
    return value != null ? value : defaultValue;
  }
}
