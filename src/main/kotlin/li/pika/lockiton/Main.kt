package li.pika.lockiton

import java.net.URI
import kotlin.concurrent.fixedRateTimer


fun main(args: Array<String>) {
    val conninfo = args[0]
    val taskCount = args.elementAtOrNull(1)?.toInt() ?: 1
    val tasks = Array(taskCount) { PowerDemonstration(URI(conninfo)) }
    val threads = tasks.map { task -> Thread(task) }
    val start = System.nanoTime()

    fun millisPassed() = (System.nanoTime() - start).toDouble() / 1000000

    for (thread in threads) thread.start()

    val ms: Long = 1000
    val delay = ms - millisPassed().toLong()
    val t = fixedRateTimer(daemon = true, initialDelay = delay, period = ms) {
        val seconds = millisPassed() / 1000
        val t = "T+%06.2fs".format(seconds)

        val collected = tasks.map { it.handOverTimings() }
        val stats = StatsSlice(collected.toTypedArray())
        val report = StatsReport(stats)

        println("$t ${Display.display(report)}")
    }

    for (thread in threads) thread.join()
    t.cancel()
}
