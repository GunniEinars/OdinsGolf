# kotlinx.serialization keeps generated serializers; protect model + serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.odinsgolf.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.odinsgolf.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.odinsgolf.**$$serializer { *; }
