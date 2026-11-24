package com.speedora.app.core.security

import java.io.File

/**
 * Sanitizes untrusted peer-provided filenames and confines resolved destination files
 * strictly within the authorized target directory to prevent path traversal attacks.
 */
object FilenameSanitizer {
    private const val MAX_FILENAME_LENGTH = 180
    private val INVALID_CHARS_REGEX = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val RESERVED_NAMES = setOf(
        ".", "..", "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    fun sanitize(rawName: String?, fallbackPrefix: String = "speedora_file"): String {
        if (rawName.isNullOrBlank()) return "${fallbackPrefix}_${System.currentTimeMillis()}"

        // Strip any directory segments (Unix or Windows separators) and null bytes
        val stripped = rawName
            .replace("\u0000", "")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .trimStart('.')

        val cleaned = INVALID_CHARS_REGEX.replace(stripped, "_")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank() || RESERVED_NAMES.contains(cleaned.uppercase())) {
            return "${fallbackPrefix}_${System.currentTimeMillis()}"
        }

        if (cleaned.length <= MAX_FILENAME_LENGTH) {
            return cleaned
        }

        val lastDot = cleaned.lastIndexOf('.')
        return if (lastDot in 1 until cleaned.length - 1) {
            val ext = cleaned.substring(lastDot).take(16)
            val baseMax = (MAX_FILENAME_LENGTH - ext.length).coerceAtLeast(8)
            cleaned.substring(0, baseMax) + ext
        } else {
            cleaned.take(MAX_FILENAME_LENGTH)
        }
    }

    /**
     * Resolves a sanitized filename inside [baseDir] and verifies canonical path confinement.
     */
    fun resolveConfinedFile(baseDir: File, untrustedFileName: String): File {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val safeName = sanitize(untrustedFileName)
        var candidate = File(baseDir, safeName)

        // Ensure strict canonical confinement
        val baseCanonical = baseDir.canonicalPath
        val candidateCanonical = candidate.canonicalPath
        require(candidateCanonical.startsWith(baseCanonical + File.separator)) {
            "Path confinement violation for $untrustedFileName"
        }

        // Non-destructive collision avoidance
        if (candidate.exists()) {
            val dotIndex = safeName.lastIndexOf('.')
            val base = if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
            val ext = if (dotIndex > 0) safeName.substring(dotIndex) else ""
            var counter = 1
            while (candidate.exists() && counter < 10_000) {
                candidate = File(baseDir, "$base ($counter)$ext")
                counter++
            }
        }
        return candidate
    }
}
