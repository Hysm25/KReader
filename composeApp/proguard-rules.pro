# ProGuard / R8 Rules for KReader

# 1. Readium 3.0 Engine
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# 2. JavaScript Interface Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. SQLDelight Database
-keep class app.cash.sqldelight.** { *; }
-keep class io.github.kreader.core.database.** { *; }
-dontwarn app.cash.sqldelight.**

# 3. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * extends kotlinx.serialization.KSerializer {
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keep class io.github.kreader.core.model.** { *; }

# 4. Android Fragments & ViewModels
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
    public <init>(...);
}
-keep public class * extends androidx.lifecycle.ViewModel {
    public <init>();
    public <init>(...);
}

# 5. Desugaring & System Compatibility
-dontwarn j$.**
-keep class androidx.sqlite.** { *; }
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
