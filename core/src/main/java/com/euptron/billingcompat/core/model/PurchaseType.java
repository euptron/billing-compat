package com.euptron.billingcompat.core.model;

public enum PurchaseType {
  NON_CONSUMABLE, // One-time, permanent (remove_ads, premium_access)
  CONSUMABLE, // Can buy multiple times (coins, gems, energy)
  SUBSCRIPTION, // Recurring (weekly, monthly, yearly)
  PENDING // Buy now, pay later (bank transfers, etc.)
}
