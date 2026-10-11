package app.qingjian.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** 自绘候选的文字与单条译词，点击和无障碍行为仍由原生 View 提供。 */
@android.annotation.SuppressLint("ViewConstructor")
final class CandidateView extends View {
    private final android.text.TextPaint wordPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint glossPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final String word;
    private final String translation;
    private final float density;
    final int preferredWidth;

    CandidateView(Context context, String word, String translation) {
        super(context);
        this.word = word;
        this.translation = translation;
        density = getResources().getDisplayMetrics().density;
        float scale = getResources().getDisplayMetrics().scaledDensity;
        wordPaint.setTextSize(21 * scale);
        wordPaint.setColor(Color.rgb(28, 66, 54));
        glossPaint.setTextSize(12 * scale);
        glossPaint.setColor(Color.rgb(91, 106, 99));
        dividerPaint.setColor(Color.rgb(222, 229, 221));
        preferredWidth = (int) Math.min(220 * density,
            Math.max(78 * density, Math.max(wordPaint.measureText(word), glossPaint.measureText(translation)) + 28 * density));
        setContentDescription(translation.isEmpty() ? word : word + "，" + translation);
        setFocusable(true);
        setClickable(true);
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        setBackgroundResource(ripple.resourceId);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - 24 * density;
        CharSequence label = TextUtils.ellipsize(word, wordPaint, width, TextUtils.TruncateAt.END);
        CharSequence gloss = TextUtils.ellipsize(translation, glossPaint, width, TextUtils.TruncateAt.END);
        canvas.drawText(label.toString(), 12 * density, 28 * density, wordPaint);
        canvas.drawText(gloss.toString(), 12 * density, 49 * density, glossPaint);
        canvas.drawLine(getWidth() - 1, 12 * density, getWidth() - 1, getHeight() - 12 * density, dividerPaint);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
    }
}
