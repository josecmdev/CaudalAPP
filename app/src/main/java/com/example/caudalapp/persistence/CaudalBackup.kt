package com.example.caudalapp.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Respaldo completo de Caudal App en un solo archivo .zip:
 *  - respaldo.json: datos del respaldo (formato, versión, fecha)
 *  - caudal-state-v1.json: el estado completo, igual al archivo local
 *  - route-tracks/route-<id>.csv: los recorridos GPS
 *
 * La restauración también acepta un caudal-state-v1.json suelto.
 */
object CaudalBackupArchive {
    const val FORMAT = "caudal-backup"
    const val VERSION = 1
    const val MANIFEST_ENTRY = "respaldo.json"
    const val STATE_ENTRY = "caudal-state-v1.json"
    const val TRACKS_PREFIX = "route-tracks/"
    private val TRACK_FILE_PATTERN = Regex("route-[A-Za-z0-9_-]+\\.csv")
    private const val MAX_STATE_BYTES = 64L * 1024 * 1024
    private const val MAX_TRACK_BYTES = 256L * 1024 * 1024

    fun write(
        output: OutputStream,
        stateFile: File,
        tracksDirectory: File,
        createdAtEpochMillis: Long,
        appVersion: String,
    ) {
        require(stateFile.exists()) { "No hay datos guardados para respaldar" }
        val tracks = tracksDirectory.listFiles { file -> file.isFile && file.name.matches(TRACK_FILE_PATTERN) }
            .orEmpty()
            .sortedBy(File::getName)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            val manifest = buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("createdAtEpochMillis", createdAtEpochMillis)
                put("appVersion", appVersion)
                put("routeTrackFiles", tracks.size)
            }
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(STATE_ENTRY))
            stateFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            tracks.forEach { track ->
                zip.putNextEntry(ZipEntry(TRACKS_PREFIX + track.name))
                track.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Lee el respaldo hacia [stagingDirectory] sin tocar los datos actuales.
     * Lanza [IllegalArgumentException] con un mensaje para el usuario si el archivo no sirve.
     */
    fun stage(input: InputStream, stagingDirectory: File): StagedBackup {
        stagingDirectory.deleteRecursively()
        File(stagingDirectory, "route-tracks").mkdirs()
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val isZip = first == 'P'.code && second == 'K'.code
        val stateText = if (isZip) stageZip(buffered, stagingDirectory) else readLimited(buffered, MAX_STATE_BYTES)
        val state = runCatching { CaudalStateCodec.decode(stateText) }.getOrElse {
            throw IllegalArgumentException("El archivo no contiene datos válidos de Caudal App", it)
        }
        val stagedState = File(stagingDirectory, STATE_ENTRY).apply { writeText(stateText, Charsets.UTF_8) }
        val stagedTracks = File(stagingDirectory, "route-tracks").listFiles().orEmpty().toList()
        return StagedBackup(state, stagedState, stagedTracks)
    }

    private fun stageZip(input: InputStream, stagingDirectory: File): String {
        var stateText: String? = null
        var manifestSeen = false
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                when {
                    name == MANIFEST_ENTRY -> {
                        val manifest = runCatching {
                            Json.parseToJsonElement(readLimited(zip, 64 * 1024)).jsonObject
                        }.getOrNull()
                        require(manifest?.get("format")?.jsonPrimitive?.contentOrNull == FORMAT) {
                            "El archivo no es un respaldo de Caudal App"
                        }
                        val version = manifest?.get("version")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        require(version in 1..VERSION) {
                            "El respaldo es de una versión más nueva de Caudal App. Actualiza la aplicación."
                        }
                        manifestSeen = true
                    }
                    name == STATE_ENTRY -> stateText = readLimited(zip, MAX_STATE_BYTES)
                    name.startsWith(TRACKS_PREFIX) -> {
                        // Solo nombres conocidos: evita rutas como "../" dentro del zip.
                        val fileName = name.removePrefix(TRACKS_PREFIX)
                        if (fileName.matches(TRACK_FILE_PATTERN)) {
                            File(stagingDirectory, "route-tracks/$fileName").outputStream().use { out ->
                                copyLimited(zip, out, MAX_TRACK_BYTES)
                            }
                        }
                    }
                }
            }
        }
        require(manifestSeen) { "El archivo no es un respaldo de Caudal App" }
        return requireNotNull(stateText) { "El respaldo no contiene los datos de Caudal App" }
    }

    private fun readLimited(input: InputStream, limit: Long): String {
        val bytes = java.io.ByteArrayOutputStream()
        copyLimited(input, bytes, limit)
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "El archivo es demasiado grande para ser un respaldo de Caudal App" }
            output.write(buffer, 0, read)
        }
    }
}

class StagedBackup(
    val state: CaudalRestoredState,
    internal val stateFile: File,
    internal val trackFiles: List<File>,
)

/**
 * Restaura un respaldo sobre los archivos locales. Antes de reemplazar nada guarda una copia
 * del estado actual en caudal-state-v1.before-restore.json para poder deshacer a mano.
 */
class CaudalBackupRestorer(
    private val stateFile: File,
    private val tracksDirectory: File,
    private val stagingDirectory: File,
) {
    val previousStateCopy: File get() = File(stateFile.parentFile, "caudal-state-v1.before-restore.json")

    fun restore(input: InputStream): CaudalRestoredState {
        try {
            val staged = CaudalBackupArchive.stage(input, stagingDirectory)
            if (stateFile.exists()) stateFile.copyTo(previousStateCopy, overwrite = true)
            tracksDirectory.mkdirs()
            staged.trackFiles.forEach { track ->
                track.copyTo(File(tracksDirectory, track.name), overwrite = true)
            }
            CaudalStateStore(stateFile).save(staged.state)
            return staged.state
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }
}
