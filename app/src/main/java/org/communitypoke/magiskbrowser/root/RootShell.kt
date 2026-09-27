package org.communitypoke.magiskbrowser.root

import android.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Minimal `su` wrapper — no external dependency. All calls are synchronous and
 * must be run off the main thread. Root is entirely optional: every caller
 * checks [isAvailable] first and degrades gracefully.
 */
object RootShell {

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    @Volatile
    private var cachedAvailability: Boolean? = null

    @Synchronized
    fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        val ok = try {
            exec("id", 10).let { it.ok && it.output.contains("uid=0") }
        } catch (_: Exception) {
            false
        }
        cachedAvailability = ok
        return ok
    }

    fun invalidateCache() {
        cachedAvailability = null
    }

    /**
     * Runs `su -c <command>`. stderr is merged into stdout (avoids a
     * stderr-buffer deadlock and is enough for diagnostics).
     */
    fun exec(command: String, timeoutSec: Long = 15): Result {
        val proc = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        proc.outputStream.close()
        val output = try {
            proc.inputStream.readBytes().toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return Result(-1, output + "\n<timed out after ${timeoutSec}s>")
        }
        return Result(proc.exitValue(), output)
    }

    fun readFile(path: String): String? {
        val r = exec("cat ${shellQuote(path)}")
        return if (r.ok) r.output else null
    }

    /** Writes [content] via base64 so quoting/binary-safe content is not an issue. */
    fun writeFile(path: String, content: String): Boolean {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return exec("echo '$b64' | base64 -d > ${shellQuote(path)}").ok
    }

    private fun shellQuote(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"
}
