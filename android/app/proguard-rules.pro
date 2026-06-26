# Vosk gebruikt JNA (native bindings) — niet wegoptimaliseren.
-keep class com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class app.tok.**$$serializer { *; }
-keepclassmembers class app.tok.** {
    *** Companion;
}
