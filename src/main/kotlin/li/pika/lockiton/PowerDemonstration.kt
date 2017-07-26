package li.pika.lockiton

import java.net.URI
import java.time.Duration


data class PowerDemonstration(val conninfo: URI,
                              val duration: Duration = Duration.ofSeconds(100),
                              val batchSize: Int = 8): Runnable {
    private var locks: Int = 0
    private var tokens: Array<Int> = arrayOf()

    private var timings: Array<LongArray> = emptyArray()

    val count: Int
        get() = timings.size

    val timingsSnapshot: List<TransactionTiming>
        get() = timings.clone().map { TransactionTiming.fromNanoTimings(it) }

    val db: DB by lazy {
        DB(conninfo)
    }

    override fun run() {
        val nanoDuration = duration.toNanos()
        val begin = System.nanoTime()

        var sleep = {
            val start = System.nanoTime()
            locks += lockSome()
            System.nanoTime() - start
        }()

        while (System.nanoTime() - begin < nanoDuration) {
            SystemNanos.sleep(sleep)
            locks += lockSome()
            val retries = timings.takeLast(10).map { maxOf(0,it.size - 2) }

            if (retries.average() > 0.1) {
                sleep *= 2
            } else {
                sleep = (0.9 * sleep).toLong()
            }
        }
    }

    fun lockSome(): Int {
        var times: LongArray = longArrayOf()
        tokens = db.retry {
            times += System.nanoTime()
            db.release(tokens)
            db.obtain()
        }
        times += System.nanoTime()
        timings += times
        return tokens.size
    }
}