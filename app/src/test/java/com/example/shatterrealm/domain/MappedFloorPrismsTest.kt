package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.V3
import com.example.shatterrealm.domain.usecase.MappedFloorPrisms
import com.example.shatterrealm.domain.usecase.RealmCollisionLayers
import org.junit.Assert.*
import org.junit.Test

class MappedFloorPrismsTest {
    private val floor=listOf(V3(0.0,0.0,0.0),V3(1.0,0.0,0.0),V3(0.0,0.0,1.0))
    @Test fun thicknessExtendsOnlyBelowMeasuredTop() {
        val p=MappedFloorPrisms.build(floor,listOf(0,1,2),V3(0.0,1.0,0.0)).single()
        assertEquals(floor.toSet(),p.vertices.take(3).toSet())
        p.vertices.drop(3).forEach { assertEquals(-.05,it.y,1e-9) }
        assertEquals(24,p.indices.size)
    }
    @Test fun reversedWindingStillProducesDownwardThickness() {
        val a=MappedFloorPrisms.build(floor,listOf(0,1,2),V3(0.0,1.0,0.0)).single()
        val b=MappedFloorPrisms.build(floor,listOf(0,2,1),V3(0.0,1.0,0.0)).single()
        assertEquals(a.vertices,b.vertices)
    }
    @Test fun verticalAnchorLocalAxesAreSupported() {
        val xy=listOf(V3(0.0,0.0,0.0),V3(1.0,0.0,0.0),V3(0.0,1.0,0.0))
        val p=MappedFloorPrisms.build(xy,listOf(0,1,2),V3(0.0,0.0,1.0)).single()
        p.vertices.drop(3).forEach { assertEquals(-.05,it.z,1e-9) }
    }
    @Test fun disconnectedTrianglesAreNotJoinedAcrossUnmappedSpace() {
        val points=floor+floor.map { it+V3(3.0,0.0,0.0) }
        val prisms=MappedFloorPrisms.build(points,listOf(0,1,2,3,4,5,0,0,0),V3(0.0,1.0,0.0))
        assertEquals(2,prisms.size)
        assertTrue(prisms.flatMap { it.vertices }.none { it.x>1.0 && it.x<3.0 })
    }
    @Test fun debrisSupportFillsMeshGapsAtTheMeasuredFloorHeight() {
        val points=floor+floor.map { it+V3(3.0,0.0,0.0) }
        val p=MappedFloorPrisms.debrisSupport(points,listOf(0,1,2,3,4,5),V3(0.0,1.0,0.0))
        assertEquals(8,p.vertices.size)
        p.vertices.take(4).forEach { assertEquals(0.0,it.y,1e-9) }
        p.vertices.drop(4).forEach { assertEquals(-.1,it.y,1e-9) }
        assertTrue(p.vertices.minOf { it.x }<0.0 && p.vertices.maxOf { it.x }>4.0)
    }
    @Test fun supportLayersOnlyAdmitFragments() {
        val debris=RealmCollisionLayers.DEBRIS;val ground=RealmCollisionLayers.DEBRIS_GROUND;val all=UInt.MAX_VALUE;val ordinary=1u
        fun collides(a:UInt,am:UInt,b:UInt,bm:UInt)=(am and b)!=0u && (bm and a)!=0u
        assertFalse(collides(ordinary,all,ground,debris))
        assertTrue(collides(debris,all,ground,debris))
        assertTrue(collides(ordinary,all,debris,all))
    }
}
