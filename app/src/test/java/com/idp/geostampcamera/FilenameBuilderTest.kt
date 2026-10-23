package com.idp.geostampcamera

import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.domain.model.CapturedPhoto
import com.idp.geostampcamera.util.FilenameBuilder
import org.junit.Test

class FilenameBuilderTest {

    private val sample = CapturedPhoto(
        id = 1, originalPath = null, stampedPath = null, publicUri = null,
        timestampMillis = 1_700_000_000_000L,
        latitude = 18.626936, longitude = 73.710798,
        shortAddress = "Pune, India", fullAddress = null,
        accuracyMeters = null, altitudeMeters = null,
        templateId = "classic", note = null, projectName = "Alpha!",
        mapType = null, widthPx = null, heightPx = null,
        status = CapturedPhoto.Status.READY
    )

    @Test fun `default order includes date and address`() {
        val name = FilenameBuilder.build(FilenameBuilder.DEFAULT, sample, 1)
        assertThat(name).contains("Pune")
        assertThat(name).matches(".*\\d{8}_\\d{6}.*")
    }

    @Test fun `empty config falls back to GeoStamp`() {
        val name = FilenameBuilder.build(FilenameBuilder.Config(emptyList(), ""), sample, 1)
        assertThat(name).isEqualTo("GeoStamp")
    }

    @Test fun `custom text is sanitised`() {
        val name = FilenameBuilder.build(
            FilenameBuilder.Config(listOf(FilenameBuilder.Token.CUSTOM), "hello world!"),
            sample, 1
        )
        assertThat(name).isEqualTo("helloworld")
    }

    @Test fun `project name has special chars removed`() {
        val name = FilenameBuilder.build(
            FilenameBuilder.Config(listOf(FilenameBuilder.Token.PROJECT), ""),
            sample, 1
        )
        assertThat(name).isEqualTo("Alpha")
    }

    @Test fun `sequence number is zero-padded`() {
        val name = FilenameBuilder.build(
            FilenameBuilder.Config(listOf(FilenameBuilder.Token.SEQ), ""),
            sample, 42
        )
        assertThat(name).isEqualTo("0042")
    }
}
