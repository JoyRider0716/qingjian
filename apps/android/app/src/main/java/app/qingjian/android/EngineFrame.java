package app.qingjian.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 引擎显示帧；Java 只转发内容，不计算候选顺序。 */
final class EngineFrame {
    final long revision;
    final String preedit;
    final String committed;
    final boolean deleteBackward;
    final String[] words;
    final String[] translations;

    EngineFrame(String json) throws JSONException {
        JSONObject frame = new JSONObject(json);
        revision = frame.getLong("revision");
        preedit = frame.getString("preedit");
        committed = frame.getString("committed");
        deleteBackward = frame.getBoolean("delete_backward");
        JSONArray candidates = frame.getJSONArray("candidates");
        words = new String[candidates.length()];
        translations = new String[candidates.length()];
        for (int i = 0; i < candidates.length(); i++) {
            words[i] = candidates.getJSONObject(i).getString("text");
            translations[i] = candidates.getJSONObject(i).getString("translation");
        }
    }
}
