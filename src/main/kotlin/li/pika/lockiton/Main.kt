package li.pika.lockiton

import java.net.URI
import java.time.Duration
import kotlin.concurrent.fixedRateTimer


fun main(args: Array<String>) {
    val conninfo = if (args.isNotEmpty()) args[0] else "postgres:///"
    val tasks = Array(8) { PowerDemonstration(URI(conninfo)) }
    val threads = tasks.map { task -> Thread(task) }
    val start = System.nanoTime()

    for (thread in threads) thread.start()

    fixedRateTimer(daemon = true, initialDelay = 1000, period = 1000) {
        val dur: List<Duration> = TransactionTiming.dur
        val durMsg = "1s/2s/5s"
        val formatted = tasks.map {
            val timings = TransactionTiming.analyze(it.timingsSnapshot, dur)
            timings?.map({ "%.2f".format(it.value.tps) })
                    ?.joinToString("/") ?: "-"
        }
        val seconds = (System.nanoTime() - start).toDouble() / 1000000000
        val t = "T+%06.2fs".format(seconds)
        println("$t TPS ($durMsg): ${formatted.joinToString(" ")}")
    }

    for (thread in threads) thread.join()
}