package li.pika.lockiton

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId


object SystemNanos {
    val epochMillis = System.currentTimeMillis()
    val systemNanos = System.nanoTime()
    val epochNanoOffset = epochMillis * 1000000 - systemNanos

    inline fun asEpochNanos(nanos: Long): Long = nanos + epochNanoOffset

    inline fun asEpochMillis(nanos: Long): Long = asEpochNanos(nanos) / 1000000

    inline fun asOffsetDateTime(nanos: Long): OffsetDateTime {
        val zone = ZoneId.of("UTC")
        val instant = Instant.ofEpochMilli(asEpochMillis(nanos))
        return OffsetDateTime.ofInstant(instant, zone)
    }

    inline fun sleep(nanos: Long) {
        val millis = nanos / 1000000
        val remainingNanos = nanos % 1000000
        Thread.sleep(millis, remainingNanos.toInt())
    }
}