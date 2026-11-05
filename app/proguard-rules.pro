# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.garidoctor.**$$serializer { *; }
-keepclassmembers class com.garidoctor.** { *** Companion; }
-keepclasseswithmembers class com.garidoctor.** { kotlinx.serialization.KSerializer serializer(...); }
# Bundled JSON resources in :core are read via ClassLoader
-keepdirectories templates/**
-keepdirectories symptoms/**
-keepdirectories obd/**
# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# usb-serial-for-android: the prober instantiates drivers from its table by reflection.
-keep class com.hoho.android.usbserial.driver.** { *; }
