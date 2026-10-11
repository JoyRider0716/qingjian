package app.qingjian.android;

import android.content.Context;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.function.IntConsumer;

/** 可横滑的候选条，顺序完全沿用引擎回包。 */
final class CandidateStrip extends HorizontalScrollView {
    private final LinearLayout content;

    CandidateStrip(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        addView(content);
    }

    void show(EngineFrame frame, IntConsumer select) {
        content.removeAllViews();
        scrollTo(0, 0);
        if (frame == null) return;
        for (int i = 0; i < frame.words.length; i++) {
            int index = i;
            CandidateView view = new CandidateView(getContext(), frame.words[i], frame.translations[i]);
            view.setOnClickListener(v -> select.accept(index));
            content.addView(view, new LinearLayout.LayoutParams(view.preferredWidth, -1));
        }
    }
}
