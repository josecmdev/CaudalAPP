package com.example.caudalapp.persistence

import com.example.caudalapp.domain.GeoPoint
import com.example.caudalapp.domain.StoreChangeResult
import com.example.caudalapp.domain.StoreDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CaudalBackupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun stateWithStore(name: String): CaudalRestoredState {
        val stores = StoreDirectory { "store-$name" }
        stores.addStore(name, GeoPoint(14.63, -90.50)) as StoreChangeResult.Success
        return CaudalRestoredState(activeRoute = null, stores = stores, completedRoutes = emptyList())
    }

    private fun device(name: String): Triple<File, File, File> {
        val root = temporaryFolder.newFolder(name)
        return Triple(File(root, "caudal-state-v1.json"), File(root, "route-tracks"), File(root, "staging"))
    }

    private fun restorer(device: Triple<File, File, File>) =
        CaudalBackupRestorer(device.first, device.second, device.third)

    @Test
    fun `el respaldo exportado se restaura completo en otra tablet`() {
        val original = device("original")
        CaudalStateStore(original.first).save(stateWithStore("Tienda Azul"))
        original.second.mkdirs()
        File(original.second, "route-10.csv").writeText("10,14.6,-90.5,,\n")
        File(original.second, "otro-archivo.txt").writeText("no se exporta")

        val backup = ByteArrayOutputStream()
        CaudalBackupArchive.write(backup, original.first, original.second, 1_000L, "1.0")

        val replacement = device("nueva")
        CaudalStateStore(replacement.first).save(stateWithStore("Tienda Vieja"))
        val restored = restorer(replacement).restore(ByteArrayInputStream(backup.toByteArray()))

        assertEquals(listOf("Tienda Azul"), restored.stores.allStores().map { it.name })
        assertEquals(listOf("Tienda Azul"), CaudalStateStore(replacement.first).load()!!.stores.allStores().map { it.name })
        assertEquals("10,14.6,-90.5,,\n", File(replacement.second, "route-10.csv").readText())
        assertFalse(File(replacement.second, "otro-archivo.txt").exists())
        val previous = CaudalStateCodec.decode(restorer(replacement).previousStateCopy.readText())
        assertEquals(listOf("Tienda Vieja"), previous.stores.allStores().map { it.name })
        assertFalse(replacement.third.exists())
    }

    @Test
    fun `tambien acepta el archivo de estado suelto`() {
        val target = device("json")
        val json = CaudalStateCodec.encode(stateWithStore("Tienda Suelta"))

        val restored = restorer(target).restore(ByteArrayInputStream(json.toByteArray()))

        assertEquals(listOf("Tienda Suelta"), restored.stores.allStores().map { it.name })
        assertTrue(target.first.exists())
    }

    @Test
    fun `un archivo invalido no toca los datos actuales`() {
        val target = device("invalido")
        CaudalStateStore(target.first).save(stateWithStore("Tienda Segura"))
        val before = target.first.readText()

        listOf(
            "esto no es json".toByteArray(),
            zip("respaldo.json" to """{"format":"otra-app","version":1}"""),
            zip("respaldo.json" to """{"format":"caudal-backup","version":99}""", "caudal-state-v1.json" to "{}"),
            zip("respaldo.json" to """{"format":"caudal-backup","version":1}"""),
            zip("respaldo.json" to """{"format":"caudal-backup","version":1}""", "caudal-state-v1.json" to "roto"),
        ).forEach { bytes ->
            try {
                restorer(target).restore(ByteArrayInputStream(bytes))
                fail("Debió rechazar el archivo")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotBlank())
            }
            assertEquals(before, target.first.readText())
            assertFalse(target.third.exists())
        }
    }

    @Test
    fun `ignora entradas del zip fuera de las carpetas conocidas`() {
        val target = device("seguro")
        val state = CaudalStateCodec.encode(stateWithStore("Tienda Zip"))
        val bytes = zip(
            "respaldo.json" to """{"format":"caudal-backup","version":1}""",
            "caudal-state-v1.json" to state,
            "route-tracks/../../escape.csv" to "malo",
            "../escape.json" to "malo",
        )

        restorer(target).restore(ByteArrayInputStream(bytes))

        assertFalse(File(target.first.parentFile.parentFile, "escape.csv").exists())
        assertFalse(File(target.first.parentFile.parentFile, "escape.json").exists())
        assertEquals(emptyList<String>(), target.second.list().orEmpty().toList())
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
