package com.example.caudalapp.domain

object AdaptiveGpsPolicy {
    const val MOVING_SPEED_METERS_PER_SECOND = 1.4f
    const val STATIONARY_INTERVAL_MILLIS = 10_000L
    const val MOVEMENT_GRACE_MILLIS = 15_000L

    fun isMoving(speedMetersPerSecond: Float?): Boolean =
        speedMetersPerSecond != null && speedMetersPerSecond >= MOVING_SPEED_METERS_PER_SECOND

    fun intervalMillis(
        configuredMovingSeconds: Int,
        millisecondsSinceMovement: Long,
    ): Long {
        val movingInterval = configuredMovingSeconds.coerceIn(1, 10) * 1_000L
        return if (millisecondsSinceMovement <= MOVEMENT_GRACE_MILLIS) {
            movingInterval
        } else {
            maxOf(movingInterval, STATIONARY_INTERVAL_MILLIS)
        }
    }
}
