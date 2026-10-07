# Regole di default per Jetpack Compose e R8
-keepattributes *Annotation*
-dontwarn javax.annotation.**

# Shizuku & IPC Binder
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }
-keep class * extends android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }

# Pattern enum & modelli dati usati per comandi o reflection
-keep class com.hilight.studio.model.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose runtime keep rules di base
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
