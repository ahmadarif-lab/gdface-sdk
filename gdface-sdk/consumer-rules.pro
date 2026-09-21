# The com.seeta.sdk.* classes are called from native code through JNI (FindClass /
# GetFieldID / GetMethodID look them up by name as strings, not through normal Java
# references). R8 in the consuming app MUST NOT obfuscate or remove them, otherwise the
# app crashes at runtime (ClassNotFoundException / NoSuchFieldError) even though it
# compiles fine.
-keep class com.seeta.sdk.** { *; }
-keepclassmembers class com.seeta.sdk.** { *; }
