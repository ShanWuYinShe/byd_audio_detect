package com.byd.audiochanneldetector.di

import android.content.Context
import android.media.AudioManager
import com.byd.audiochanneldetector.data.repository.AudioChannelRepository
import com.byd.audiochanneldetector.data.repository.AudioChannelRepositoryImpl
import com.byd.audiochanneldetector.data.scanner.ALSAScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideALSAScanner(): ALSAScanner {
        return ALSAScanner()
    }

    @Provides
    @Singleton
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @Provides
    @Singleton
    fun provideAudioChannelRepository(
        @ApplicationContext context: Context,
        alsaScanner: ALSAScanner,
        audioManager: AudioManager
    ): AudioChannelRepository {
        return AudioChannelRepositoryImpl(context, alsaScanner, audioManager)
    }
}