# ── OpenCV ──
-keep class org.opencv.** { *; }
-keep class com.quickbirdstudios.opencv.** { *; }

# ── 无障碍服务（系统反射调用） ──
-keep class com.example.ninjaau.core.accessibility.** { *; }

# ── MediaProjection 相关 ──
-keep class android.media.projection.** { *; }

# ── Compose ──
-dontwarn androidx.compose.**

# ── 保留枚举 values/valueOf ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── 保留 data class (Gson/Serializable) ──
-keep class com.example.ninjaau.model.** { *; }
