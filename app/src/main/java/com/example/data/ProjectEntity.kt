package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ratio: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val duration: Float,
    val layerCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val documentJson: String
)
