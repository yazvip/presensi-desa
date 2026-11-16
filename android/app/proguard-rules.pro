# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**

# Serializer yang dipakai lewat refleksi/delegasi @Serializable
-keepclassmembers class id.desa.presensi.** {
    *** Companion;
}
-keepclasseswithmembers class id.desa.presensi.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class id.desa.presensi.**$$serializer { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ML Kit barcode
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX memakai anotasi @Experimental
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
