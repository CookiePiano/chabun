# Release builds are shrunk with R8 (see app/build.gradle.kts).
#
# Pleos Vehicle SDK 2.3.2 ships no consumer rules. It binds to the vehicle service over AIDL and
# passes its own Parcelable and enum types across the binder, so the SDK is kept exactly as
# published; renaming or removing any of it could break the binder protocol at run time.
-keep class ai.pleos.playground.vehicle.** { *; }
-keep interface ai.pleos.playground.vehicle.** { *; }
-keep class ai.pleos.playground.sdk.** { *; }

# The SpeechToText and TextToSpeech SDKs 3.3.1 also ship no consumer rules and talk to the
# speech service over AIDL (ai.pleos.playground.caas.**). They load native code through JNA,
# which finds Java classes and fields by name. Kept as published for the same reason.
-keep class ai.pleos.playground.stt.** { *; }
-keep interface ai.pleos.playground.stt.** { *; }
-keep class ai.pleos.playground.tts.** { *; }
-keep interface ai.pleos.playground.tts.** { *; }
-keep class ai.pleos.playground.caas.** { *; }
-keep interface ai.pleos.playground.caas.** { *; }
-keep class ai.pleos.playground.security.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# The Vehicle SDK reads capability answers with Gson 2.10.1, which ships no R8 rules. Gson needs
# the generic signatures of its TypeToken subclasses; without them every capability check that
# returns a map (fan, window, reading light, wiper) fails and the feature falls back to the
# simulation. Seen on the Pleos Connect v2.1.0 emulator on 2026-11-12.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# Keeps class and line information in stack traces from the vehicle; the mapping file in
# app/build/outputs/mapping/release/ translates the rest.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
