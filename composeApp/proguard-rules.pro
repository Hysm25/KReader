# ProGuard / R8 Rules for KReader

# 1. Readium 3.0 Engine
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# 2. JavaScript Interface Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 3. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep @kotlinx.serialization.Serializable class io.github.kreader.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
    *** Companion;
}
-keepclassmembers class * extends kotlinx.serialization.KSerializer {
    <fields>;
    <methods>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}

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
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
