package com.example.caudalapp.domain

object RouteElapsedPolicy {
    const val CHECKPOINT_MILLIS = 60_000L

    fun needsCheckpoint(sessionStartedAtElapsedRealtime: Long, nowElapsedRealtime: Long): Boolean =
        sessionStartedAtElapsedRealtime >= 0L &&
            nowElapsedRealtime - sessionStartedAtElapsedRealtime >= CHECKPOINT_MILLIS

    fun currentElapsedMillis(
        accumulatedMillis: Long,
        sessionStartedAtElapsedRealtime: Long,
        nowElapsedRealtime: Long,
    ): Long {
        val currentSession = if (
            sessionStartedAtElapsedRealtime >= 0L &&
            nowElapsedRealtime >= sessionStartedAtElapsedRealtime
        ) {
            nowElapsedRealtime - sessionStartedAtElapsedRealtime
        } else {
            0L
        }
        return (accumulatedMillis + currentSession).coerceAtLeast(0L)
    }
}
