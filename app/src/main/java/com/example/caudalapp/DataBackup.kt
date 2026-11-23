package com.example.caudalapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.caudalapp.persistence.CaudalBackupArchive
import com.example.caudalapp.persistence.CaudalBackupRestorer
import com.example.caudalapp.persistence.CaudalRestoredState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exporta y restaura el respaldo completo (datos + recorridos GPS) en un .zip. */
object DataBackup {
    const val STATE_FILE = "caudal-state-v1.json"
    private const val TRACK_DIRECTORY = "route-tracks"
    private const val BACKUP_CACHE_DIRECTORY = "backups"

    /** Crea el archivo de respaldo. Llamar fuera del hilo principal. */
    fun prepare(context: Context): Uri {
        val directory = File(context.cacheDir, BACKUP_CACHE_DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backup = File(directory, "caudal-respaldo-$timestamp.zip")
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        backup.outputStream().use { output ->
            CaudalBackupArchive.write(
                output = output,
                stateFile = File(context.filesDir, STATE_FILE),
                tracksDirectory = File(context.filesDir, TRACK_DIRECTORY),
                createdAtEpochMillis = System.currentTimeMillis(),
                appVersion = versionName,
            )
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
    }

    /** Abre el menú para enviar el respaldo a Drive, correo, WhatsApp, etc. */
    fun share(context: Context, uri: Uri) {
        val timestamp = uri.lastPathSegment.orEmpty().removePrefix("caudal-respaldo-").removeSuffix(".zip")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, "Respaldo Caudal App $timestamp")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Guardar respaldo en").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Reemplaza los datos locales con el respaldo elegido. No cambia nada si el archivo no es válido. */
    fun restore(context: Context, uri: Uri): CaudalRestoredState {
        val restorer = CaudalBackupRestorer(
            stateFile = File(context.filesDir, STATE_FILE),
            tracksDirectory = File(context.filesDir, TRACK_DIRECTORY),
            stagingDirectory = File(context.cacheDir, "restore-staging"),
        )
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "No se pudo abrir el archivo elegido"
        }
        return input.use(restorer::restore)
    }
}
