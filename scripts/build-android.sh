#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
CORE_DIR="$ROOT_DIR/core"
ANDROID_DIR="$ROOT_DIR/android"
JNI_LIBS_DIR="$ANDROID_DIR/app/src/main/jniLibs"
KOTLIN_OUT_DIR="$ANDROID_DIR/app/src/main/java"

echo "=== Building Kothalipi Core for Android ==="

cd "$CORE_DIR"

# 1. Compile UniFFI bindgen CLI
cargo build --bin uniffi-bindgen

# 2. Build for Android targets
TARGETS=("aarch64-linux-android" "x86_64-linux-android")
for TARGET in "${TARGETS[@]}"; do
    echo "Compiling for $TARGET..."
    cargo build --release --target "$TARGET"
    
    ABI=""
    case "$TARGET" in
        "aarch64-linux-android") ABI="arm64-v8a" ;;
        "x86_64-linux-android")  ABI="x86_64" ;;
    esac

    mkdir -p "$JNI_LIBS_DIR/$ABI"
    cp "target/$TARGET/release/libkothalipi_core.so" "$JNI_LIBS_DIR/$ABI/"
done

# 3. Generate Kotlin Bindings
echo "Generating Kotlin UniFFI bindings..."
mkdir -p "$KOTLIN_OUT_DIR"
cargo run --bin uniffi-bindgen generate \
    --library "target/aarch64-linux-android/release/libkothalipi_core.so" \
    --language kotlin \
    --out-dir "$KOTLIN_OUT_DIR"

echo "=== Android build completed successfully! ==="
