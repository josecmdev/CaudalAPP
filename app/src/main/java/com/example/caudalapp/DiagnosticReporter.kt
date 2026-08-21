package com.example.caudalapp

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReporter {
    private const val CRASH_FILE = "caudal-crashes.log"
    private const val MAX_CRASH_LOG_CHARS = 120_000

    fun installCrashCapture(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CaudalCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CaudalCrashHandler(context.applicationContext, previous))
    }

    fun share(context: Context) {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = File(directory, "caudal-report-$timestamp.txt")
        report.writeText(buildReport(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Caudal App $timestamp")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Enviar reporte con").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun buildReport(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val state = File(context.filesDir, "caudal-state-v1.json")
        val crashes = File(context.filesDir, CRASH_FILE)
            .takeIf(File::exists)
            ?.readText()
            ?.takeLast(MAX_CRASH_LOG_CHARS)
            ?: "Sin cierres inesperados registrados."
        return buildString {
            appendLine("CAUDAL APP · REPORTE DE DIAGNÓSTICO")
            appendLine("Generado: ${Date()}")
            appendLine("Versión: ${packageInfo.versionName} (${packageInfo.longVersionCode})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            appendLine("Estado local: ${if (state.exists()) "${state.length()} bytes" else "no encontrado"}")
            appendLine()
            appendLine("CIERRES INESPERADOS CAPTURADOS")
            appendLine(crashes)
        }
    }

    private class CaudalCrashHandler(
        private val context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                File(context.filesDir, CRASH_FILE).appendText(
                    buildString {
                        appendLine()
                        appendLine("--- ${Date()} · hilo ${thread.name} ---")
                        appendLine(error.stackTraceToString())
                    },
                )
            }
            delegate?.uncaughtException(thread, error)
        }
    }
}
