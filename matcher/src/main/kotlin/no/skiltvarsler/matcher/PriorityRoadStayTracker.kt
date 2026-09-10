package no.skiltvarsler.matcher

/**
 * One forkjørsveg alert per stay: when entering the stretch, or when reaching the 206 plate
 * (not foreshadowed tens of metres ahead). Reminder plates stay silent until the stretch has
 * been left for [graceAfterLeaveMs], or the driver turns onto a different priority road.
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

    /**
     * Turning onto another priority-road arm starts a fresh alert opportunity without
     * waiting for [graceAfterLeaveMs] (straight sequence splits must not call this).
     */
    fun allowAlertAfterTurn() {
        stayActive = true
        alertedThisStay = false
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
