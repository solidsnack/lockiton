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

        data class Summary(var records: IntArray,
                           var retries: IntArray,
                           var nominalDuration: LongArray,
                           var duration: LongArray,
                           var start: OffsetDateTime,
                           var end: OffsetDateTime) {
            val nanos: Long
                get() = Duration.between(start, end).toNanos()

            val idle: Double
                get() = 1 - (nominalDuration.sum() / nanos.toDouble())

            val tps: Double
                get() = records.size / (nanos.toDouble() / 1000000000)

            val medianRetriesPerTransaction: Double
                get() = median(retries.filter { it > 0 }.map { it.toDouble() })

            val fractionWithRetries: Double
                get() = retries.map { if (it > 0) 1 else 0 }.average()

            val medianMillis: Double
                get() = median(duration.map { it.toDouble() }) /
                            1000000

            val medianNominalMillis: Double
                get() = median(nominalDuration.map { it.toDouble() }) /
                            1000000

            constructor(start: OffsetDateTime, end: OffsetDateTime):
                    this(
                            intArrayOf(), intArrayOf(),
                            longArrayOf(), longArrayOf(),
                            start, end
                    )
        }

        val dur = listOf(Duration.ofSeconds(1),
                         Duration.ofSeconds(2),
                         Duration.ofSeconds(10))

        fun analyze(timings: List<TransactionTiming>,
                    durations: List<Duration> = dur): Map<Duration, Summary>? {
            if (timings.isEmpty()) return null

            val end = OffsetDateTime.now()
            val targetTimes = durations.map { end - it }
            val summary = Summary(end, end)
            var summaries: HashMap<OffsetDateTime, Summary> = hashMapOf()

            for (timing in timings.reversed()) {
                for (t in targetTimes) {
                    if (timing.end >= t) { summaries[t] = summary.copy() }
                }

                summary.records += 1
                summary.retries += timing.retries.size
                summary.nominalDuration += timing.nominalDuration
                summary.duration += timing.duration
                summary.start = timing.start

                if (timing.end < targetTimes.last()) break
            }

            val correlated = (targetTimes zip durations).map {
                (t, d) -> Pair(d, summaries[t] ?: Summary(end, end))
            }

            return mapOf(*correlated.toTypedArray())
        }

        private fun<N: Number> robustMean(numbers: Collection<N>,
                                          retaining: Double = 0.90): Double {
            val fractionToDrop = maxOf(0.0, (1 - retaining) / 2)
            val doubles = numbers.map { it.toDouble() }.sorted()
            val fivePercent = (doubles.size * fractionToDrop).toInt()
            return doubles.drop(fivePercent).dropLast(fivePercent).average()
        }

        private fun median(doubles: List<Double>): Double {
            if (doubles.size < 1) return 0.0
            val data = doubles.sorted()
            // NB: When data.size is odd, we have: i == j
            val i = data.size / 2
            val j = (data.size - 1) / 2
            return (data[i] + data[j]) / 2.0
        }
    }

}
