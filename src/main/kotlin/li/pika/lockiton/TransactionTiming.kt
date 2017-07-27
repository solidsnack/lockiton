package li.pika.lockiton

import java.time.Duration
import java.time.OffsetDateTime


data class TransactionTiming(val start: OffsetDateTime,
                             val duration: Long,
                             val retries: LongArray) {

    val end: OffsetDateTime = start.plusNanos(duration)

    val nominalDuration: Long = duration - (retries.lastOrNull() ?: 0)

    companion object {
        fun fromNanoTimings(timings: LongArray): TransactionTiming {
            val start = SystemNanos.asOffsetDateTime(timings.first())
            val rebased = timings.map { it - timings.first() }
            val duration = rebased.last()
            val retries = rebased.drop(1).take(timings.size - 2)

            return TransactionTiming(start, duration, retries.toLongArray())
        }

        data class Summary(var records: Int = 0,
                           var retries: Int = 0,
                           var nominalDuration: Long = 0,
                           var duration: Long = 0,
                           var start: OffsetDateTime,
                           var end: OffsetDateTime) {
            val nanos: Long
                get() = Duration.between(start, end).toNanos()

            val idle: Double
                get() = 1 - (nominalDuration / nanos.toDouble())

            val tps: Double
                get() = records / (nanos.toDouble() / 1000000000)

            val retryRatio: Double
                get() = retries / records.toDouble()
        }

        val dur = listOf(Duration.ofSeconds(1),
                         Duration.ofSeconds(2),
                         Duration.ofSeconds(10))

        fun analyze(timings: List<TransactionTiming>,
                    durations: List<Duration> = dur): Map<Duration, Summary>? {
            if (timings.isEmpty()) return null

            val end = timings.last().end
            val targetTimes = durations.map { end - it }
            val summary = Summary(start = end, end = end)
            var summaries: HashMap<OffsetDateTime, Summary> = hashMapOf()

            for (timing in timings.reversed()) {
                summary.records += 1
                summary.retries += timing.retries.size
                summary.nominalDuration += timing.nominalDuration
                summary.duration += timing.duration
                summary.start = timing.start

                for (t in targetTimes) {
                    if (timing.end >= t) { summaries[t] = summary.copy() }
                }

                if (timing.end < targetTimes.last()) break
            }

            val correlated = (targetTimes zip durations).map {
                (t, d) -> Pair(d, summaries[t]!!)
            }

            return mapOf(*correlated.toTypedArray())
        }
    }

}
