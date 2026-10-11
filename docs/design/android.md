# Android 输入法

第一阶段实现位于 `apps/android`：Java 原生输入法服务与键盘 + Rust JNI 动态库；输入法与 Core 在同一进程，暂不引入独立 Server。

## 数据流与线程

`InputMethodService` 收到触屏按键，单线程工作队列通过 JNI 送给 `Session`；`Session` 调用 `Engine::push/query/annotate/commit`，返回 JSON 显示帧。
UI 线程将提交文本交给 `InputConnection`，将剩余拼音设为 composing text，候选条直接展示回包的词与一条英文释义。
引擎创建、查询、学习落盘与销毁都在同一个工作线程，初始化与大词库解析不阻塞 UI。

每次输入框变更递增 Java 的代次，旧请求的回包不再接触当前编辑器。
每个 Rust 显示帧携带 revision，选词必须带上展示时的 revision；旧点击不提交新候选。
JNI 用线程局部整数句柄注册表，错误与 panic 在边界转成 Java 异常；不让 Java 解引用 Rust 指针。

## 生命周期与隐私

新输入框先丢弃旧组句、断开学习链，并按 `EditorInfo` 切换 private 状态。
文本 / 可见 / Web / 数字密码框全部直输；`IME_FLAG_NO_PERSONALIZED_LEARNING` 可继续拼音输入但不写学习数据。
首版没有网络权限、云预测、输入日志或宿主上下文读取。数据在应用私有目录，关闭系统自动备份。
选词和会话结束后 flush 学习；读取失败退回只在内存里学习，避免覆盖不可读文件。

## 打包与范围

最低 API 26，目标 API 35，Rust 1.96.0，Gradle 8.11.1，AGP 8.9.2，NDK 27.2.12479018。
打包 ARM64 / x86_64 动态库和仓库产品词库、英文词表、英文释义表，保留 GPL 与数据来源说明。
候选用自绘显示面，按键与启用页使用原生控件，延续仓库的显示 / 控件划分。

首版只提供全拼键盘与英文 / 数字直输。神经模型与静态 LM 数据、更多学习语言、方案设置、个人数据管理和正式签名发布属于后续阶段；必须分别验证手机资源占用与交互。
