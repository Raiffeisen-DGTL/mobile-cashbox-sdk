# Consumer R8 rules for MobileCashbox. Applied when an app minifies this AAR.

# Keep the entire public API surface (everything directly declared in the base package) so
# consumers can call it after R8 runs. Internal implementation is NOT kept here deliberately.
-keep public interface ru.raiffeisen.mobilecashbox.* { public *; }
-keep public class ru.raiffeisen.mobilecashbox.* { public *; }
-keep public enum ru.raiffeisen.mobilecashbox.* { public *; }

# Keep the internal @Serializable DTOs. They are serialized explicitly (see DEFAULT_JSON)
# rather than via Ktor ContentNegotiation, but R8 must not strip/rename them or their fields,
# or the wire format breaks at runtime.
-keep @kotlinx.serialization.Serializable class ru.raiffeisen.mobilecashbox.internal.** { *; }

# Keep custom serializers referenced from the internal payment/refund DTOs.
-keep class ru.raiffeisen.mobilecashbox.internal.gateway.BigDecimalSerializer { *; }
-keep class ru.raiffeisen.mobilecashbox.internal.gateway.LocalDateTimeSerializer { *; }

# Preserve Kotlin/JVM metadata so consumers keep nullability/generics and named/default args.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Ktor/coroutines/kotlin-stdlib reference optional engine classes that aren't on
# an app's classpath; these are library-internal and nothing to fail the build over.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
