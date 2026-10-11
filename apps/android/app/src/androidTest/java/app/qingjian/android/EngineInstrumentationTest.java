package app.qingjian.android;

import android.test.InstrumentationTestCase;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.io.File;
import org.json.JSONObject;

/** 在设备上验证 APK 内真实词库、JNI 与密码字段策略。 */
@SuppressWarnings("deprecation")
public final class EngineInstrumentationTest extends InstrumentationTestCase {
    public void testPackagedEngineQueriesAndCommits() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        File resources = ResourceInstaller.install(context);
        File learning = new File(context.getCacheDir(), "test-learning-" + System.nanoTime());
        long handle = NativeEngine.create(resources.getAbsolutePath(), learning.getAbsolutePath());
        try {
            EngineFrame frame = new EngineFrame(NativeEngine.dispatch(handle, "{\"type\":\"input\",\"text\":\"kaifa\"}"));
            assertEquals("开发", frame.words[0]);
            assertTrue(frame.translations[0].contains("develop"));
            JSONObject select = new JSONObject().put("type", "select").put("index", 0).put("revision", frame.revision);
            frame = new EngineFrame(NativeEngine.dispatch(handle, select.toString()));
            assertEquals("开发", frame.committed);
            assertEquals("", frame.preedit);
            assertTrue(new File(learning, "user.tsv").isFile());
        } finally { NativeEngine.destroy(handle); }
    }

    public void testPasswordAndNoLearningFields() {
        EditorInfo info = new EditorInfo();
        int[] variations = {InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD};
        for (int variation : variations) {
            info.inputType = InputType.TYPE_CLASS_TEXT | variation;
            assertTrue(EditorPolicy.password(info));
            assertTrue(EditorPolicy.privateInput(info));
        }
        info.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        assertTrue(EditorPolicy.password(info));
        assertTrue(EditorPolicy.numeric(info));
        info.inputType = InputType.TYPE_CLASS_TEXT;
        info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        assertFalse(EditorPolicy.password(info));
        assertTrue(EditorPolicy.privateInput(info));
    }
}
