# OkHttp ships its own rules; org.json and the app's models are read by name only through JSONObject.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Tink (under security-crypto) names compile-time annotations that are not shipped.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
