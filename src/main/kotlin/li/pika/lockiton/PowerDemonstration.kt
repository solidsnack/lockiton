package li.pika.lockiton

import java.net.URI
import java.time.Duration


data class PowerDemonstration(val conninfo: URI,
                              val duration: Duration = Duration.ofSeconds(100),
                              val batchSize: Int = 8): Runnable {
    private var locks: Int = 0
    private var tokens: Array<Int> = emptyArray()
    private var timings: Array<SystemNanos.Timing> = emptyArray()

    fun handOverTimings(): Array<SystemNanos.Timing> = synchronized(timings) {
        val old = timings.clone()
        timings = emptyArray()
        old
    }

    private val db: DB by lazy { DB(conninfo) }

    @Synchronized
    override fun run() {
        val nanoDuration = duration.toNanos()
        val begin = System.nanoTime()

        while (System.nanoTime() - begin < nanoDuration) {
            locks += lockSome()
        }
    }

    private fun postTiming(timing: SystemNanos.Timing) {
        synchronized(timings) {
            timings += timing
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
        postTiming(SystemNanos.Timing(times))
        return tokens.size
    }
}