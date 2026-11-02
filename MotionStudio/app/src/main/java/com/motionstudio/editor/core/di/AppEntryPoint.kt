package com.motionstudio.editor.core.di

import com.motionstudio.editor.core.data.ProjectRepository
import com.motionstudio.editor.core.media.MediaRepository
import com.motionstudio.editor.core.rendering.ThumbnailGenerator
import com.motionstudio.editor.core.rendering.canvas.CanvasCompositor
import com.motionstudio.editor.core.rendering.export.VideoExporter
import com.motionstudio.editor.core.settings.SettingsRepository
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
