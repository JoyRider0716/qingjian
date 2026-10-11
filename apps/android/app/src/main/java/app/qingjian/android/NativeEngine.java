package app.qingjian.android;

/** JNI 的调用方必须始终使用创建引擎的同一线程。 */
final class NativeEngine {
    static { System.loadLibrary("qingjian_android"); }

    private NativeEngine() { }

    static native long create(String resources, String user);
    static native String dispatch(long handle, String command);
    static native void destroy(long handle);
}
