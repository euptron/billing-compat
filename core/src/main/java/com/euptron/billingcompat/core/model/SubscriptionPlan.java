package com.euptron.billingcompat.core.model;

public enum SubscriptionPlan {
  WEEKLY,
  MONTHLY,
  QUARTERLY,
  YEARLY;

  private static String weeklyId    = "subs_weekly";    // sensible defaults
  private static String monthlyId   = "subs_monthly";
  private static String quarterlyId = "subs_quarterly";
  private static String yearlyId    = "subs_yearly";

  private static int weeklyDays    = 7;
  private static int monthlyDays   = 30;
  private static int quarterlyDays = 90;
  private static int yearlyDays    = 365;

  /**
   * Call this once in Application.onCreate() or before BillingConfigBuilder.build().
   * Any IDs you don't supply keep their defaults.
   */
  public static void configure(Config config) {
    if (config.weeklyId    != null) weeklyId    = config.weeklyId;
    if (config.monthlyId   != null) monthlyId   = config.monthlyId;
    if (config.quarterlyId != null) quarterlyId = config.quarterlyId;
    if (config.yearlyId    != null) yearlyId    = config.yearlyId;

    if (config.weeklyDays    > 0) weeklyDays    = config.weeklyDays;
    if (config.monthlyDays   > 0) monthlyDays   = config.monthlyDays;
    if (config.quarterlyDays > 0) quarterlyDays = config.quarterlyDays;
    if (config.yearlyDays    > 0) yearlyDays    = config.yearlyDays;
  }

  public String getProductId() {
    switch (this) {
      case WEEKLY:    return weeklyId;
      case MONTHLY:   return monthlyId;
      case QUARTERLY: return quarterlyId;
      case YEARLY:    return yearlyId;
      default:        return monthlyId;
    }
  }

  public int getDays() {
    switch (this) {
      case WEEKLY:    return weeklyDays;
      case MONTHLY:   return monthlyDays;
      case QUARTERLY: return quarterlyDays;
      case YEARLY:    return yearlyDays;
      default:        return monthlyDays;
    }
  }

  public String getPeriod() {
    switch (this) {
      case WEEKLY:    return "week";
      case MONTHLY:   return "month";
      case QUARTERLY: return "quarter";
      case YEARLY:    return "year";
      default:        return "month";
    }
  }

  public static SubscriptionPlan fromProductId(String productId) {
    if (productId == null) return null;
    if (productId.equals(weeklyId))    return WEEKLY;
    if (productId.equals(monthlyId))   return MONTHLY;
    if (productId.equals(quarterlyId)) return QUARTERLY;
    if (productId.equals(yearlyId))    return YEARLY;
    return null;
  }

  public static class Config {
    String weeklyId;
    String monthlyId;
    String quarterlyId;
    String yearlyId;

    int weeklyDays;
    int monthlyDays;
    int quarterlyDays;
    int yearlyDays;

    public Config weekly(String productId) {
      this.weeklyId = productId; return this;
    }

    public Config monthly(String productId) {
      this.monthlyId = productId; return this;
    }

    public Config quarterly(String productId) {
      this.quarterlyId = productId; return this;
    }

    public Config yearly(String productId) {
      this.yearlyId = productId; return this;
    }

    // Only needed if your billing periods differ from the defaults
    public Config weeklyDays(int days) {
      this.weeklyDays = days; return this;
    }

    public Config monthlyDays(int days) {
      this.monthlyDays = days; return this;
    }

    public Config quarterlyDays(int days) {
      this.quarterlyDays = days; return this;
    }

    public Config yearlyDays(int days) {
      this.yearlyDays = days; return this;
    }
  }
}