# Settings, widget sizes and widget placement are persisted to SharedPreferences by enum
# name (see WidgetStorage and SettingsRepository) and read back with valueOf / name
# comparison. Renaming those constants would quietly reset everyone's setup on the first
# minified build, so pin the project's enums.
-keepclassmembers enum alexcmb.mytvlauncher.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep line numbers in crash reports from released builds, while still obfuscating the
# original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
