package no.skiltvarsler.tracking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import no.skiltvarsler.matcher.AlertEngine
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.Replay
import no.skiltvarsler.matcher.SyntheticGraph
import no.skiltvarsler.matcher.e6NorthLink
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tiles.TravelDirection

object ReplayRunner {
    suspend fun run(context: Context) {
        try {
            LastAlertStore.setTracking("Replay E6 nord")
            val settings = SettingsStore(context).settings.first()
            val graph = SyntheticGraph.e6VestbyLike()
            val engine = AlertEngine(graph, settings)
            val fixes = Replay.alongLink(
                graph.e6NorthLink(),
                TravelDirection.MED,
                speedMetersPerSecond = 25.0,
            )
            for (fix in fixes) {
                val alerts = engine.update(fix)
                val match = engine.currentMatch()
                if (match != null) {
                    LastAlertStore.setTracking(
                        "Replay lenke ${match.sequenceId}  ${"%.0f".format(match.position * SyntheticGraph.LENGTH_METERS)} m",
                    )
                }
                for (alert in alerts) {
                    withContext(Dispatchers.Main.immediate) {
                        AlertNotifier.publishAlert(context, alert)
                    }
                }
                delay(80)
            }
            val cameras = Replay.play(AlertEngine(graph, settings), fixes)
                .alertsOf(AlertKind.SPEED_CAMERA)
            if (cameras.isEmpty()) {
                LastAlertStore.setTracking("Replay ferdig — ingen fotoboks")
            } else {
                LastAlertStore.setTracking("Replay ferdig — fotoboks ${cameras.first().nvdbId}")
            }
        } catch (error: Exception) {
            LastAlertStore.setTracking("Replay feilet: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
