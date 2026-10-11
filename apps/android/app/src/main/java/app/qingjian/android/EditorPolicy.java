package app.qingjian.android;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** 密码与禁止个性化学习的字段不写用户数据；密码只用直输。 */
final class EditorPolicy {
    private EditorPolicy() { }

    static boolean password(EditorInfo info) {
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            || (inputClass == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    }

    static boolean privateInput(EditorInfo info) {
        return password(info) || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    static boolean numeric(EditorInfo info) {
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        return inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE
            || inputClass == InputType.TYPE_CLASS_DATETIME;
    }
}
