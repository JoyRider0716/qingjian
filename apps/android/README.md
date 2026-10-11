# Android 基础版

原生 `InputMethodService` + Java 键盘，通过 JNI 复用 Rust `Engine`。Android 8.0（API 26）起，支持 ARM64 与 x86_64；首版暂不打包 32 位 ARM。

功能：中文全拼、简拼、Core 纠错和词频整句、英文译词、横滑候选、部分选词后继续组句、英文与数字直输、中文标点、退格、本地词频与个人 n-gram 学习。
不申请网络权限，不接云联想；神经模型、静态 bigram 数据、双拼 / 五笔设置、语音、滑行输入和主题设置尚未接入。

## 构建

需要 JDK 17 或 21、Rust 1.96.0、Android SDK Platform 35、Build Tools 35.0.0、NDK 27.2.12479018。

```sh
rustup target add aarch64-linux-android x86_64-linux-android
export ANDROID_HOME=/你的/Android/Sdk
export JAVA_HOME=/你的/JDK
cd apps/android
./gradlew assembleDebug
```

Gradle 自动编译两个 ABI 的 Rust 动态库并打包现有产品数据：`assets/lexicon/dict.tsv`、`english.tsv`、`assets/glossary/glossary-en.tsv`。
输出为 `app/build/outputs/apk/debug/app-debug.apk`，开发签名，可安装测试；发布需单独配置签名。
当前基础版以开发签名预发布到 GitHub Releases。推送与 `versionName` 一致的 `android-v*` 标签（如 `android-v0.1.0-dev`）会触发 `.github/workflows/android-release.yml`，执行 Rust 测试、APK 构建、Lint、签名和 ZIP 对齐校验，再上传 APK 与 `SHA256SUMS`。该流程只发布 Android 预发布版本。开发签名每次构建可能变化，重新安装可能需先卸载；正式版需配置长期维护的发布签名。
Rust 交叉编译脚本也可以通过 `ANDROID_NDK_HOME` 指定 NDK；Gradle 只配 `local.properties` 时，还须把这个变量提供给 Rust 脚本。
安装时以 APK 版本号区分静态数据目录，升级不会覆盖独立的学习目录。修改打包数据后调试，需递增 `versionCode` 或清除应用数据。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动「青简」，按提示启用并选中输入法，在试输入框键入 `nihao` / `kaifa`，确认「你好 / hello」「开发 / develop」，点击候选上屏。

## 验证

```sh
# 仓库根目录
cargo test -p qingjian-android --locked
cargo clippy -p qingjian-android --all-targets --locked -- -D warnings
cargo build -p qingjian-android --locked
mkdir -p /tmp/qingjian-jni-classes
javac -d /tmp/qingjian-jni-classes apps/android/app/src/main/java/app/qingjian/android/NativeEngine.java apps/android/tests/NativeSmoke.java
java -Djava.library.path=target/debug -cp /tmp/qingjian-jni-classes app.qingjian.android.NativeSmoke assets/sample

# 有设备或模拟器时
cd apps/android
./gradlew lintDebug connectedDebugAndroidTest
```

Rust 测试验证候选与释义、部分上屏、旧候选版本、退格、符号 / 原样上屏以及学习持久化与隐私。
主机 JNI 测试实际加载 `.so`，验证线程归属、失效句柄、查询和落盘。
设备测试实际加载 APK 内的正式词库与 JNI，并检查密码与禁止学习字段策略；桌面键盘体验和跨应用行为还须真机验证。

本次验证（2026-10-11）：完整 Rust workspace（排除 macOS 壳）717 项通过、1 项忽略；fmt / Clippy 通过；Android Lint 无问题。
Android 8.0 / API 26 x86_64 模拟器安装 APK，2 项设备测试通过；触屏输入、英文译词展示、候选点击上屏、删除组句与删除已上屏字符已实际验证。
APK 开发签名与 16 KiB ZIP 对齐已检查，ARM64 / x86_64 原生库均完成交叉编译。尚未进行 ARM64 真机、其他应用兼容性或真实 16 KiB 页设备测试。

## 实现边界

- Java 只接系统编辑器、显示候选和转发按键，所有中文排序逻辑留在 Core。
- Rust 引擎由同一工作线程创建、操作和销毁；线程局部注册表将整数句柄映射到会话，避免裸指针句柄。
- Java 用输入框代次拒绝旧回包；Rust 用候选帧版本拒绝旧槽位点击。
- 密码框强制直输，密码与 `IME_FLAG_NO_PERSONALIZED_LEARNING` 均禁止学习。没有输入日志、网络请求和应用上下文采集。
- 切换输入框 / 光标移出组句区时清理引擎会话；编辑器提交通过 `InputConnection`，退格按 Unicode 码点删除。
- 数据与学习文件在应用私有目录；禁止 Android 自动备份。卸载会删除这些文件。
- 本地释义只显示英语；整句使用 Core 词频与个人 n-gram 回退，不声称桌面模型效果。
- 候选由 Canvas 自绘并提供独立可访问点击区域；触屏按键与设置页使用 Android 原生控件。
- 两个 ABI 的 ELF 设置 16 KiB 页对齐，APK 使用未压缩动态库；发布前仍需在真实 16 KiB 页设备验证。
