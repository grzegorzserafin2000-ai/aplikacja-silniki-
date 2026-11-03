package pl.cg.ziehlconfig;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A highlighted position is an instruction, only a confirmed readback gets a green tick. */
final class MotorDiagram extends View {
  private final int[] addresses;
  private final boolean[] confirmed;
  private final int current;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ValueAnimator pulse;
  private float glow = .5f;

  MotorDiagram(Context context, int[] addresses, boolean[] confirmed, int current) {
    super(context);
    this.addresses = addresses.clone(); this.confirmed = confirmed.clone(); this.current = current;
    setContentDescription("Plan wentylatorów. Teraz: " + GuidedSession.simpleLabel(addresses[current])
        + ". Zielony znacznik oznacza potwierdzone ustawienia.");
  }

  @Override protected void onMeasure(int width, int height) {
    setMeasuredDimension(MeasureSpec.getSize(width), Visuals.dp(getContext(), 174));
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float d = getResources().getDisplayMetrics().density;
    for (int section = 0; section < 2; section++) {
      boolean supply = section == 0;
      float y = (section == 0 ? 51 : 133) * d;
      paint.setColor(Visuals.MUTED); paint.setTextSize(13 * d); paint.setTextAlign(Paint.Align.LEFT);
      canvas.drawText(supply ? "NAWIEW →" : "WYWIEW →", 8 * d, y - 27 * d, paint);
      int count = 0;
      for (int address : addresses) if (((address & 1) == 1) == supply) count++;
      if (count == 0) {
        paint.setTextSize(12 * d); canvas.drawText("Brak w tej centrali", 8 * d, y + 4 * d, paint); continue;
      }
      float slot = (getWidth() - 16 * d) / Math.max(4, count);
      paint.setColor(0xff343434); paint.setStrokeWidth(3 * d);
      canvas.drawLine(8 * d, y, getWidth() - 8 * d, y, paint);
      int n = 0;
      for (int i = 0; i < addresses.length; i++) {
        if ((((addresses[i] & 1) == 1) != supply)) continue;
        float x = 8 * d + slot * (n++ + .5f);
        boolean active = i == current;
        if (active) {
          paint.setColor(Visuals.ORANGE); paint.setAlpha((int)(45 + 55 * glow));
          canvas.drawCircle(x, y, (18 + 3 * glow) * d, paint); paint.setAlpha(255);
        }
        paint.setColor(confirmed[i] ? 0xff17392b : active ? 0xff63300d : 0xff242424);
        canvas.drawCircle(x, y, 15 * d, paint);
        paint.setColor(confirmed[i] ? Visuals.GREEN : active ? Color.WHITE : Visuals.MUTED);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(16 * d);
        canvas.drawText(confirmed[i] ? "✓" : String.valueOf((addresses[i] - 231) / 2 + 1), x, y + 6 * d, paint);
      }
    }
  }

  private void animateHighlight() {
    if (pulse != null) { pulse.cancel(); pulse = null; }
    if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) return;
    pulse = ValueAnimator.ofFloat(0, 1); pulse.setDuration(1100);
    pulse.setRepeatMode(ValueAnimator.REVERSE); pulse.setRepeatCount(ValueAnimator.INFINITE);
    pulse.addUpdateListener(a -> { glow = (float) a.getAnimatedValue(); invalidate(); }); pulse.start();
  }
  @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); animateHighlight(); }
  @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); animateHighlight(); }
  @Override protected void onDetachedFromWindow() {
    if (pulse != null) { pulse.cancel(); pulse = null; }
    super.onDetachedFromWindow();
  }
}
