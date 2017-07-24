package li.pika.lockiton

import java.net.URI
import kotlin.concurrent.fixedRateTimer


fun main(args: Array<String>) {
    val conninfo = if (args.isNotEmpty()) args[0] else "postgres:///"
    val tasks = Array(8) { PowerDemonstration(URI(conninfo)) }
    val threads = tasks.map { task -> Thread(task) }
    var lastCounts: List<Int> = listOf()
    val start = System.nanoTime()

    fun printCounts() {
        val counts = tasks.map { task -> task.count }
        val seconds = (System.nanoTime() - start).toDouble() / 1000000000
        val rate = (counts.sum() / seconds) / 1000
        val timing = "%4.1fkHz for %6.2fs".format(rate, seconds)
        if (counts != lastCounts) println("Counts ($timing): $counts")
        lastCounts = counts
    }

    for (thread in threads) thread.start()


    fixedRateTimer(daemon = true, initialDelay = 1000, period = 1000) {
        printCounts()
    }

    for (thread in threads) thread.join()

    printCounts()
}