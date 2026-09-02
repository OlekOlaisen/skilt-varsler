package no.skiltvarsler.tiles

import java.nio.ByteBuffer
import java.nio.ByteOrder

object GeometryCodec {
    fun encode(points: List<LatLon>): ByteArray {
        val buffer = ByteBuffer.allocate(4 + points.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(points.size)
        for (point in points) {
            buffer.putFloat(point.longitude.toFloat())
            buffer.putFloat(point.latitude.toFloat())
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<LatLon> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        return buildList(count) {
            repeat(count) {
                val longitude = buffer.float.toDouble()
                val latitude = buffer.float.toDouble()
                add(LatLon(latitude = latitude, longitude = longitude))
            }
        }
    }
}
