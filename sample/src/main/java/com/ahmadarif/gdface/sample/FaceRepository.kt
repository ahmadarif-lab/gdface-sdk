package com.ahmadarif.gdface.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One photo of a person. [engineId] is the id it was registered under in the SDK. */
data class FaceImage(val engineId: String, val file: String)

data class Person(val id: String, val name: String, val createdAt: Long, val images: List<FaceImage>)

sealed interface EnrollResult {
    data class Saved(val person: Person, val newPerson: Boolean) : EnrollResult
    data object NoFace : EnrollResult
    data object Failed : EnrollResult
}

data class ImportSummary(val people: Int, val images: Int, val skipped: Int)

/**
 * The people enrolled through this app: their names and photos, kept next to the SDK's own
 * face database (which stores only anonymous features and knows no names).
 *
 * The SDK holds one face per id, so each photo is registered under `<personId>#<n>`: a
 * person with three photos is three faces in the SDK, and a match on any of them is that
 * person. Because the photos are kept, the whole list can be exported and later imported
 * by registering the photos again, which also works across SDK model versions.
 */
class FaceRepository(private val context: Context, private val service: FaceService) {

    private val dir = File(context.filesDir, "faces")
    private val photoDir = File(dir, "photos")
    private val indexFile = File(dir, "faces.json")

    var persons: List<Person> by mutableStateOf(readIndex())
        private set

    fun photoFile(image: FaceImage) = File(photoDir, image.file)

    fun personForEngineId(engineId: String): Person? =
        persons.firstOrNull { person -> person.images.any { it.engineId == engineId } }

    /**
     * Registers the face in [bitmap]. A new photo goes to [personId] when given, else to
     * the person already enrolled under the same name, else to a new person.
     */
    suspend fun enroll(bitmap: Bitmap, name: String, personId: String? = null): EnrollResult {
        val cleanName = name.trim()
        val rect = service.call { it.detectFaceRect(bitmap) } ?: return EnrollResult.NoFace

        val existing = persons.firstOrNull { it.id == personId }
            ?: persons.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        val id = existing?.id ?: newId()
        val number = (existing?.images?.maxOfOrNull { it.engineId.substringAfter('#').toIntOrNull() ?: 0 } ?: 0) + 1
        val engineId = "$id#$number"

        if (!service.call { it.enroll(engineId, bitmap) }) return EnrollResult.Failed

        val image = FaceImage(engineId, "$id-$number.jpg")
        withContext(Dispatchers.IO) { writePhoto(image.file, bitmap.cropFace(rect)) }

        val person = existing?.copy(images = existing.images + image)
            ?: Person(id, cleanName, System.currentTimeMillis(), listOf(image))
        persons = if (existing != null) persons.map { if (it.id == id) person else it } else persons + person
        saveIndex()
        return EnrollResult.Saved(person, newPerson = existing == null)
    }

    suspend fun rename(person: Person, name: String) {
        persons = persons.map { if (it.id == person.id) it.copy(name = name.trim()) else it }
        saveIndex()
    }

    suspend fun delete(person: Person) {
        service.call { engine -> person.images.forEach { engine.unenroll(it.engineId) } }
        withContext(Dispatchers.IO) { person.images.forEach { photoFile(it).delete() } }
        persons = persons.filter { it.id != person.id }
        saveIndex()
    }

    /** Empties the SDK's database too, including faces this app did not enroll. */
    suspend fun clearAll() {
        service.call { it.clearAll() }
        withContext(Dispatchers.IO) { photoDir.listFiles()?.forEach { it.delete() } }
        persons = emptyList()
        saveIndex()
    }

    /** Writes every person and photo to [uri] as a ZIP file. */
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri) ?: error("Could not open the file")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(INDEX_ENTRY))
            zip.write(indexJson(persons).toString().toByteArray())
            zip.closeEntry()
            persons.flatMap { it.images }.forEach { image ->
                val file = photoFile(image)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry("$PHOTO_ENTRY_DIR${image.file}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Adds the people of a file written by [export]. Photos are registered with the SDK
     * again; people that are already here (same id) and photos without a face are skipped.
     */
    suspend fun import(uri: Uri): ImportSummary {
        val entries = withContext(Dispatchers.IO) { readZip(uri) }
        val incoming = parseIndex(entries[INDEX_ENTRY]?.toString(Charsets.UTF_8) ?: error("This is not a GdFace export"))

        var people = 0
        var images = 0
        var skipped = 0
        for (person in incoming) {
            if (persons.any { it.id == person.id }) {
                skipped += person.images.size
                continue
            }
            val added = mutableListOf<FaceImage>()
            for (image in person.images) {
                val bytes = entries["$PHOTO_ENTRY_DIR${image.file}"]
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bitmap == null || !service.call { it.enroll(image.engineId, bitmap) }) {
                    skipped++
                    continue
                }
                withContext(Dispatchers.IO) { writePhoto(image.file, bitmap) }
                added += image
            }
            if (added.isNotEmpty()) {
                persons = persons + person.copy(images = added)
                people++
                images += added.size
            }
        }
        saveIndex()
        return ImportSummary(people, images, skipped)
    }

    // ---------------------------------------------------------------- storage

    private suspend fun saveIndex() = withContext(Dispatchers.IO) {
        dir.mkdirs()
        indexFile.writeText(indexJson(persons).toString())
    }

    private fun writePhoto(name: String, bitmap: Bitmap) {
        photoDir.mkdirs()
        File(photoDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun readIndex(): List<Person> = try {
        if (indexFile.exists()) parseIndex(indexFile.readText()) else emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the face list, starting empty", e)
        emptyList()
    }

    private fun readZip(uri: Uri): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0L
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    total += n
                    // A guard against a crafted file, far above what an export holds.
                    if (buffer.size() + n > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) error("The file is too large")
                    buffer.write(chunk, 0, n)
                }
                entries[entry.name] = buffer.toByteArray()
            }
        }
        return entries
    }

    private fun indexJson(list: List<Person>) = JSONObject()
        .put("version", 1)
        .put("persons", JSONArray().also { array ->
            list.forEach { person ->
                array.put(
                    JSONObject()
                        .put("id", person.id)
                        .put("name", person.name)
                        .put("createdAt", person.createdAt)
                        .put("images", JSONArray().also { images ->
                            person.images.forEach {
                                images.put(JSONObject().put("engineId", it.engineId).put("file", it.file))
                            }
                        })
                )
            }
        })

    /** Reads an index and drops whatever does not have the shape this app writes: an
     * imported file is not trusted, and its names end up in file paths and SDK ids. */
    private fun parseIndex(text: String): List<Person> {
        val array = JSONObject(text).getJSONArray("persons")
        val result = mutableListOf<Person>()
        for (i in 0 until array.length()) {
            val p = array.getJSONObject(i)
            val id = p.getString("id")
            if (!ID_PATTERN.matches(id)) continue
            val images = mutableListOf<FaceImage>()
            val imageArray = p.getJSONArray("images")
            for (j in 0 until imageArray.length()) {
                val image = imageArray.getJSONObject(j)
                val engineId = image.getString("engineId")
                val file = image.getString("file")
                if (ENGINE_ID_PATTERN.matches(engineId) && engineId.startsWith("$id#") && PHOTO_PATTERN.matches(file)) {
                    images += FaceImage(engineId, file)
                }
            }
            val name = p.getString("name").trim().take(MAX_NAME_LENGTH)
            if (name.isNotEmpty() && images.isNotEmpty()) result += Person(id, name, p.optLong("createdAt"), images)
        }
        return result
    }

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(8)

    private companion object {
        const val TAG = "FaceRepository"
        const val INDEX_ENTRY = "faces.json"
        const val PHOTO_ENTRY_DIR = "photos/"
        const val MAX_ENTRY_BYTES = 10 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
        const val MAX_NAME_LENGTH = 60
        val ID_PATTERN = Regex("[0-9a-f]{8}")
        val ENGINE_ID_PATTERN = Regex("[0-9a-f]{8}#[0-9]{1,6}")
        val PHOTO_PATTERN = Regex("[0-9a-f]{8}-[0-9]{1,6}\\.jpg")
    }
}

/** A square crop around [rect], with room around the face, at most [maxSize] pixels. */
fun Bitmap.cropFace(rect: Rect, maxSize: Int = 512): Bitmap {
    val side = (maxOf(rect.width(), rect.height()) * 1.6f).toInt().coerceIn(1, minOf(width, height))
    val left = (rect.centerX() - side / 2).coerceIn(0, width - side)
    val top = (rect.centerY() - side / 2).coerceIn(0, height - side)
    val square = Bitmap.createBitmap(this, left, top, side, side)
    return if (side > maxSize) Bitmap.createScaledBitmap(square, maxSize, maxSize, true) else square
}
