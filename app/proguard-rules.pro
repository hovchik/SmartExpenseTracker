# Keep Gson model classes (serialised by reflection to flowsense_data.json)
-keep class com.flowsense.app.data.model.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
