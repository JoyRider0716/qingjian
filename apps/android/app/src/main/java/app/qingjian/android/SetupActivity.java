package app.qingjian.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启用输入法与本地试输入，不索取网络或存储权限。 */
public final class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding * 2, padding, padding);
        content.setBackgroundColor(Color.rgb(244, 246, 238));
        TextView title = new TextView(this);
        title.setText("青简");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(31, 66, 53));
        content.addView(title);
        TextView description = new TextView(this);
        description.setText("好好输入，顺便多认识一个词。\n\n中文全拼、英文译词和输入习惯学习都在本机完成。密码框不学习；本版本不连接网络。\n");
        description.setTextSize(16);
        content.addView(description);
        Button enable = new Button(this);
        enable.setText("1. 启用青简输入法");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        content.addView(enable);
        Button select = new Button(this);
        select.setText("2. 切换到青简");
        select.setOnClickListener(v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        content.addView(select);
        EditText trial = new EditText(this);
        trial.setHint("在这里试试 nihao 或 kaifa");
        trial.setMinLines(3);
        trial.setGravity(android.view.Gravity.TOP);
        trial.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(trial, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView version = new TextView(this);
        version.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME));
        content.addView(version);
        setContentView(content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            } else {
                view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }
}
