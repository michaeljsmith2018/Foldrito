# Add project specific Proguard rules here.
# By default, the Proguard rules in the Android SDK are used.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Jetpack Compose rules if needed, though they are usually packaged with the library.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
