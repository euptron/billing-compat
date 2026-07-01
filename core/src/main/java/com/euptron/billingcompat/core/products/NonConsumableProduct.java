package com.euptron.billingcompat.core.products;

import com.euptron.billingcompat.core.model.PurchaseType;

public class NonConsumableProduct implements Purchasable {
  private final String id;
  private final String name;
  private final String description;
  private final double price;
  private final String currency;
  private final String stockKeepingUnit;

  private NonConsumableProduct(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.description = builder.description;
    this.price = builder.price;
    this.currency = builder.currency;
    this.stockKeepingUnit = builder.stockKeepingUnit != null ? builder.stockKeepingUnit : id;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public double getPrice() {
    return price;
  }

  @Override
  public String getCurrency() {
    return currency;
  }

  @Override
  public PurchaseType getType() {
    return PurchaseType.NON_CONSUMABLE;
  }

  @Override
  public String getStockKeepingUnit() {
    return stockKeepingUnit;
  }

  public static class Builder {
    private String id;
    private String name;
    private String description;
    private double price;
    private String currency = "USD";
    private String stockKeepingUnit;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String desc) {
      this.description = desc;
      return this;
    }

    public Builder price(double price) {
      this.price = price;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder stockKeepingUnit(String stockKeepingUnit) {
      this.stockKeepingUnit = stockKeepingUnit;
      return this;
    }

    public NonConsumableProduct build() {
      if (id == null || name == null) {
        throw new IllegalStateException("ID and Name are required");
      }
      return new NonConsumableProduct(this);
    }
  }
}
