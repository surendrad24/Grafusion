# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,includedescriptorclasses class com.fusionlancers.grafusion.**$$serializer { *; }
-keepclassmembers class com.fusionlancers.grafusion.** {
    *** Companion;
}
-keepclasseswithmembers class com.fusionlancers.grafusion.** {
    kotlinx.serialization.KSerializer serializer(...);
}
