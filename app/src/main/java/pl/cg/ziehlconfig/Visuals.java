package pl.cg.ziehlconfig;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared tablet presentation; status colors never authorize or imply a motor write. */
final class Visuals {
  static final int ORANGE = 0xffff6900, GREEN = 0xff70ddac, MUTED = 0xffa8acb3;

  static int dp(Context c, int n) {
    return (int) (c.getResources().getDisplayMetrics().density * n + .5f);
  }

  static GradientDrawable panel(Context c, int fill, int border) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(dp(c, 16));
    g.setStroke(dp(c, 1), border);
    return g;
  }

  static Drawable icon(Context c, int res, int color, int size) {
    Drawable d = c.getDrawable(res).mutate();
    d.setTint(color);
    d.setBounds(0, 0, dp(c, size), dp(c, size));
    return d;
  }

  static int actionIcon(String title) {
    String t = title.toLowerCase(java.util.Locale.ROOT);
    if (t.contains("zakończ")) return R.drawable.ui_check;
    if (t.contains("wróć")) return R.drawable.ui_back;
    if (t.contains("przerwij")) return R.drawable.ui_stop;
    if (t.contains("nawiew")) return R.drawable.ui_supply;
    if (t.contains("wywiew")) return R.drawable.ui_extract;
    if (t.contains("raport")) return R.drawable.ui_report;
    if (t.contains("wstecz")) return R.drawable.ui_back;
    if (t.contains("menu")) return R.drawable.ui_unit;
    if (t.contains("sprawdź") || t.contains("potwierdz")) return R.drawable.ui_check;
    if (t.contains("wykryj") || t.contains("wyszukaj") || t.contains("szukaj"))
      return R.drawable.ui_search;
    if (t.contains("fabrycz")) return R.drawable.ui_power;
    if (t.contains("parametr") || t.contains("ustawień")) return R.drawable.ui_tune;
    return R.drawable.ui_next;
  }

  static void decorate(Button b) {
    b.setCompoundDrawablesRelative(
        icon(b.getContext(), actionIcon(b.getText().toString()), ORANGE, 24), null, null, null);
    b.setCompoundDrawablePadding(dp(b.getContext(), 12));
    b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    b.setMinimumHeight(dp(b.getContext(), 58));
  }

  static LinearLayout steps(Context c, String[] labels, int active) {
    LinearLayout row = new LinearLayout(c);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (int i = 0; i < labels.length; i++) {
      TextView t = new TextView(c);
      t.setText((i + 1) + "\n" + labels[i]);
      t.setTextSize(11);
      t.setGravity(Gravity.CENTER);
      t.setPadding(dp(c, 2), dp(c, 10), dp(c, 2), dp(c, 10));
      t.setTextColor(i <= active ? Color.WHITE : MUTED);
      t.setBackground(
          panel(c, i == active ? 0xff3b200e : 0xff151515, i <= active ? ORANGE : 0xff303030));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.setMargins(dp(c, 2), 0, dp(c, 2), 0);
      row.addView(t, p);
    }
    return row;
  }

  static View tile(Context c, int res, String title, String detail, Runnable action) {
    LinearLayout tile = new LinearLayout(c);
    tile.setOrientation(LinearLayout.HORIZONTAL);
    tile.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 24));
    tile.setGravity(Gravity.CENTER_VERTICAL);
    tile.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x66ff6900), panel(c, 0xff171717, 0xff39312b), null));
    tile.setFocusable(true);
    tile.setClickable(true);
    tile.setContentDescription(title + ". " + detail);
    tile.setOnClickListener(v -> action.run());
    ImageView icon = new ImageView(c);
    icon.setImageDrawable(icon(c, res, ORANGE, 40));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(icon, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
    LinearLayout words = new LinearLayout(c);
    words.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.setMargins(dp(c, 18), 0, 0, 0);
    tile.addView(words, lp);
    TextView name = new TextView(c);
    name.setText(title);
    name.setTextSize(20);
    name.setTextColor(Color.WHITE);
    name.setTypeface(null, android.graphics.Typeface.BOLD);
    words.addView(name);
    TextView desc = new TextView(c);
    desc.setText(detail);
    desc.setTextSize(14);
    desc.setTextColor(MUTED);
    desc.setPadding(0, dp(c, 8), 0, 0);
    words.addView(desc);
    return tile;
  }

  static void enter(View view) {
    if (ValueAnimator.areAnimatorsEnabled()) {
      view.setAlpha(.2f);
      view.setTranslationY(dp(view.getContext(), 8));
      view.animate().alpha(1).translationY(0).setDuration(160).start();
    }
  }
}
