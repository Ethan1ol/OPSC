package com.example.opsc.media

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Where uploaded images are stored. The app only talks to this interface, so the provider can be
 * changed without touching any screen. Today it is [FirestoreBlobStore] (works on the free Firebase
 * plan); a Firebase Storage version (needs the Blaze plan) would be one more class.
 *
 * A [path] names the item, for example `users/{uid}/media/avatar`.
 */
interface BlobStore {
    /** Saves [bytes] at [path]. Returns false if it could not be saved (offline, too large, not allowed). */
    suspend fun put(path: String, bytes: ByteArray, mime: String, version: Long): Boolean

    /** Reads the bytes at [path], or null if there is nothing there or the read failed. */
    suspend fun get(path: String): ByteArray?

    /** Removes the item at [path]. Returns false on failure. */
    suspend fun delete(path: String): Boolean
}

/**
 * Stores images as Blob fields in Firestore documents (limit 1 MiB each, so images are compressed
 * first). Free-plan friendly. Reads and writes are covered by firestore.rules.
 */
class FirestoreBlobStore(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : BlobStore {

    override suspend fun put(path: String, bytes: ByteArray, mime: String, version: Long): Boolean = try {
        if (bytes.size > ImageMath.MAX_BYTES) {
            Log.w(TAG, "Refusing to store ${bytes.size} bytes at $path (too large)")
            false
        } else {
            withTimeoutOrNull(TIMEOUT_MS) {
                db.document(path).set(mapOf("data" to Blob.fromBytes(bytes), "mime" to mime, "updatedAt" to version)).await()
                true
            } ?: false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Could not store $path", e)
        false
    }

    override suspend fun get(path: String): ByteArray? = try {
        withTimeoutOrNull(TIMEOUT_MS) { db.document(path).get().await().getBlob("data")?.toBytes() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read $path", e)
        null
    }

    override suspend fun delete(path: String): Boolean = try {
        withTimeoutOrNull(TIMEOUT_MS) { db.document(path).delete().await(); true } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Could not delete $path", e)
        false
    }

    private companion object {
        const val TAG = "FirestoreBlobStore"
        const val TIMEOUT_MS = 20_000L
    }
}

/**
 * Keeps a copy of every image on the phone, so pictures show instantly, survive restarts and work
 * offline. Each file remembers which version it holds, so it is only downloaded again when the
 * version on the server changes.
 */
class MediaRepository private constructor(context: Context, private val store: BlobStore = FirestoreBlobStore()) {
    private val directory = File(context.applicationContext.filesDir, "media").apply { mkdirs() }
    private val prefs = context.applicationContext.getSharedPreferences("battle_fitness_media", Context.MODE_PRIVATE)

    private fun fileFor(path: String) = File(directory, path.replace('/', '_') + ".jpg")

    /** The cached file for [path], or null if nothing is cached yet. */
    fun cached(path: String): File? = fileFor(path).takeIf { it.exists() && it.length() > 0 }

    /**
     * Makes sure the phone has version [remoteVersion] of [path] and returns the file. Downloads it
     * only if the cached copy is missing or older. Returns the old cached file if the download fails
     * (for example offline), or null if there is nothing at all.
     */
    suspend fun ensureLocal(path: String, remoteVersion: Long): File? {
        if (remoteVersion <= 0L) return cached(path)
        val existing = cached(path)
        if (existing != null && prefs.getLong(path, 0L) == remoteVersion) return existing
        val bytes = store.get(path) ?: return existing
        return write(path, bytes, remoteVersion)
    }

    /** Uploads an image and caches it. Returns the cached file, or null if the upload failed. */
    suspend fun upload(path: String, bytes: ByteArray, version: Long): File? {
        if (!store.put(path, bytes, "image/jpeg", version)) return null
        return write(path, bytes, version)
    }

    /** Removes the image from the server and the phone. */
    suspend fun remove(path: String): Boolean {
        val ok = store.delete(path)
        if (ok) {
            fileFor(path).delete()
            prefs.edit().remove(path).apply()
        }
        return ok
    }

    private suspend fun write(path: String, bytes: ByteArray, version: Long): File = withContext(Dispatchers.IO) {
        val file = fileFor(path)
        file.writeBytes(bytes)
        prefs.edit().putLong(path, version).apply()
        file
    }

    companion object {
        @Volatile private var instance: MediaRepository? = null

        fun get(context: Context): MediaRepository = instance ?: synchronized(this) {
            instance ?: MediaRepository(context.applicationContext).also { instance = it }
        }

        /** Where a user's profile picture lives. */
        fun avatarPath(uid: String) = "users/$uid/media/avatar"

        /** Where a user's profile banner lives. */
        fun bannerPath(uid: String) = "users/$uid/media/banner"

        /** Where the referee's proof photo for a fixture lives. */
        fun proofPath(clubId: String, fixtureId: String) = "clubs/$clubId/fixtures/$fixtureId/proof/photo"
    }
}
