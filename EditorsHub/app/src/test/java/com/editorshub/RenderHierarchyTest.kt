package com.editorshub

import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.RenderGraph
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderHierarchyTest {
    @Test fun hiddenParentHidesItsChildren() {
        val project = Project(id = "p", name = "P", durationMs = 5000L, layers = listOf(
            Layer(id = "group", name = "Group", type = LayerType.GROUP, visible = false, durationMs = 5000L),
            Layer(id = "child", name = "Child", type = LayerType.SHAPE, parentId = "group", durationMs = 5000L)
        ))
        assertEquals(0, RenderGraph.resolve(project, 1000L).layers.size)
    }

    @Test fun inactiveParentHidesItsChildrenOutsideItsClip() {
        val project = Project(id = "p", name = "P", durationMs = 5000L, layers = listOf(
            Layer(id = "group", name = "Group", type = LayerType.GROUP, startMs = 1000L, durationMs = 1000L),
            Layer(id = "child", name = "Child", type = LayerType.SHAPE, parentId = "group", durationMs = 5000L)
        ))
        assertEquals(0, RenderGraph.resolve(project, 500L).layers.size)
        assertEquals(2, RenderGraph.resolve(project, 1500L).layers.size)
    }

    @Test fun cyclicParentingDoesNotRenderRecursiveChildren() {
        val project = Project(id = "p", name = "P", durationMs = 5000L, layers = listOf(
            Layer(id = "a", name = "A", type = LayerType.GROUP, parentId = "b", durationMs = 5000L),
            Layer(id = "b", name = "B", type = LayerType.GROUP, parentId = "a", durationMs = 5000L),
            Layer(id = "child", name = "Child", type = LayerType.SHAPE, parentId = "a", durationMs = 5000L)
        ))
        assertEquals(0, RenderGraph.resolve(project, 1000L).layers.size)
    }
}
