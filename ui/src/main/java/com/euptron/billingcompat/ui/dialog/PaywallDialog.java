package com.euptron.billingcompat.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.euptron.billingcompat.ui.R;
import com.euptron.billingcompat.ui.adapter.PaywallAdapter;
import com.euptron.billingcompat.ui.model.PaywallCallback;
import com.euptron.billingcompat.ui.model.PaywallConfig;
import com.google.android.material.button.MaterialButton;

/**
 * Fullscreen paywall {@link DialogFragment} with self-contained inset handling.
 *
 * <h2>Inset strategy</h2>
 *
 * <p>Android 15+ (API 35) enforces edge-to-edge for all apps. Many apps on API 29–34 also opt in
 * via {@code WindowCompat.setDecorFitsSystemWindows(window, false)}. This dialog detects both cases
 * and applies WindowInsets itself so it never draws behind the status bar or navigation bar,
 * regardless of what the host app does.
 *
 * <ul>
 *   <li>Top inset → applied as padding to the top bar, keeping app-name + cancel button clear of
 *       the status bar.
 *   <li>Bottom inset → applied as padding to the RecyclerView so the subscribe button / footer
 *       clears gesture nav or the nav bar. The padding is additive with the existing 24 dp bottom
 *       padding so the feel stays premium.
 *   <li>Left/right insets → applied to the root view to handle display cutouts in landscape.
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * PaywallDialog.newInstance(config)
 *     .setCallback(callback)
 *     .show(getSupportFragmentManager(), "paywall");
 * }</pre>
 */
public class PaywallDialog extends DialogFragment {

  private PaywallConfig config;
  @Nullable private PaywallCallback callback;
  private WindowInsetsCompat windowInsets;

  public static PaywallDialog newInstance(PaywallConfig config) {
    PaywallDialog dialog = new PaywallDialog();
    dialog.config = config;
    return dialog;
  }

  public PaywallDialog setCallback(@Nullable PaywallCallback callback) {
    this.callback = callback;
    return this;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    Dialog dialog = new Dialog(requireContext(), R.style.PaywallDialogTheme);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    return dialog;
  }

  @Override
  public void onStart() {
    super.onStart();
    Dialog dialog = getDialog();
    if (dialog == null || dialog.getWindow() == null) return;

    Window window = dialog.getWindow();

    window.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    window.setWindowAnimations(R.style.PaywallDialogAnimation);
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(Color.TRANSPARENT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false);
    } else {
      window
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }


    View decorView = window.getDecorView();
    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
    
    boolean isLight = (getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;

    controller.setAppearanceLightStatusBars(isLight);
    controller.setAppearanceLightNavigationBars(isLight);
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_paywall, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    if (config == null) {
      dismissAllowingStateLoss();
      return;
    }

    View topBar = view.findViewById(R.id.paywall_top_bar);
    android.widget.TextView appNameView = view.findViewById(R.id.paywall_app_name);
    MaterialButton cancelButton = view.findViewById(R.id.paywall_cancel_button);
    RecyclerView recyclerView = view.findViewById(R.id.paywall_recycler_view);

    cancelButton.setOnClickListener(v -> dismiss());
    appNameView.setText(config.getAppName());

    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    PaywallAdapter adapter = new PaywallAdapter(config);
    adapter.setOnSubscribeClickListener(
        plan -> {
          if (callback != null) callback.onSubscribeClicked(plan);
        });
    adapter.setOnRestoreClickListener(
        () -> {
          if (callback != null) callback.onRestoreClicked();
        });
    recyclerView.setAdapter(adapter);
    if (isHostActivityEdgeToEdge()) applyInsets(view);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    if (callback != null) callback.onDismissed();
  }

  @Override
  public void onCancel(@NonNull DialogInterface dialog) {
    super.onCancel(dialog);
    // onDismiss is always called after onCancel — callback fires once via onDismiss.
  }

  @SuppressWarnings("unused")
  private boolean isHostActivityEdgeToEdge() {
    Activity activity = getActivity();
    if (activity == null) return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      // API 35+: always edge-to-edge
      return true;
    }
    // API 29–34: check decorFitsSystemWindows via insets on the decor view.
    // If the decor view reports non-zero insets for systemBars it means the
    // system is dispatching insets to the view hierarchy, i.e. the activity
    // is NOT fitting system windows (= edge-to-edge opted in).
    View decorView = activity.getWindow().getDecorView();
    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decorView);
    if (insets == null) return false;
    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
    return systemBars.top > 0 || systemBars.bottom > 0;
  }

  private void applyInsets(View view) {
    final int initialLeft = view.getPaddingLeft();
    final int initialTop = view.getPaddingTop();
    final int initialRight = view.getPaddingRight();
    final int initialBottom = view.getPaddingBottom();

    ViewCompat.setOnApplyWindowInsetsListener(
        view,
        (v, windowInsets) -> {
          Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(
              initialLeft + insets.left,
              initialTop + insets.top,
              initialRight + insets.right,
              initialBottom);

          return WindowInsetsCompat.CONSUMED;
        });

    // Trigger inset dispatch immediately in case the view is already attached.
    ViewCompat.requestApplyInsets(view);
  }
}
