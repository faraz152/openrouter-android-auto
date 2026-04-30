package io.openrouter.android.auto

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for Storage.kt — StorageAdapter interface, MemoryStorage, FileStorage.
 * SharedPrefsStorage requires Android Context, so its static stripSensitiveJson()
 * is tested here; the storage methods themselves require instrumented tests.
 */
class StorageTest {

    // ─── MemoryStorage ───────────────────────────────────────────────────────

    @Nested
    inner class MemoryStorageTests {

        private val storage = MemoryStorage()

        @Test
        fun `get returns null for unset key`() = runBlocking {
            assertNull(storage.get("nonexistent"))
        }

        @Test
        fun `set then get returns value`() = runBlocking {
            storage.set("key1", "value1")
            assertEquals("value1", storage.get("key1"))
        }

        @Test
        fun `set overwrites existing value`() = runBlocking {
            storage.set("key", "first")
            storage.set("key", "second")
            assertEquals("second", storage.get("key"))
        }

        @Test
        fun `remove deletes key`() = runBlocking {
            storage.set("key", "value")
            storage.remove("key")
            assertNull(storage.get("key"))
        }

        @Test
        fun `clear removes all keys`() = runBlocking {
            storage.set("a", "1")
            storage.set("b", "2")
            storage.clear()
            assertNull(storage.get("a"))
            assertNull(storage.get("b"))
        }

        @Test
        fun `remove nonexistent key is safe`() = runBlocking {
            storage.remove("nonexistent")
            assertNull(storage.get("nonexistent"))
        }

        @Test
        fun `getStorageKeys returns correct constants`() {
            assertEquals("models", StorageKeys.MODELS)
            assertEquals("model_configs", StorageKeys.MODEL_CONFIGS)
            assertEquals("user_preferences", StorageKeys.USER_PREFERENCES)
            assertEquals("last_fetch", StorageKeys.LAST_FETCH)
        }
    }

    // ─── MemoryStorage Concurrency ───────────────────────────────────────────

    @Nested
    inner class MemoryStorageConcurrency {

        @Test
        fun `concurrent set and get are thread-safe`() = runBlocking {
            val storage = MemoryStorage()
            val count = 100
            val jobs = mutableListOf<Job>()

            // Launch parallel writers
            repeat(count) { i ->
                jobs += launch {
                    storage.set("key_$i", "value_$i")
                }
            }

            // Launch parallel readers
            repeat(count) { i ->
                jobs += launch {
                    storage.get("key_$i")
                }
            }

            jobs.joinAll()

            // All written values should be present
            repeat(count) { i ->
                assertEquals("value_$i", storage.get("key_$i"))
            }
        }

        @Test
        fun `concurrent clear and set do not corrupt state`() = runBlocking {
            val storage = MemoryStorage()
            val iterations = 50
            val jobs = mutableListOf<Job>()

            repeat(iterations) { i ->
                jobs += launch { storage.set("k", "v_$i") }
                jobs += launch { storage.clear() }
            }

            jobs.joinAll()
            // No assertion needed — test passes if no exception or deadlock
        }
    }

    // ─── FileStorage CRUD ────────────────────────────────────────────────────

    @Nested
    inner class FileStorageCrud {

        private val testDir = File(System.getProperty("user.dir"), ".test-storage-${System.currentTimeMillis()}")

        @AfterEach
        fun cleanup() {
            testDir.deleteRecursively()
        }

        private fun testFile(name: String): File {
            testDir.mkdirs()
            return File(testDir, name)
        }

        @Test
        fun `set then get returns persisted value`() = runBlocking {
            val storage = FileStorage(testFile("config.json").absolutePath)
            storage.set("foo", "bar")
            assertEquals("bar", storage.get("foo"))
        }

        @Test
        fun `new instance loads from existing file`() = runBlocking {
            val path = testFile("persist.json").absolutePath
            FileStorage(path).set("models", "[\"a\",\"b\"]")
            assertEquals("[\"a\",\"b\"]", FileStorage(path).get("models"))
        }

        @Test
        fun `remove deletes key from file`() = runBlocking {
            val storage = FileStorage(testFile("remove.json").absolutePath)
            storage.set("keep", "yes")
            storage.set("gone", "no")
            storage.remove("gone")
            assertEquals("yes", storage.get("keep"))
            assertNull(storage.get("gone"))
        }

        @Test
        fun `clear wipes file data`() = runBlocking {
            val path = testFile("clear.json").absolutePath
            val s1 = FileStorage(path)
            s1.set("a", "1")
            s1.set("b", "2")
            s1.clear()
            val s2 = FileStorage(path)
            assertNull(s2.get("a"))
            assertNull(s2.get("b"))
        }

        @Test
        fun `file is created on first set`() = runBlocking {
            val file = testFile("new.json")
            assertFalse(file.exists())
            val storage = FileStorage(file.absolutePath)
            storage.set("init", "true")
            assertTrue(file.exists())
        }
    }

    // ─── FileStorage Security ─────────────────────────────────────────────────

    @Nested
    inner class FileStorageSecurity {

        @Test
        fun `path traversal outside allowed roots is rejected`() {
            val exception = assertThrows(IllegalStateException::class.java) {
                FileStorage("/etc/passwd")
            }
            assertTrue(exception.message!!.contains("outside"))
        }

        @Test
        fun `path under user dir is allowed`() = runBlocking {
            val dir = File(System.getProperty("user.dir"), ".test-security-${System.currentTimeMillis()}")
            val file = File(dir, "allowed.json")
            try {
                val storage = FileStorage(file.absolutePath)
                storage.set("safe", "data")
                assertEquals("data", storage.get("safe"))
            } finally {
                dir.deleteRecursively()
            }
        }

        @Test
        fun `relative path is rejected if outside allowed roots`() {
            val exception = assertThrows(IllegalStateException::class.java) {
                FileStorage("../../../../../../etc/shadow")
            }
            assertTrue(exception.message!!.contains("outside"))
        }
    }

    // ─── SharedPrefsStorage stripSensitiveJson ───────────────────────────────

    @Nested
    inner class SharedPrefsStripSensitiveJson {

        @Test
        fun `apiKey is stripped from JSON`() {
            val input = """{"name":"test","apiKey":"sk-or-123","theme":"dark"}"""
            val output = SharedPrefsStorage.stripSensitiveJson(input)
            assertFalse(output.contains("sk-or-123"), "API key should be stripped")
            assertTrue(output.contains("name"))
            assertTrue(output.contains("theme"))
        }

        @Test
        fun `api_key snake_case is stripped`() {
            val input = """{"user":"u","api_key":"secret"}"""
            val output = SharedPrefsStorage.stripSensitiveJson(input)
            assertFalse(output.contains("secret"))
            assertTrue(output.contains("user"))
        }

        @Test
        fun `Authorization header is stripped`() {
            val input = """{"Authorization":"Bearer xyz","data":"hello"}"""
            val output = SharedPrefsStorage.stripSensitiveJson(input)
            assertFalse(output.contains("xyz"))
            assertTrue(output.contains("hello"))
        }

        @Test
        fun `non-JSON string is returned unchanged`() {
            assertEquals("plain text", SharedPrefsStorage.stripSensitiveJson("plain text"))
        }

        @Test
        fun `empty object is handled gracefully`() {
            val output = SharedPrefsStorage.stripSensitiveJson("{}")
            assertTrue(output.contains("{}") || output.isBlank())
        }

        @Test
        fun `JSON without sensitive keys is preserved as-is`() {
            val input = """{"model":"gpt-4","temperature":0.7}"""
            val output = SharedPrefsStorage.stripSensitiveJson(input)
            assertTrue(output.contains("model"))
            assertTrue(output.contains("gpt-4"))
        }
    }

    // ─── SENSITIVE_KEY_PATTERN regex ─────────────────────────────────────────

    @Nested
    inner class SensitiveKeyPattern {

        @Test
        fun `matches apiKey camelCase`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("apiKey"))
        }

        @Test
        fun `matches api_key snake_case`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("api_key"))
        }

        @Test
        fun `matches api-key kebab`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("api-key"))
        }

        @Test
        fun `matches apiSecret`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("apiSecret"))
        }

        @Test
        fun `matches api_secret`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("api_secret"))
        }

        @Test
        fun `matches Authorization`() {
            assertTrue(SENSITIVE_KEY_PATTERN.matches("Authorization"))
        }

        @Test
        fun `does not match regular keys`() {
            assertFalse(SENSITIVE_KEY_PATTERN.matches("model"))
            assertFalse(SENSITIVE_KEY_PATTERN.matches("temperature"))
            assertFalse(SENSITIVE_KEY_PATTERN.matches("username"))
        }
    }
}
