package com.garidoctor.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose's LocalContext may be a wrapper; billing and rewarded ads need the real Activity. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
