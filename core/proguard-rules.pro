# Add project specific ProGuard rules here.
# By default, the flags in this file are applied to all release builds.
# For more details, see https://developer.android.com/build/shrink-code

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class io.openrouter.android.auto.**$$serializer { *; }
-keepclassmembers class io.openrouter.android.auto.** { *** Companion; }
-keepclasseswithmembers class io.openrouter.android.auto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
