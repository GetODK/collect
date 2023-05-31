package org.odk.collect.android.formmanagement

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.odk.collect.forms.FormSource
import org.odk.collect.forms.ManifestFile
import org.odk.collect.forms.MediaFile
import org.odk.collect.formstest.FormFixtures
import org.odk.collect.formstest.InMemFormsRepository
import org.odk.collect.shared.TempFiles
import org.odk.collect.shared.strings.Md5
import java.io.File

class FormMediaDownloaderTest {

    @Test
    fun `myTest`() {
        for (i in 1..1000) {
            // Save forms
            val formsRepository = InMemFormsRepository()
            val form1 = FormFixtures.form(
                version = "1",
                date = 1,
                mediaFiles = listOf(Pair("file", "old"))
            )
            formsRepository.save(form1)

            val form2 = FormFixtures.form(
                version = "2",
                date = 2,
                mediaFiles = listOf(Pair("file", "existing"))
            )
            formsRepository.save(form2)

            val allFormVersions = formsRepository.getAllByFormId(form1.formId)
            assertThat(allFormVersions[0].version, equalTo(form1.version))
            assertThat(allFormVersions[1].version, equalTo(form2.version))

            val sortedForms =  allFormVersions.sortedByDescending {
                it.date
            }

            assertThat(sortedForms[0].version, equalTo(form2.version))
            assertThat(sortedForms[1].version, equalTo(form1.version))
        }
    }

    @Test
    fun `returns false when there is an existing copy of a media file and an older one`() {
        // Save forms
        val formsRepository = InMemFormsRepository()

        val form1 = FormFixtures.form(
            version = "1",
            date = 1,
            mediaFiles = listOf(Pair("file", "old"))
        )

        formsRepository.save(form1)

        val form2 = FormFixtures.form(
            version = "2",
            date = 2,
            mediaFiles = listOf(Pair("file", "existing"))
        )
        formsRepository.save(form2)

        // Set up same media file on server
        val existingMediaFileHash = Md5.getMd5Hash(File(form2.formMediaPath, "file"))!!
        val mediaFile = MediaFile("file", existingMediaFileHash, "downloadUrl")
        val manifestFile = ManifestFile(null, listOf(mediaFile))
        val serverFormDetails =
            ServerFormDetails(null, null, "formId", "3", null, false, true, manifestFile)
        val formSource = mock<FormSource> {
            on { fetchMediaFile(mediaFile.downloadUrl) } doReturn "existing".toByteArray()
                .inputStream()
        }

        val formMediaDownloader = FormMediaDownloader(formsRepository, formSource)
        val result = formMediaDownloader.download(
            serverFormDetails,
            File(TempFiles.createTempDir(), "temp").absolutePath,
            TempFiles.createTempDir(),
            mock(),
            true
        )

        assertThat(result, equalTo(false))
    }

    @Test
    fun `returns false when there is an existing copy of a media file and an older one and media file list hash doesn't match existing copy`() {
        // Save forms
        val formsRepository = InMemFormsRepository()
        val form1 = FormFixtures.form(
            version = "1",
            date = 1,
            mediaFiles = listOf(Pair("file", "old"))
        )
        formsRepository.save(form1)

        val form2 = FormFixtures.form(
            version = "2",
            date = 2,
            mediaFiles = listOf(Pair("file", "existing"))
        )
        formsRepository.save(form2)

        // Set up same media file on server
        val mediaFile = MediaFile("file", "somethingElse", "downloadUrl")
        val manifestFile = ManifestFile(null, listOf(mediaFile))
        val serverFormDetails =
            ServerFormDetails(null, null, "formId", "3", null, false, true, manifestFile)
        val formSource = mock<FormSource> {
            on { fetchMediaFile(mediaFile.downloadUrl) } doReturn "existing".toByteArray()
                .inputStream()
        }

        val formMediaDownloader = FormMediaDownloader(formsRepository, formSource)
        val result = formMediaDownloader.download(
            serverFormDetails,
            File(TempFiles.createTempDir(), "temp").absolutePath,
            TempFiles.createTempDir(),
            mock()
        )

        assertThat(result, equalTo(false))
    }
}
