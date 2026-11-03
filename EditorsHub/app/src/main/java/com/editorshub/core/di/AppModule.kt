package com.editorshub.core.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.data.ProjectRepositoryImpl
import com.editorshub.core.database.MotionDatabase
import com.editorshub.core.database.ProjectDao
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): MotionDatabase =
        Room.databaseBuilder(context, MotionDatabase::class.java, "editorshub.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(database: MotionDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository
}
