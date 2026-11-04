package com.garidoctor.app.ai

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug builds: App Check debug provider. Logcat prints a debug token to register in the Firebase console. */
object AppCheckSetup {
    fun install() = FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
