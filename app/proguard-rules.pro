# Release builds are not minified (isMinifyEnabled = false); these keep a future switch safe.
# Jsoup references optional re2j classes.
-dontwarn com.google.re2j.**
# OkHttp platform-specific classes.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
