package com.speedora.app

import com.google.common.truth.Truth.assertThat
import com.speedora.app.core.security.FilenameSanitizer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilenameSanitizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `strips path traversal unix and windows separators`() {
        assertThat(FilenameSanitizer.sanitize("../../etc/passwd")).isEqualTo("passwd")
        assertThat(FilenameSanitizer.sanitize("..\\..\\Windows\\System32\\cmd.exe")).isEqualTo("cmd.exe")
        assertThat(FilenameSanitizer.sanitize("report\u0000.pdf")).isEqualTo("report.pdf")
    }

    @Test
    fun `replaces reserved names and keeps long extensions`() {
        val con = FilenameSanitizer.sanitize("CON")
        assertThat(con).startsWith("speedora_file_")

        val longName = "a".repeat(250) + ".mp4"
        val sanitized = FilenameSanitizer.sanitize(longName)
        assertThat(sanitized.length).isAtMost(180)
        assertThat(sanitized).endsWith(".mp4")
    }

    @Test
    fun `resolves confined file inside base directory and avoids collisions`() {
        val baseDir = tempFolder.newFolder("received")
        val first = FilenameSanitizer.resolveConfinedFile(baseDir, "../photo.jpg")
        assertThat(first.canonicalPath).startsWith(baseDir.canonicalPath)
        assertThat(first.name).isEqualTo("photo.jpg")

        first.writeText("one")
        val second = FilenameSanitizer.resolveConfinedFile(baseDir, "photo.jpg")
        assertThat(second.name).isEqualTo("photo (1).jpg")
    }
}
