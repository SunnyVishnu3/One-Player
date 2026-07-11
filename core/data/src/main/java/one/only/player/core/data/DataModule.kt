package one.only.player.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import one.only.player.core.data.repository.AndroidSubtitleFontFileValidator
import one.only.player.core.data.repository.FavoriteRepository
import one.only.player.core.data.repository.LocalFavoriteRepository
import one.only.player.core.data.repository.LocalMediaRepository
import one.only.player.core.data.repository.LocalPlaybackMarkRepository
import one.only.player.core.data.repository.LocalPreferencesRepository
import one.only.player.core.data.repository.LocalRemoteServerRepository
import one.only.player.core.data.repository.LocalSearchHistoryRepository
import one.only.player.core.data.repository.LocalSubtitleFontRepository
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PlaybackMarkRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.data.repository.SearchHistoryRepository
import one.only.player.core.data.repository.SubtitleFontFileValidator
import one.only.player.core.data.repository.SubtitleFontRepository

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindsSearchHistoryRepository(
        searchHistoryRepository: LocalSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    fun bindsRemoteServerRepository(
        remoteServerRepository: LocalRemoteServerRepository,
    ): RemoteServerRepository

    @Binds
    @Singleton
    fun bindsFavoriteRepository(
        favoriteRepository: LocalFavoriteRepository,
    ): FavoriteRepository

    @Binds
    @Singleton
    fun bindsPlaybackMarkRepository(
        playbackMarkRepository: LocalPlaybackMarkRepository,
    ): PlaybackMarkRepository

    @Binds
    @Singleton
    fun bindsSubtitleFontRepository(
        subtitleFontRepository: LocalSubtitleFontRepository,
    ): SubtitleFontRepository

    @Binds
    fun bindsSubtitleFontFileValidator(
        subtitleFontFileValidator: AndroidSubtitleFontFileValidator,
    ): SubtitleFontFileValidator

    companion object {
        @dagger.Provides
        @javax.inject.Singleton
        fun providesOkHttpClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().build()

        @dagger.Provides
        @javax.inject.Singleton
        fun providesJson(): kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
