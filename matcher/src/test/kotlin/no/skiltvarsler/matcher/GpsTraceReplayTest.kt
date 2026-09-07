package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpsTraceReplayTest {
    @Test
    fun osloRing2StaysOnMainSequence() {
        val text = readResource("gps/oslo_ring2.txt")
        val (meta, fixes) = GpsTrace.parse(text)
        assertThat(meta.name).contains("Oslo Ring 2")
        assertThat(fixes.size).isAtLeast(50)

        val graph = TraceGraph.fromFixes(fixes, tileId = "oslo-ring2")
        val matcher = MapMatcher(graph)
        fixes.forEach { matcher.update(it) }
        val match = matcher.current()
        assertThat(match).isNotNull()
        assertThat(match!!.sequenceId).isEqualTo(TraceGraph.SEQ_MAIN)
        assertThat(match.position).isGreaterThan(0.5)
    }

    @Test
    fun e6JessheimStaysOnMainThroughTunnelMultipath() {
        val text = readResource("gps/e6_jessheim_grua.txt")
        val (_, clean) = GpsTrace.parse(text)
        assertThat(clean.size).isAtLeast(40)

        val corrupted = GpsTrace.withTunnelMultipath(
            fixes = clean,
            fromIndex = clean.size / 3,
            sampleCount = 8,
            offsetNorthMeters = 55.0,
            offsetEastMeters = 40.0,
            accuracyMeters = 70.0,
        )
        val graph = TraceGraph.fromFixes(clean, tileId = "e6-jessheim")
        val matcher = MapMatcher(graph)
        corrupted.forEach { matcher.update(it) }
        val match = matcher.current()
        assertThat(match).isNotNull()
        assertThat(match!!.sequenceId).isEqualTo(TraceGraph.SEQ_MAIN)
        assertThat(match.sequenceId).isNotEqualTo(TraceGraph.SEQ_SIDE)
    }

    @Test
    fun osloRing2DoesNotJumpToSideTemptationDuringMultipath() {
        val text = readResource("gps/oslo_ring2.txt")
        val (_, clean) = GpsTrace.parse(text)
        val mid = clean.size / 2
        val corrupted = GpsTrace.withTunnelMultipath(
            fixes = clean,
            fromIndex = mid,
            sampleCount = 6,
            offsetNorthMeters = 0.0,
            offsetEastMeters = 70.0,
            accuracyMeters = 65.0,
        )
        val graph = TraceGraph.fromFixes(clean, tileId = "oslo-ring2-hold")
        val matcher = MapMatcher(graph)
        corrupted.take(mid + 6).forEach { matcher.update(it) }
        assertThat(matcher.isHolding()).isTrue()
        assertThat(matcher.current()!!.sequenceId).isEqualTo(TraceGraph.SEQ_MAIN)
    }

    private fun readResource(path: String): String {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing test resource $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
