package li.pika.lockiton

import java.net.URI
import java.time.Duration


data class PowerDemonstration(val conninfo: URI,
                              val duration: Duration = Duration.ofSeconds(100),
                              val batchSize: Int = 8): Runnable {
    private var locks: Int = 0
    private var tokens: Array<Int> = arrayOf()
    private var timings: Array<LongArray> = emptyArray()

    val timingsSnapshot: List<TransactionTiming>
        get() = timings.clone().map { TransactionTiming.fromNanoTimings(it) }

    private val db: DB by lazy { DB(conninfo) }

    @Synchronized
    override fun run() {
        val nanoDuration = duration.toNanos()
        val begin = System.nanoTime()

        while (System.nanoTime() - begin < nanoDuration) {
            locks += lockSome()
        }
    }

    private fun lockSome(): Int {
        var times: LongArray = longArrayOf()
        tokens = db.retry {
            times += System.nanoTime()
            release(tokens)
            obtain()
        }
        times += System.nanoTime()
        timings += times
        return tokens.size
    }
}