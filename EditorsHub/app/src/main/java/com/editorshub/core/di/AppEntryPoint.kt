package com.editorshub.core.di

import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.core.rendering.canvas.CanvasCompositor
import com.editorshub.core.rendering.export.VideoExporter
import com.editorshub.core.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets Compose AndroidViews reach singleton rendering services without a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun compositor(): CanvasCompositor
    fun media(): MediaRepository
    fun projectRepository(): ProjectRepository
    fun settings(): SettingsRepository
    fun thumbnailGenerator(): ThumbnailGenerator
    fun videoExporter(): VideoExporter
}
