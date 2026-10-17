# Release R8 rules. Room, Hilt, DataStore, Compose, coroutines and Play Billing ship their own
# consumer rules; the manifest keeps the services, receivers and activities it names (the
# accessibility services, TrackingService, BootReceiver, the alarm receivers, MainActivity).
# Unscroll uses no reflection or serialization of its own, so only these are needed:

# Release builds log nothing at debug or verbose level (the calls are removed).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Readable stack traces in Play Console (mapping.txt) without exposing source file names.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

# Room: entities and DAOs are generated code referenced directly; keep the generated database
# implementation's no-arg constructor Room instantiates by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Accessibility services are bound by the system by class name (also kept via the manifest);
# keep their config XML references intact.
-keep class * extends android.accessibilityservice.AccessibilityService { <init>(); }
