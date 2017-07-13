package li.pika.lockiton

import java.net.URI
import kotlin.concurrent.fixedRateTimer


fun main(args: Array<String>) {
    val conninfo = if (args.isNotEmpty()) args[0] else "postgres:///"
    val tasks = arrayOf(PowerDemonstration(URI(conninfo)),
                        PowerDemonstration(URI(conninfo)),
                        PowerDemonstration(URI(conninfo)),
                        PowerDemonstration(URI(conninfo)))
    val threads = tasks.map { task -> Thread(task) }

    for (thread in threads) thread.start()

    var lastCounts: List<Int> = listOf()
    fun printCounts() {
        val counts = tasks.map { task -> task.count }
        if (counts != lastCounts) println("Counts: $counts")
        lastCounts = counts
    }

    fixedRateTimer(daemon = true, initialDelay = 1000, period = 1000) {
        printCounts()
    }

    for (thread in threads) thread.join()

    printCounts()
}