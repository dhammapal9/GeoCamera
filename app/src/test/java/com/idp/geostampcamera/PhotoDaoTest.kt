package com.idp.geostampcamera

import android.content.Context
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.idp.geostampcamera.data.local.GeoStampDatabase
import com.idp.geostampcamera.data.local.PhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoDaoTest {

    private lateinit var db: GeoStampDatabase

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, GeoStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test fun `insert then observe yields the row`() = runBlocking {
        val dao = db.photoDao()
        val id = dao.insert(sample(timestamp = 1_000L))
        val list = dao.observeAll().first()
        assertThat(list).hasSize(1)
        assertThat(list[0].id).isEqualTo(id)
    }

    @Test fun `search matches address and note`() = runBlocking {
        val dao = db.photoDao()
        dao.insert(sample(timestamp = 1_000L, address = "Pune, India"))
        dao.insert(sample(timestamp = 2_000L, address = "Mumbai, India"))
        val puneHits = dao.search("Pune").first()
        assertThat(puneHits.map { it.shortAddress }).containsExactly("Pune, India")
    }

    @Test fun `only photos with coordinates appear in geotagged flow`() = runBlocking {
        val dao = db.photoDao()
        dao.insert(sample(timestamp = 1L, lat = 10.0, lng = 20.0))
        dao.insert(sample(timestamp = 2L, lat = null, lng = null))
        val geo = dao.observeWithCoordinates().first()
        assertThat(geo).hasSize(1)
    }

    private fun sample(
        timestamp: Long,
        address: String? = "Short",
        lat: Double? = 10.0,
        lng: Double? = 20.0
    ): PhotoEntity = PhotoEntity(
        originalPath = null, stampedPath = null, publicUri = null,
        timestampMillis = timestamp, latitude = lat, longitude = lng,
        shortAddress = address, fullAddress = null,
        accuracyMeters = null, altitudeMeters = null,
        templateId = null, note = null, projectName = null,
        mapType = null, widthPx = null, heightPx = null,
        status = "READY"
    )
}*/
