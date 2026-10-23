package com.idp.geostampcamera.di

import android.content.Context
import androidx.room.Room
import com.idp.geostampcamera.BuildConfig
import com.idp.geostampcamera.data.local.GeoStampDatabase
import com.idp.geostampcamera.data.local.PhotoDao
import com.idp.geostampcamera.data.local.TemplateDao
import com.idp.geostampcamera.data.remote.OpenMeteoApi
import com.idp.geostampcamera.util.AppDispatchers
import com.idp.geostampcamera.util.DefaultAppDispatchers
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDispatchers(): AppDispatchers = DefaultAppDispatchers()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GeoStampDatabase =
        Room.databaseBuilder(context, GeoStampDatabase::class.java, GeoStampDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePhotoDao(db: GeoStampDatabase): PhotoDao = db.photoDao()
    @Provides fun provideTemplateDao(db: GeoStampDatabase): TemplateDao = db.templateDao()
    @Provides fun provideSavedLocationDao(db: GeoStampDatabase): com.idp.geostampcamera.data.local.SavedLocationDao =
        db.savedLocationDao()

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.WEATHER_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton
    fun provideWeatherApi(retrofit: Retrofit): OpenMeteoApi = retrofit.create(OpenMeteoApi::class.java)
}
