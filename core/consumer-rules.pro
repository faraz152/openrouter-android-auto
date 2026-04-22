# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes in the SDK package
-keep,includedescriptorclasses class io.openrouter.android.auto.**$$serializer { *; }
-keepclassmembers class io.openrouter.android.auto.** {
    *** Companion;
}
-keepclasseswithmembers class io.openrouter.android.auto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
