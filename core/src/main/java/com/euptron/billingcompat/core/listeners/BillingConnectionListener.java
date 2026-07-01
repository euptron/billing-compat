package com.euptron.billingcompat.core.listeners;

public interface BillingConnectionListener {
  void onConnected();

  void onDisconnected();

  void onConnectionError(String error);
}
