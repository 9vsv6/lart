# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.lart.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class app.lart.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
