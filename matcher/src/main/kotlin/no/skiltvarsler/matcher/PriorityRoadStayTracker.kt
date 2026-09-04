package no.skiltvarsler.matcher

/**
 * One forkjørsveg alert per stay: the 206 at the entrance, or stretch
 * enter if joining from a side road. Reminder plates stay silent until
 * the stretch has been left for [graceAfterLeaveMs].
 *
 * NVDB 596 geometry often has gaps between reminder plates.
 */
class PriorityRoadStayTracker(
    private val graceAfterLeaveMs: Long = GRACE_AFTER_LEAVE_MS,
) {
    var stayActive: Boolean = false
        private set
    var alertedThisStay: Boolean = false
        private set

    private var lastActiveMs: Long = Long.MIN_VALUE / 2

    fun onTick(
        onPriorityRoad: Boolean,
        signInWindow: Boolean,
        endSignInWindow: Boolean,
        nowMs: Long,
    ) {
        if (endSignInWindow) {
            lastActiveMs = nowMs
            resetStay()
            return
        }
        if (onPriorityRoad || signInWindow) {
            stayActive = true
            lastActiveMs = nowMs
            return
        }
        if (!stayActive) {
            return
        }
        if (nowMs - lastActiveMs >= graceAfterLeaveMs) {
            resetStay()
        }
    }

    fun allowAlert(): Boolean = !alertedThisStay

    fun markAlerted() {
        stayActive = true
        alertedThisStay = true
    }

    fun reset() {
        resetStay()
        lastActiveMs = Long.MIN_VALUE / 2
    }

    private fun resetStay() {
        stayActive = false
        alertedThisStay = false
    }

    companion object {
        const val GRACE_AFTER_LEAVE_MS = 90_000L
    }
}
