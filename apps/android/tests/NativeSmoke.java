package app.qingjian.android;

import java.nio.file.Files;
import java.nio.file.Path;

/** 主机 JVM 也穿过真实 JNI，验证句柄生命周期与线程归属。 */
final class NativeSmoke {
    public static void main(String[] args) throws Exception {
        Path user = Files.createTempDirectory("qingjian-jni-");
        long handle = NativeEngine.create(args[0], user.toString());
        try {
            String query = NativeEngine.dispatch(handle, "{\"type\":\"input\",\"text\":\"kaifa\"}");
            require(query.contains("开发") && query.contains("develop"), "JNI query and translation");
            String selected = NativeEngine.dispatch(handle, "{\"type\":\"space\"}");
            require(selected.contains("\"committed\":\"开发\""), "JNI commit");
            require(Files.readString(user.resolve("user.tsv")).contains("开发"), "JNI learning persistence");
            Thread wrong = new Thread(() -> {
                try { NativeEngine.dispatch(handle, "{\"type\":\"raw\"}"); throw new AssertionError("cross-thread handle accepted"); }
                catch (IllegalStateException expected) { }
            });
            final Throwable[] error = new Throwable[1];
            wrong.setUncaughtExceptionHandler((thread, failure) -> error[0] = failure);
            wrong.start();
            wrong.join();
            require(error[0] == null, "JNI thread isolation");
        } finally { NativeEngine.destroy(handle); }
        try { NativeEngine.dispatch(handle, "{\"type\":\"raw\"}"); throw new AssertionError("destroyed handle accepted"); }
        catch (IllegalStateException expected) { }
        try (var paths = Files.walk(user)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        System.out.println("JNI query, commit, persistence, thread isolation and destroyed-handle checks passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
