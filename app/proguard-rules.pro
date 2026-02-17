# Keep Gson model classes
-keep class com.smartexpense.tracker.data.model.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
