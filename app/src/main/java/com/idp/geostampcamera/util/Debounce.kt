package com.idp.geostampcamera.util

import android.view.View

fun View.setDebouncedClickListener(windowMs: Long = 500, action: (View) -> Unit) {
    var lastClick = 0L
    setOnClickListener {
        val now = System.currentTimeMillis()
        if (now - lastClick < windowMs) return@setOnClickListener
        lastClick = now
        action(it)
    }
}
