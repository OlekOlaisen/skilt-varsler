package no.skiltvarsler.matcher

/**
 * Shared junction continuation rules for [MapMatcher] and [HorizonScanner].
 *
 * Through-road hops may bend moderately (bridge approaches, slight kinks in NVDB
 * sequences). Side streets at ~90° stay out because only the straightest options
 * within [BEST_CONTINUATION_MARGIN_DEGREES] of the best heading are kept.
 */
object JunctionPolicy {
    const val CONTINUE_HEADING_DEGREES = 70.0
    const val BEST_CONTINUATION_MARGIN_DEGREES = 12.0
}
