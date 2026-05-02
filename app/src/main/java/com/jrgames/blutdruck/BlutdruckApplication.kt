package com.jrgames.blutdruck

import android.app.Application
import com.jrgames.blutdruck.data.local.BlutdruckDatabase

class BlutdruckApplication : Application() {
    val database by lazy { BlutdruckDatabase.getInstance(this) }
}

