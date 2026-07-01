package com.euptron.billingcompat.core.providers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.euptron.billingcompat.core.handlers.ProductHandler;
import com.euptron.billingcompat.core.listeners.BillingConnectionListener;
import com.euptron.billingcompat.core.model.PurchaseType;
import com.euptron.billingcompat.core.products.Purchasable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GooglePlayProvider implements PaymentProvider, PurchasesUpdatedListener {
  private static final String TAG = "GooglePlayProvider";

  private final Context context;

  private BillingClient billingClient;
  private final List<Purchasable> pendingProducts = new ArrayList<>();
  private BillingConnectionListener connectionListener;
  private final Map<String, ProductDetails> productDetailsCache = new HashMap<>();
  private final Map<PurchaseType, ProductHandler<?>> handlers = new HashMap<>();
  private boolean isConnected = false;
  private OfferSelector offerSelector = new DefaultOfferSelector();

  public GooglePlayProvider(Context context) {
    this.context = context.getApplicationContext();
    initializeBillingClient();
  }

  private void initializeBillingClient() {
    PendingPurchasesParams pendingParams =
        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build();

    billingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(pendingParams)
            .build();
  }

  public void registerHandler(PurchaseType type, ProductHandler<?> handler) {
    handlers.put(type, handler);
    Log.d(TAG, "Registered handler for: " + type);
  }

  public <T extends ProductHandler<?>> T getHandler(PurchaseType type) {
    @SuppressWarnings("unchecked")
    T handler = (T) handlers.get(type);
    return handler;
  }

  public <T extends ProductHandler<?>> T getHandler(Class<?> handlerClass) {
    for (ProductHandler<?> handler : handlers.values()) {
      if (handlerClass.isInstance(handler)) {
        @SuppressWarnings("unchecked")
        T result = (T) handler;
        return result;
      }
    }
    return null;
  }

  public BillingClient getBillingClient() {
    return billingClient;
  }

  @Override
  public void connect() {
    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              isConnected = true;
              Log.d(TAG, "Connected to Google Play Billing");

              queryProductDetails(new ArrayList<>(pendingProducts));
              // Sync all handlers
              for (ProductHandler<?> handler : handlers.values()) {
                handler.sync();
              }

              if (connectionListener != null) {
                connectionListener.onConnected();
              }
            } else {
              Log.e(TAG, "Connection failed: " + billingResult.getDebugMessage());
              if (connectionListener != null) {
                connectionListener.onConnectionError(billingResult.getDebugMessage());
              }
            }
          }

          @Override
          public void onBillingServiceDisconnected() {
            isConnected = false;
            Log.w(TAG, "Disconnected from Google Play Billing");
            if (connectionListener != null) {
              connectionListener.onDisconnected();
            }
          }
        });
  }

  @Override
  public void disconnect() {
    if (billingClient != null) {
      billingClient.endConnection();
      isConnected = false;
    }
  }

  @Override
  public boolean isReady() {
    return isConnected && billingClient.isReady();
  }

  @Override
  public void launchPurchase(Activity activity, Purchasable product) {
    launchPurchase(activity, product, null);
  }

  @Override
  public void launchPurchase(Activity activity, Purchasable product, String oldSku) {
    if (!isReady()) {
      Log.e(TAG, "Billing client not ready");
      return;
    }

    ProductDetails details = productDetailsCache.get(product.getStockKeepingUnit());
    if (details == null) {
      Log.e(TAG, "Product details not found for: " + product.getStockKeepingUnit());
      return;
    }

    if (product.getType() == PurchaseType.SUBSCRIPTION) {
      List<ProductDetails.SubscriptionOfferDetails> offerDetails =
          details.getSubscriptionOfferDetails();

      if (offerDetails == null || offerDetails.isEmpty()) {
        Log.e(TAG, "No offer details found for: " + product.getStockKeepingUnit());
        return;
      }

      // Pick offer — prefer "trial" tag, fall back to index 0
      String offerToken = offerSelector.selectOffer(offerDetails, product.getStockKeepingUnit());
      if (offerToken == null) {
        Log.e(TAG, "OfferSelector returned null for: " + product.getStockKeepingUnit());
        return;
      }

      if (oldSku != null) {
        // Upgrade/downgrade — need old purchase token + new API
        final String finalOfferToken = offerToken;

        QueryPurchasesParams queryParams =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(
            queryParams,
            new PurchasesResponseListener() {
              @Override
              public void onQueryPurchasesResponse(
                  @NonNull BillingResult result, @NonNull List<Purchase> purchases) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                  Log.e(TAG, "Failed to query purchases for upgrade");
                  return;
                }

                String oldPurchaseToken = null;
                for (Purchase purchase : purchases) {
                  if (purchase.getProducts().contains(oldSku)) {
                    oldPurchaseToken = purchase.getPurchaseToken();
                    break;
                  }
                }

                if (oldPurchaseToken == null) {
                  Log.e(TAG, "Old purchase token not found for: " + oldSku);
                  return;
                }

                // New non-deprecated API — replacement params set on ProductDetailsParams
                BillingFlowParams.ProductDetailsParams productDetailsParams =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(finalOfferToken)
                        .setSubscriptionProductReplacementParams(
                            BillingFlowParams.ProductDetailsParams
                                .SubscriptionProductReplacementParams.newBuilder()
                                .setOldProductId(oldSku)
                                .setReplacementMode(
                                    BillingFlowParams.ProductDetailsParams
                                        .SubscriptionProductReplacementParams.ReplacementMode
                                        .CHARGE_PRORATED_PRICE)
                                .build())
                        .build();

                // SubscriptionUpdateParams still needed for oldPurchaseToken
                BillingFlowParams.SubscriptionUpdateParams updateParams =
                    BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(oldPurchaseToken)
                        .build();

                BillingFlowParams flowParams =
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            Collections.singletonList(productDetailsParams))
                        .setSubscriptionUpdateParams(updateParams)
                        .build();

                activity.runOnUiThread(
                    new Runnable() {
                      @Override
                      public void run() {
                        billingClient.launchBillingFlow(activity, flowParams);
                      }
                    });
              }
            });

      } else {
        // Fresh subscription — no upgrade
        BillingFlowParams.ProductDetailsParams productDetailsParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build();

        BillingFlowParams flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        billingClient.launchBillingFlow(activity, flowParams);
      }

    } else {
      // One-time products — no offerToken needed
      BillingFlowParams.ProductDetailsParams productDetailsParams =
          BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build();

      BillingFlowParams flowParams =
          BillingFlowParams.newBuilder()
              .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
              .build();

      billingClient.launchBillingFlow(activity, flowParams);
    }
  }

  @Override
  public void onPurchasesUpdated(
      @NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
        && purchases != null) {
      for (Purchase purchase : purchases) {
        handlePurchase(purchase);
      }
    } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
      Log.d(TAG, "User canceled purchase");
    } else {
      Log.e(TAG, "Purchase update error: " + billingResult.getDebugMessage());
    }
  }

  public void setPendingProducts(List<Purchasable> products) {
    this.pendingProducts.addAll(products);
  }

  private void handlePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
      // Handle pending
      for (String productId : purchase.getProducts()) {
        Purchasable product = findProduct(productId);
        if (product != null) {
          ProductHandler<Purchasable> handler = getTypedHandler(product.getType());
          if (handler != null) handler.onPurchasePending(product, purchase);
        }
      }
      return;
    }

    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
      for (String productId : purchase.getProducts()) {
        Purchasable product = findProduct(productId);
        if (product != null) {
          ProductHandler<Purchasable> handler = getTypedHandler(product.getType());
          if (handler != null) handler.onPurchaseSuccess(product, purchase);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Purchasable> ProductHandler<T> getTypedHandler(PurchaseType type) {
    return (ProductHandler<T>) handlers.get(type);
  }

  private Purchasable findProduct(String productId) {
    for (ProductHandler<?> handler : handlers.values()) {
      Purchasable product = handler.getProduct(productId);
      if (product != null) {
        return product;
      }
    }
    return null;
  }

  public void setOfferSelector(OfferSelector offerSelector) {
    if (offerSelector != null) {
      this.offerSelector = offerSelector;
    }
  }

  @Override
  public void queryPurchases() {
    // Query all product types
    queryPurchases(BillingClient.ProductType.INAPP);
    queryPurchases(BillingClient.ProductType.SUBS);
  }

  private void queryPurchases(String productType) {
    if (!isReady()) return;

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(productType).build();

    billingClient.queryPurchasesAsync(
        params,
        new PurchasesResponseListener() {
          @Override
          public void onQueryPurchasesResponse(
              @NonNull BillingResult result, @NonNull List<Purchase> purchases) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  for (String productId : purchase.getProducts()) {
                    Purchasable product = findProduct(productId);
                    if (product != null) {
                      ProductHandler<?> handler = getHandler(product.getType());
                      if (handler != null) {
                        // Handler will sync internally
                      }
                    }
                  }
                }
              }
            }
          }
        });
  }

  @Override
  public void acknowledge(String purchaseToken) {
    AcknowledgePurchaseParams params =
        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build();

    billingClient.acknowledgePurchase(
        params,
        new AcknowledgePurchaseResponseListener() {
          @Override
          public void onAcknowledgePurchaseResponse(@NonNull BillingResult result) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              Log.d(TAG, "Purchase acknowledged");
            }
          }
        });
  }

  @Override
  public void consume(String purchaseToken) {
    ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build();

    billingClient.consumeAsync(
        params,
        new ConsumeResponseListener() {
          @Override
          public void onConsumeResponse(@NonNull BillingResult result, @NonNull String token) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              Log.d(TAG, "Purchase consumed");
            }
          }
        });
  }

  @Override
  public void setConnectionListener(BillingConnectionListener listener) {
    this.connectionListener = listener;
  }

  public void queryProductDetails(List<Purchasable> products) {
    if (!isReady()) return;

    List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
    for (Purchasable product : products) {
      String productType = mapToProductType(product.getType());
      productList.add(
          QueryProductDetailsParams.Product.newBuilder()
              .setProductId(product.getStockKeepingUnit())
              .setProductType(productType)
              .build());
    }

    QueryProductDetailsParams params =
        QueryProductDetailsParams.newBuilder().setProductList(productList).build();

    billingClient.queryProductDetailsAsync(
        params,
        new ProductDetailsResponseListener() {
          @Override
          public void onProductDetailsResponse(
              @NonNull BillingResult result, @NonNull QueryProductDetailsResult details) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              for (ProductDetails detail : details.getProductDetailsList()) {
                productDetailsCache.put(detail.getProductId(), detail);
              }
              Log.d(TAG, "Cached " + productDetailsCache.size() + " product details");
            }
          }
        });
  }

  private String mapToProductType(PurchaseType type) {
    switch (type) {
      case NON_CONSUMABLE:
      case CONSUMABLE:
      case PENDING:
        return BillingClient.ProductType.INAPP;
      case SUBSCRIPTION:
        return BillingClient.ProductType.SUBS;
      default:
        return BillingClient.ProductType.INAPP;
    }
  }
}
