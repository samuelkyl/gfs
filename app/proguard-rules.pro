# Project-specific ProGuard / R8 rules.
# Note: Room, Hilt, Compose, and kotlinx-serialization bundle consumer ProGuard rules automatically.

# Keep kotlinx.serialization @Serializable classes and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepnames class <2>$$serializer {
    static <2>$$serializer INSTANCE;
}

# Keep Room database constructor & entities
-keep class ca.sleung.gfs.data.local.db.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase {
    void <init>();
}
