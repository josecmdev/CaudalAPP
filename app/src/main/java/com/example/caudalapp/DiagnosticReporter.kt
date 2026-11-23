package com.example.caudalapp

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReporter {
    private const val CRASH_FILE = "caudal-crashes.log"
    private const val ERROR_FILE = "caudal-errors.log"
    private const val MAX_CRASH_LOG_CHARS = 120_000
    private const val LOGCAT_LINES = 400

    @Volatile
    private var appContext: Context? = null

    /** Guarda un error que la app pudo manejar, para que llegue en el reporte aunque no haya cierre. */
    fun logError(where: String, error: Throwable? = null) {
        val context = appContext ?: return
        runCatching {
            appendTrimmed(
                File(context.filesDir, ERROR_FILE),
                buildString {
                    appendLine()
                    appendLine("--- ${Date()} · $where ---")
                    error?.let { appendLine(it.stackTraceToString()) }
                },
            )
        }
    }

    private fun appendTrimmed(log: File, text: String) {
        // Recorta para que el registro no crezca sin límite en la tablet.
        if (log.length() > MAX_CRASH_LOG_CHARS * 2L) log.writeText(log.readText().takeLast(MAX_CRASH_LOG_CHARS))
        log.appendText(text)
    }

    fun installCrashCapture(context: Context) {
        appContext = context.applicationContext
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
        fun tail(name: String, empty: String) = File(context.filesDir, name)
            .takeIf(File::exists)
            ?.readText()
            ?.takeLast(MAX_CRASH_LOG_CHARS)
            ?: empty
        val crashes = tail(CRASH_FILE, "Sin cierres inesperados registrados.")
        val errors = tail(ERROR_FILE, "Sin errores registrados.")
        // Sin permisos especiales Android solo deja leer el logcat del propio proceso: avisos y errores recientes.
        val logcat = runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", LOGCAT_LINES.toString(), "--pid=${android.os.Process.myPid()}", "*:W"),
            )
            process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "Sin avisos." }
        }.getOrElse { "No se pudo leer: ${it.message}" }
        return buildString {
            appendLine("CAUDAL APP · REPORTE DE DIAGNÓSTICO")
            appendLine("Generado: ${Date()}")
            appendLine("Versión: ${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            appendLine("Estado local: ${if (state.exists()) "${state.length()} bytes" else "no encontrado"}")
            appendLine()
            appendLine("CIERRES INESPERADOS CAPTURADOS")
            appendLine(crashes)
            appendLine()
            appendLine("ERRORES MANEJADOS")
            appendLine(errors)
            appendLine()
            appendLine("AVISOS RECIENTES DEL SISTEMA (esta sesión de la app)")
            appendLine(logcat)
        }
    }

    private class CaudalCrashHandler(
        private val context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                appendTrimmed(
                    File(context.filesDir, CRASH_FILE),
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
