package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.V3
import kotlin.math.sqrt

/** Solid collision support underneath the measured floor; no expansion of its footprint. */
object MappedFloorPrisms {
    data class Prism(val vertices:List<V3>,val indices:List<Int>)
    private val faces=listOf(0,1,2,3,5,4,0,3,4,0,4,1,1,4,5,1,5,2,2,5,3,2,3,0)
    private fun V3.dot(b:V3)=x*b.x+y*b.y+z*b.z
    fun build(vertices:List<V3>,indices:List<Int>,up:V3,thickness:Double=.05):List<Prism> {
        require(thickness>0 && indices.size%3==0)
        return indices.chunked(3).mapNotNull { t ->
            val a=vertices[t[0]];var b=vertices[t[1]];var c=vertices[t[2]]
            var n=(b-a).cross(c-a)
            val length=sqrt(n.dot(n))
            if(length<1e-7) null else {
                if(n.dot(up)<0) { val swap=b;b=c;c=swap;n=n*(-1.0) }
                val down=n*(-thickness/length)
                Prism(listOf(a,b,c,a+down,b+down,c+down),faces)
            }
        }
    }
    /** Extrapolates measured floor geometry for a debris-only catch collider, including mesh holes. */
    fun debrisSupport(vertices:List<V3>,indices:List<Int>,up:V3,span:Double=64.0,thickness:Double=.1):Prism {
        require(span>0 && thickness>0)
        val top=build(vertices,indices,up,thickness).first().vertices.take(3)
        val a=top[0];val edge=top[1]-a;val u=edge*(1.0/sqrt(edge.dot(edge)))
        val cross=edge.cross(top[2]-a);val n=cross*(1.0/sqrt(cross.dot(cross)));val v=n.cross(u)
        val average=vertices.reduce { p,q -> p+q }*(1.0/vertices.size)
        val center=average-n*((average-a).dot(n));val half=span*.5
        val corners=listOf(center-u*half-v*half,center+u*half-v*half,center+u*half+v*half,center-u*half+v*half)
        return Prism(corners+corners.map { it-n*thickness },
            listOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,3,7,4,3,4,0))
    }
}
