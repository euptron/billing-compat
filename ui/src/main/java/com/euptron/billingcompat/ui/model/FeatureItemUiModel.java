package com.euptron.billingcompat.ui.model;

import androidx.annotation.Nullable;

/**
 * A single feature row shown in the "What you get" card.
 *
 * <p>Each instance renders as a checkmark icon + title + optional subtitle row.
 *
 * <pre>{@code
 * // Title only
 * new FeatureItemUiModel("Ad-free experience")
 *
 * // Title + subtitle
 * new FeatureItemUiModel("Unlimited downloads", "Movies, music, podcasts & more")
 * }</pre>
 */
public final class FeatureItemUiModel {

  private final String text;
  @Nullable private final String subtitle;

  /** Title-only constructor — subtitle will be hidden. */
  public FeatureItemUiModel(String text) {
    this(text, null);
  }

  /**
   * @param text Required. The main feature label shown in bold body text.
   * @param subtitle Optional. A shorter description shown below the title in muted text. Pass
   *     {@code null} to hide the subtitle row entirely.
   */
  public FeatureItemUiModel(String text, @Nullable String subtitle) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Feature text must not be null or empty");
    }
    this.text = text;
    this.subtitle = subtitle;
  }

  public String getText() {
    return text;
  }

  /** Returns the subtitle string, or {@code null} if none was provided. */
  @Nullable
  public String getSubtitle() {
    return subtitle;
  }

  /** Returns {@code true} if this item has a non-null, non-empty subtitle. */
  public boolean hasSubtitle() {
    return subtitle != null && !subtitle.isEmpty();
  }
}
