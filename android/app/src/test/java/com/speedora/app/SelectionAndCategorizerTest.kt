package com.speedora.app

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.FileSizeFormatter
import com.speedora.app.core.model.LocalFileItem
import com.speedora.app.core.model.SortOrder
import com.speedora.app.data.files.FileCategorizer
import com.speedora.app.data.files.SelectionManager
import org.junit.Test
import org.mockito.Mockito

class SelectionAndCategorizerTest {

    private fun testUri(): Uri = Mockito.mock(Uri::class.java)

    @Test
    fun `categorizes extensions and mime types accurately`() {
        assertThat(FileCategorizer.categorize("vacation.jpg", "image/jpeg")).isEqualTo(FileCategory.PHOTOS)
        assertThat(FileCategorizer.categorize("clip.mp4", "video/mp4")).isEqualTo(FileCategory.VIDEOS)
        assertThat(FileCategorizer.categorize("track.flac", "audio/flac")).isEqualTo(FileCategory.MUSIC)
        assertThat(FileCategorizer.categorize("app.apk", null)).isEqualTo(FileCategory.APPS)
        assertThat(FileCategorizer.categorize("contract.pdf", "application/pdf")).isEqualTo(FileCategory.DOCUMENTS)
        assertThat(FileCategorizer.categorize("backup.zip", "application/zip")).isEqualTo(FileCategory.ARCHIVES)
    }

    @Test
    fun `selection manager persists selections across categories and computes byte totals`() {
        val manager = SelectionManager()
        val photo = LocalFileItem("1", testUri(), "a.jpg", 1024L, "image/jpeg", FileCategory.PHOTOS, 100L)
        val doc = LocalFileItem("2", testUri(), "b.pdf", 2048L, "application/pdf", FileCategory.DOCUMENTS, 200L)

        manager.toggle(photo)
        manager.toggle(doc)
        assertThat(manager.selection.value.totalCount).isEqualTo(2)
        assertThat(manager.selection.value.totalBytes).isEqualTo(3072L)

        manager.removeById("1")
        assertThat(manager.selection.value.totalCount).isEqualTo(1)
        assertThat(manager.selection.value.totalBytes).isEqualTo(2048L)

        manager.clear()
        assertThat(manager.selection.value.totalCount).isEqualTo(0)
    }

    @Test
    fun `sorting and size formatting produce expected results`() {
        val a = LocalFileItem("1", testUri(), "zeta.txt", 100L, "text/plain", FileCategory.DOCUMENTS, 10L)
        val b = LocalFileItem("2", testUri(), "alpha.txt", 500L, "text/plain", FileCategory.DOCUMENTS, 50L)

        assertThat(FileCategorizer.sortFiles(listOf(a, b), SortOrder.LARGEST).first().id).isEqualTo("2")
        assertThat(FileCategorizer.sortFiles(listOf(a, b), SortOrder.NAME_ASC).first().id).isEqualTo("2")
        assertThat(FileSizeFormatter.format(1024L * 1024L)).isEqualTo("1.00 MB")
    }
}
