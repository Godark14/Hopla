# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.hopla.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
