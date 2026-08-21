package com.example.caudalapp.domain

object RouteElapsedPolicy {
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
