# LockCal ProGuard Rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.lockcal.app.model.** { *; }
