package li.pika.lockiton

import java.time.Duration
import java.time.OffsetDateTime


class StatsReport(slice: StatsSlice) {
    private val percentiles: Array<PercentileSpec> =
        arrayOf(50, 95, 99).map { PercentileSpec(it) }.toTypedArray()

    private val threadStats = object {
        val transactionHz: EndsAndMiddles =
            EndsAndMiddles(slice.perThreadHz { 1.0 })
        val retryHz: EndsAndMiddles = EndsAndMiddles(slice.perThreadHz {
            it.intervals.size.toDouble() - 1
        })
    }

    private val aggregateStats = object {
        val transactionHz: Double = slice.frequencyInHz { 1.0 }
        val retryHz: Double = slice.frequencyInHz {
            it.intervals.size.toDouble() - 1
        }
        val transactionDurationMs = slice.percentile(percentiles) {
            it.duration / 1000000.0
        }
        val timeWasted = slice.percentile(percentiles) {
            (it.duration - it.intervals.last()).toDouble() / it.duration
        }
    }

    fun display(): String {
        return arrayOf(
            "${Display.display(aggregateStats.transactionHz)} txn/s " +
            "(${Display.display(threadStats.transactionHz)})",
            "${Display.display(aggregateStats.retryHz)} retry/s " +
            "(${Display.display(threadStats.retryHz)})",
            "${Display.display(aggregateStats.transactionDurationMs)} ms/txn",
            "${Display.display(aggregateStats.timeWasted)}% in retry"
        ).joinToString("  ")
    }

    class EndsAndMiddles(items: DoubleArray) {
        val selected: DoubleArray = endsAndMiddles(items.sortedArray())
        val truncated: Boolean = items.size > selected.size

        val display: String = if (truncated) {
            Display.display(selected.first()) + "..." +
                Display.display(selected.drop(1).dropLast(1)) + "..." +
                Display.display(selected.last())
        } else {
            Display.display(selected)
        }

        companion object {
            // Expects items to be sorted.
            private fun endsAndMiddles(items: DoubleArray): DoubleArray {
                if (items.size <= 5) return items
                // Up to 5 indices for odd size arrays and 4 for even.
                val values = arrayOf(                   // 2n      2n + 1
                    0,
                    ((items.size - 1.5) / 2).toInt(),   // n - 1   n - 1
                    ((items.size - 1.0) / 2).toInt(),   // n - 1   n
                    ((items.size + 1.5) / 2).toInt(),   // n       n + 1
                    items.size - 1
                ).sorted().distinct().map { items[it] }
                return values.toDoubleArray()
            }
        }
    }
}