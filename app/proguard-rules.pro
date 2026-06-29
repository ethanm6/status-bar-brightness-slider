# ============================================================
# LSPosed module ProGuard rules
# ============================================================
#
# IMPORTANT: minifyEnabled is set to false in build.gradle.kts,
# so ProGuard/R8 does not run on release builds. These rules are
# here as a safety net in case minification is ever enabled.
#
# The hook class is referenced by name in assets/xposed_init.
# LSPosed finds it via Class.forName() at runtime, so its name
# must never be obfuscated or removed.

-keep class dev.module.statusbarbrightnessgesture.** { *; }

# Keep all Xposed API classes (they are compileOnly and not in the APK,
# but keep the references intact so the DEX bytecode is valid).
-keep class de.robv.android.xposed.** { *; }

# BrightnessInfo is accessed via Display.getBrightnessInfo() — keep its fields.
-keep class android.hardware.display.BrightnessInfo { *; }
