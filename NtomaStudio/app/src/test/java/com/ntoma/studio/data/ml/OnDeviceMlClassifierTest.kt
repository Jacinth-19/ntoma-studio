package com.ntoma.studio.data.ml

import com.ntoma.studio.domain.model.FabricCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnDeviceMlClassifierTest {

    @Test
    fun `denim labels map to denim`() {
        assertEquals(FabricCategory.DENIM, OnDeviceMlClassifier.hintFor("jean, blue jean, denim"))
    }

    @Test
    fun `velvet maps to velvet`() {
        assertEquals(FabricCategory.VELVET, OnDeviceMlClassifier.hintFor("velvet"))
    }

    @Test
    fun `quilt maps to brocade family`() {
        assertEquals(FabricCategory.BROCADE, OnDeviceMlClassifier.hintFor("quilt, comforter"))
    }

    @Test
    fun `unrelated label maps to nothing`() {
        assertNull(OnDeviceMlClassifier.hintFor("goldfish"))
        assertNull(OnDeviceMlClassifier.hintFor("sports car"))
    }

    @Test
    fun `mapping is case insensitive`() {
        assertEquals(FabricCategory.DENIM, OnDeviceMlClassifier.hintFor("JEAN, BLUE JEAN, DENIM"))
    }
}
