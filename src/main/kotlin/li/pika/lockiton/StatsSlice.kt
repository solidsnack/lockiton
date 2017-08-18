package li.pika.lockiton

import java.time.Duration
import java.time.OffsetDateTime

import org.apache.commons.math3.stat.descriptive.rank.Percentile


private typealias Statistic = (Array<SystemNanos.Timing>) -> Double
private typealias Measure = (SystemNanos.Timing) -> Double


data class StatsSlice(val timings: Array<Array<SystemNanos.Timing>>) {
    private val zero: Boolean = timings.size < 1 || timings.all { it.size < 1 }
    private val bounds by lazy {
        val acc = Pair(OffsetDateTime.MAX, OffsetDateTime.MIN)
        timings.flatten().fold(acc) { (start, end), it ->
            Pair(if (it.start < start) it.start else start,
                 if (it.end > end) it.end else end)
        }
    }

    val threads: Int = timings.size
    val start: OffsetDateTime by lazy { bounds.first }
    val end: OffsetDateTime by lazy { bounds.second }
    val duration: Duration by lazy { Duration.between(start, end) }

    fun frequencyInHz(measure: Measure): Double {
        if (zero) return 0.0
        return hz(measure, timings.flatten().toTypedArray())
    }

    fun aggregateStatistic(statistic: Statistic): Double {
        if (zero) return 0.0
        return statistic(timings.flatten().toTypedArray())
    }

    fun percentile(percentiles: Array<PercentileSpec>,
                   measure: Measure): DoubleArray {
        if (zero) return doubleArrayOf()
        val data = timings.flatMap { it.map(measure) }
        val p = Percentile()
        p.data = data.toDoubleArray()
        val result = percentiles.map { p.evaluate(it.value) }
        return result.toDoubleArray()
    }

    fun perThread(statistic: Statistic): DoubleArray =
        timings.map(statistic).toDoubleArray()

    fun perThreadHz(measure: Measure): DoubleArray =
        timings.map { hz(measure, it) }.toDoubleArray()

    private fun hz(measure: Measure, data: Array<SystemNanos.Timing>): Double {
        if (data.size < 1) return 0.0
        val start = data.minBy { it.systemNanos.first() }
        val end = data.maxBy { it.systemNanos.last() }
        val seconds = SystemNanos.Timing.secondsBetween(start!!, end!!)
        return data.map(measure).sum() / seconds
    }
}