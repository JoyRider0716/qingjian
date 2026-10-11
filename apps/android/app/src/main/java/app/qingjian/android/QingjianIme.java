package app.qingjian.android;

import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 系统输入法壳：按键进 JNI，显示帧与提交文本送回当前编辑器。 */
public final class QingjianIme extends InputMethodService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    // handle 仅由 worker 读取和写入。
    private long handle;
    private int generation;
    private boolean active;
    private boolean ready;
    private boolean destroyed;
    private boolean password;
    private boolean privateInput;
    private boolean numeric;
    private boolean english;
    private boolean shifted;
    private boolean symbols;
    private LinearLayout keyboard;
    private CandidateStrip candidates;
    private TextView status;
    private EngineFrame displayed;
    private String failure;

    @Override public void onCreate() {
        super.onCreate();
        worker.execute(() -> {
            try {
                File resources = ResourceInstaller.install(this);
                handle = NativeEngine.create(resources.getAbsolutePath(), new File(getFilesDir(), "learning").getAbsolutePath());
                main.post(() -> {
                    if (destroyed) return;
                    ready = true;
                    renderKeyboard();
                    renderStatus();
                });
            } catch (Exception | LinkageError error) {
                main.post(() -> {
                    if (destroyed) return;
                    failure = "词库加载失败，请重新启用青简";
                    renderStatus();
                    renderKeyboard();
                });
            }
        });
    }

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 242, 233));
        // Android 15 的 edge-to-edge 下给系统导航栏留出底部空间。
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = android.os.Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                : insets.getSystemWindowInsetBottom();
            view.setPadding(0, 0, 0, bottom);
            return insets;
        });
        status = new TextView(this);
        status.setTextColor(Color.rgb(45, 85, 66));
        status.setTextSize(14);
        status.setPadding(dp(14), dp(5), dp(14), dp(5));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));
        candidates = new CandidateStrip(this);
        root.addView(candidates, new LinearLayout.LayoutParams(-1, dp(60)));
        if (displayed != null) {
            EngineFrame frame = displayed;
            candidates.show(frame, index -> select(index, frame.revision));
        }
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(3), 0, dp(3), dp(3));
        root.addView(keyboard);
        renderKeyboard();
        renderStatus();
        return root;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        generation++;
        active = true;
        password = EditorPolicy.password(info);
        privateInput = EditorPolicy.privateInput(info);
        numeric = EditorPolicy.numeric(info);
        symbols = numeric;
        shifted = false;
        displayed = null;
        send(command("reset", "private", privateInput), false);
        clearCandidates();
        renderKeyboard();
        renderStatus();
    }

    @Override public void onFinishInput() {
        active = false;
        generation++;
        displayed = null;
        send(command("reset", "private", true), false);
        clearCandidates();
        super.onFinishInput();
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd,
                                           int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        if (displayed != null && !displayed.preedit.isEmpty()
                && (newStart != candidatesEnd || newEnd != candidatesEnd)) {
            generation++;
            displayed = null;
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) connection.finishComposingText();
            send(command("reset", "private", privateInput), false);
            clearCandidates();
            renderStatus();
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        active = false;
        generation++;
        worker.execute(() -> { if (handle != 0) NativeEngine.destroy(handle); handle = 0; });
        worker.shutdown();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private JSONObject command(String type, String key, Object value) {
        try {
            JSONObject command = new JSONObject().put("type", type);
            if (key != null) command.put(key, value);
            return command;
        } catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
    }

    private void send(JSONObject command, boolean editorAction) {
        int epoch = generation;
        worker.execute(() -> {
            if (handle == 0) return;
            try {
                EngineFrame frame = new EngineFrame(NativeEngine.dispatch(handle, command.toString()));
                main.post(() -> {
                    if (!active || destroyed || generation != epoch) return;
                    apply(frame);
                    if (editorAction) performEnter();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (!active || destroyed || generation != epoch) return;
                    // 不显示引擎异常中的输入内容。
                    android.widget.Toast.makeText(this, "输入未完成，请缩短拼音或重新输入", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void apply(EngineFrame frame) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        connection.beginBatchEdit();
        try {
            if (!frame.committed.isEmpty()) connection.commitText(frame.committed, 1);
            if (frame.deleteBackward) {
                CharSequence selected = connection.getSelectedText(0);
                if (selected != null && selected.length() > 0) connection.commitText("", 1);
                else connection.deleteSurroundingTextInCodePoints(1, 0);
            }
            if (!frame.preedit.isEmpty()) connection.setComposingText(frame.preedit, 1);
            else {
                if (frame.committed.isEmpty() && !frame.deleteBackward
                        && displayed != null && !displayed.preedit.isEmpty()) {
                    connection.setComposingText("", 1);
                }
                connection.finishComposingText();
            }
        } finally { connection.endBatchEdit(); }
        displayed = frame;
        if (candidates != null) candidates.show(frame, index -> select(index, frame.revision));
        renderStatus();
    }

    private void select(int index, long revision) {
        JSONObject selection = command("select", "index", index);
        try { selection.put("revision", revision); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
        send(selection, false);
    }

    private boolean direct() { return password || numeric || english; }

    private void letter(String text) {
        if (direct() || shifted) {
            literalDirect(shifted ? text.toUpperCase(java.util.Locale.ROOT) : text);
            if (shifted) { shifted = false; renderKeyboard(); }
        } else send(command("input", "text", text), false);
    }

    private void literalDirect(String text) {
        // 顺序穿过同一队列，先处理尚未完成的拼音，再插入直输文字。
        int epoch = generation;
        worker.execute(() -> {
            if (handle == 0) return;
            try {
                EngineFrame frame = new EngineFrame(NativeEngine.dispatch(handle, command("raw", null, null).toString()));
                main.post(() -> {
                    if (!active || destroyed || generation != epoch) return;
                    apply(frame);
                    InputConnection connection = getCurrentInputConnection();
                    if (connection != null) connection.commitText(text, 1);
                });
            } catch (Exception error) { main.post(() -> { if (generation == epoch) renderStatus(); }); }
        });
    }

    private void symbol(String text) {
        if (direct()) literalDirect(text);
        else send(command("literal", "text", text), false);
    }

    private void enter() { send(command("raw", null, null), true); }

    private void performEnter() {
        InputConnection connection = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (connection == null || info == null) return;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action);
        } else connection.commitText("\n", 1);
    }

    private void switchLanguage() {
        if (password || numeric) return;
        send(command("raw", null, null), false);
        english = !english;
        shifted = false;
        renderKeyboard();
        renderStatus();
    }

    private void renderStatus() {
        if (status == null) return;
        if (failure != null) status.setText(failure);
        else if (!ready) status.setText("正在加载青简词库…");
        else if (password) status.setText("青简 · 密码输入");
        else if (displayed != null && !displayed.preedit.isEmpty()) status.setText(displayed.preedit);
        else status.setText(getString(R.string.keyboard_status,
            getString(direct() ? R.string.direct_mode : R.string.pinyin_mode),
            privateInput ? getString(R.string.no_learning_suffix) : ""));
    }

    private void clearCandidates() { if (candidates != null) candidates.show(null, index -> { }); }

    private void renderKeyboard() {
        if (keyboard == null) return;
        keyboard.removeAllViews();
        if (symbols) {
            addCharacters("1234567890", true);
            addCharacters("@#$%&*+-/", true);
            addCharacters("()[]!?=:;", true);
        } else {
            addCharacters("qwertyuiop", false);
            addCharacters("asdfghjkl", false);
            addCharacters("zxcvbnm", false);
        }
        LinearLayout tools = row();
        key(tools, symbols ? "ABC" : "123", 1, () -> { symbols = !symbols; renderKeyboard(); });
        key(tools, "⇧", 1, () -> { shifted = !shifted; renderKeyboard(); });
        key(tools, "'", 1, () -> { if (direct() || symbols) symbol("'"); else send(command("input", "text", "'"), false); });
        key(tools, ",", 1, () -> symbol(","));
        key(tools, ".", 1, () -> symbol("."));
        key(tools, "⌫", 1.5f, () -> send(command("backspace", null, null), false));
        LinearLayout bottom = row();
        key(bottom, "🌐", 1, () -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        key(bottom, direct() ? "英" : "中", 1, this::switchLanguage);
        key(bottom, "空格", 3, () -> { if (direct()) literalDirect(" "); else send(command("space", null, null), false); });
        key(bottom, "回车", 1.6f, this::enter);
    }

    private void addCharacters(String characters, boolean symbolsRow) {
        LinearLayout row = row();
        for (int i = 0; i < characters.length(); i++) {
            String character = characters.substring(i, i + 1);
            key(row, shifted && !symbolsRow ? character.toUpperCase(java.util.Locale.ROOT) : character, 1,
                () -> { if (symbolsRow) symbol(character); else letter(character); });
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        keyboard.addView(row, new LinearLayout.LayoutParams(-1, dp(49)));
        return row;
    }

    private void key(LinearLayout row, String label, float weight, Runnable pressed) {
        Button key = new Button(this);
        key.setText(label);
        key.setTextSize(label.length() > 1 ? 14 : 18);
        key.setAllCaps(false);
        key.setMinWidth(0);
        key.setMinimumWidth(0);
        key.setPadding(0, 0, 0, 0);
        key.setTextColor(Color.rgb(31, 66, 53));
        key.setEnabled(ready || "🌐".equals(label));
        key.setContentDescription("⌫".equals(label) ? "退格" : "⇧".equals(label) ? "大写" : label);
        key.setOnClickListener(v -> pressed.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, weight);
        params.setMargins(dp(1), 0, dp(1), 0);
        row.addView(key, params);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
