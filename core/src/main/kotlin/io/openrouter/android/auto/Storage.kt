package io.openrouter.android.auto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

// ==================== Storage Interface ====================

/**
 * Pluggable storage for the SDK — models cache, model configs, preferences, etc.
 * All operations are suspend functions (async-ready).
 */
interface StorageAdapter {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

// ==================== Storage Keys ====================

object StorageKeys {
    const val MODELS = "models"
    const val MODEL_CONFIGS = "model_configs"
    const val USER_PREFERENCES = "user_preferences"
    const val LAST_FETCH = "last_fetch"
}

// ==================== Sensitive Key Patterns ====================

/** Regex matching keys that hold secrets and must never be persisted. */
val SENSITIVE_KEY_PATTERN = Regex("api[_-]?key|api[_-]?secret|Authorization", RegexOption.IGNORE_CASE)

// ==================== Memory Storage ====================

/**
 * Volatile in-memory storage backed by a ConcurrentHashMap-equivalent.
 * Thread-safe via [Mutex]. Ideal for testing and CLI.
 */
class MemoryStorage : StorageAdapter {

    private val store = mutableMapOf<String, String>()
    private val mutex = Mutex()

    override suspend fun get(key: String): String? = mutex.withLock { store[key] }

    override suspend fun set(key: String, value: String) {
        mutex.withLock { store[key] = value }
    }

    override suspend fun remove(key: String) {
        mutex.withLock { store.remove(key) }
    }

    override suspend fun clear() {
        mutex.withLock { store.clear() }
    }
}

// ==================== File Storage ====================

/**
 * File-backed storage that persists a single JSON file.
 *
 * Security:
 * - Path-traversal prevention: resolves canonical path and asserts it falls under
 *   `user.home` or `user.dir`.
 * - Owner-only file permissions (rw-------) on platforms that support POSIX.
 */
class FileStorage(configPath: String) : StorageAdapter {

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** Allowed root directories for path-traversal prevention. */
        private fun allowedRoots(): List<String> = listOfNotNull(
            System.getProperty("user.home")?.let { File(it).canonicalPath },
            System.getProperty("user.dir")?.let { File(it).canonicalPath }
        )
    }

    private val file: File
    private val mutex = Mutex()
    private var data: MutableMap<String, String> = mutableMapOf()

    init {
        file = File(configPath)
        val canonicalPath = file.canonicalPath
        val roots = allowedRoots()

        val isWithinAllowedRoot = roots.any { root ->
            canonicalPath.startsWith(root)
        }

        check(isWithinAllowedRoot) {
            "Config path '$configPath' resolves to '$canonicalPath' which is outside " +
                "allowed roots ($roots). Path traversal is not allowed."
        }

        load()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val text = file.readText(Charsets.UTF_8)
                data = json.decodeFromString<Map<String, String>>(text).toMutableMap()
            }
        } catch (_: Exception) {
            data = mutableMapOf()
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val text = json.encodeToString(data.toMap())
            file.writeText(text, Charsets.UTF_8)

            try {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                    )
                )
            } catch (_: UnsupportedOperationException) {
                // Non-POSIX filesystems (e.g. Windows FAT) silently skip
            }
        } catch (_: Exception) {
            // Save failure is non-fatal; in-memory state remains valid
        }
    }

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) { mutex.withLock { data[key] } }

    override suspend fun set(key: String, value: String) =
        withContext(Dispatchers.IO) { mutex.withLock { data[key] = value; save() } }

    override suspend fun remove(key: String) =
        withContext(Dispatchers.IO) { mutex.withLock { data.remove(key); save() } }

    override suspend fun clear() =
        withContext(Dispatchers.IO) { mutex.withLock { data.clear(); save() } }
}
