package li.pika.lockiton

import java.net.URI
import java.time.Duration
import kotlin.concurrent.fixedRateTimer


fun main(args: Array<String>) {
    val conninfo = args[0]
    val taskCount = args[1].toInt()
    val tasks = Array(taskCount) { PowerDemonstration(URI(conninfo)) }
    val threads = tasks.map { task -> Thread(task) }
    val start = System.nanoTime()

    for (thread in threads) thread.start()

    var t = fixedRateTimer(daemon = true, initialDelay = 1000, period = 1000) {
        val dur: List<Duration> = TransactionTiming.dur
        val durMsg = "1s/2s/10s"
        val data = tasks.map { it.timingsSnapshot }
        val analyzed = data.map { TransactionTiming.analyze(it, dur) }

        val seconds = (System.nanoTime() - start).toDouble() / 1000000000
        val t = "T+%06.2fs".format(seconds)
        println("$t Stats ($durMsg)")
        println(formatStats("Total Time", analyzed) {
            Duration.ofNanos(it.nanos).toMillis() / 1000.0
        })
        println(formatStats("TPS", analyzed) { it.tps })
        println(formatStats("Retries per Transaction", analyzed) {
            it.medianRetriesPerTransaction
        })
        println(formatStats("Retried Transactions", analyzed) {
            it.fractionWithRetries
        })
        println(formatStats("Retry Time Ratio", analyzed) {
            it.idle
        })
        println(formatStats("Nominal (ms)", analyzed) {
            it.medianNominalMillis
        })
    }

    for (thread in threads) thread.join()
    t.cancel()
}


fun formatStats(
        description: String,
        stats: List<Map<Duration, TransactionTiming.Companion.Summary>?>,
        f: (TransactionTiming.Companion.Summary) -> Number
    ): String {
    val sep = " "
    val float = "%7.2f"
    val empty = "   -   "
    val formatted = stats.map {
        if (it != null) {
            val nums = it.map { f(it.value).toDouble() }
            val fmt = nums.map { if (it.isNaN()) empty else float.format(it) }
            fmt.joinToString("/")
        } else {
            arrayOf(empty, empty, empty).joinToString("/")
        }
    }
    val label = "  %25s".format(description + ":")
    return label + sep + formatted.joinToString(sep)
}