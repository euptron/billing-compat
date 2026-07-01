package com.euptron.billingcompat.core.listeners;

import com.euptron.billingcompat.core.model.SubscriptionPlan;

public interface SubscriptionListener {
  void onSubscriptionActivated(SubscriptionPlan plan);

  void onSubscriptionExpired(SubscriptionPlan plan);

  void onSubscriptionRenewed(SubscriptionPlan plan);

  void onSubscriptionCanceled(SubscriptionPlan plan);
}
