#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
CORE_DIR="$ROOT_DIR/core"
IOS_DIR="$ROOT_DIR/ios"
FRAMEWORK_DIR="$IOS_DIR/Frameworks"

echo "=== Building Kothalipi Core for iOS ==="

cd "$CORE_DIR"

# 1. Build UniFFI bindgen CLI
cargo build --bin uniffi-bindgen

# 2. Build for iOS targets (Device + Simulator)
cargo build --release --target aarch64-apple-ios
cargo build --release --target aarch64-apple-ios-sim

# 3. Generate Swift bindings
echo "Generating Swift UniFFI bindings..."
mkdir -p "$IOS_DIR/Kothalipi/Generated"
cargo run --bin uniffi-bindgen generate \
    --library "target/aarch64-apple-ios/release/libkothalipi_core.dylib" \
    --language swift \
    --out-dir "$IOS_DIR/Kothalipi/Generated"

# 4. Create XCFramework
echo "Creating KothalipiCore.xcframework..."
rm -rf "$FRAMEWORK_DIR/KothalipiCore.xcframework"
mkdir -p "$FRAMEWORK_DIR"

xcodebuild -create-xcframework \
    -library "target/aarch64-apple-ios/release/libkothalipi_core.a" \
    -headers "$IOS_DIR/Kothalipi/Generated" \
    -library "target/aarch64-apple-ios-sim/release/libkothalipi_core.a" \
    -headers "$IOS_DIR/Kothalipi/Generated" \
    -output "$FRAMEWORK_DIR/KothalipiCore.xcframework"

echo "=== iOS build completed successfully! ==="
