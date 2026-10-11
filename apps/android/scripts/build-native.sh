#!/usr/bin/env bash
# //! Android ARM64 与 x86_64 的 Rust 动态库，供 Gradle 打包。
set -euo pipefail
android_root="$(cd "$(dirname "$0")/.." && pwd)"
repository_root="$(cd "$android_root/../.." && pwd)"
output_dir="${1:-$android_root/app/build/generated/jniLibs}"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ndk_root="${ANDROID_NDK_HOME:-${sdk_root:+$sdk_root/ndk/27.2.12479018}}"
if [[ -z "$ndk_root" || ! -d "$ndk_root" ]]; then
    echo '请设置 ANDROID_HOME 或 ANDROID_NDK_HOME，安装 NDK 27.2.12479018。' >&2
    exit 1
fi
case "$(uname -s)" in
    Linux) host=linux-x86_64 ;;
    Darwin) host=darwin-x86_64 ;;
    *) echo '请在 Linux 或 macOS 上构建 Android 动态库。' >&2; exit 1 ;;
esac
toolchain="$ndk_root/toolchains/llvm/prebuilt/$host/bin"
export CARGO_TARGET_DIR="$repository_root/target/android"
export CARGO_BUILD_JOBS="${CARGO_BUILD_JOBS:-2}"
cd "$repository_root"
for abi in arm64-v8a x86_64; do
    case "$abi" in
        arm64-v8a) target=aarch64-linux-android; clang=aarch64-linux-android26-clang ;;
        x86_64) target=x86_64-linux-android; clang=x86_64-linux-android26-clang ;;
    esac
    linker_variable="CARGO_TARGET_${target^^}_LINKER"
    linker_variable="${linker_variable//-/_}"
    flags_variable="CARGO_TARGET_${target^^}_RUSTFLAGS"
    flags_variable="${flags_variable//-/_}"
    env "$linker_variable=$toolchain/$clang" "$flags_variable=-C link-arg=-Wl,-z,max-page-size=16384" \
        cargo build -p qingjian-android --target "$target" --release --locked
    mkdir -p "$output_dir/$abi"
    cp "$CARGO_TARGET_DIR/$target/release/libqingjian_android.so" "$output_dir/$abi/"
done
