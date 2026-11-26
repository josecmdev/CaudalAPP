package com.example.caudalapp

import android.app.Application

class CaudalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticReporter.installCrashCapture(this)
        com.example.caudalapp.sync.CaudalSync.start(this)
    }
}
