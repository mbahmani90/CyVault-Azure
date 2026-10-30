# R8 rules for the release build.
# Ktor, kotlinx.serialization, Koin, Compose and MSAL ship their own consumer rules,
# so only app-specific rules go here.

# Keep file and line numbers so release stack traces can be retraced with mapping.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MSAL depends on Nimbus JOSE, which uses Tink only for optional Ed25519/X25519/XChaCha20
# algorithms. Tink isn't on the classpath and MSAL doesn't use those algorithms.
-dontwarn com.google.crypto.tink.**
