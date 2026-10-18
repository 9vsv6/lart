# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.lart.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class app.lart.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Named only in the manifest's Cast meta-data, which R8 doesn't follow.
-keep class app.lart.mobile.player.CastOptionsProvider { *; }
