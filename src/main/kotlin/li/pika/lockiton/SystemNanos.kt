package li.pika.lockiton

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId


object SystemNanos {
    private val oneMillion = 1000000
    private val oneBillion = 1000000000

    val epochMillis = System.currentTimeMillis()
    val systemNanos = System.nanoTime()
    val epochNanoOffset = epochMillis * oneMillion - systemNanos

    fun asEpochNanos(nanos: Long): Long = nanos + epochNanoOffset

    fun asEpochMillis(nanos: Long): Long = asEpochNanos(nanos) / oneMillion

    fun asOffsetDateTime(nanos: Long): OffsetDateTime {
        val zone = ZoneId.of("UTC")
        val instant = Instant.ofEpochMilli(asEpochMillis(nanos))
        return OffsetDateTime.ofInstant(instant, zone)
    }


    fun sleep(nanos: Long) {
        val millis = nanos / oneMillion
        val remainingNanos = nanos % oneMillion
        Thread.sleep(millis, remainingNanos.toInt())
    }

    class Timing(timings: LongArray) {
        val systemNanos: LongArray

        init {
            val msg = "Timings array must contain at least 2 elements."
            if (timings.size < 2) throw Err(msg)
            systemNanos = timings
        }

        val duration: Long
            get() = systemNanos.last() - systemNanos.first()

        val intervals: LongArray by lazy {
            val pairs = systemNanos.dropLast(1) zip systemNanos.drop(1)
            pairs.map { it.second - it.first }.toLongArray()
        }

        val start: OffsetDateTime
            get() = asOffsetDateTime(systemNanos.first())

        val end: OffsetDateTime
            get() = asOffsetDateTime(systemNanos.last())

        companion object {
            fun secondsBetween(start: Timing, end: Timing): Double {
                val nanos = end.systemNanos.last() - start.systemNanos.first()
                return nanos.toDouble() / oneBillion
            }
        }
    }
}