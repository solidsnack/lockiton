package li.pika.lockiton

import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId


data class PowerDemonstration(val conninfo: URI,
                              val duration: Duration = Duration.ofSeconds(100),
                              val batchSize: Int = 8): Runnable {
    var count: Int = 0
    var locks: Int = 0
    var tokens: Array<Int> = arrayOf()

    val db: DB by lazy {
        DB(conninfo)
    }

    override fun run() {
        val begin = t()
        while (Duration.between(begin, t()) < duration) {
            val start = System.nanoTime()
            count += 1
            locks += lockSome()
            val duration = System.nanoTime() - start
            val millis = duration / 1000000
            val nanos = duration % 1000000
            Thread.sleep(millis, nanos.toInt())
        }
    }

    fun lockSome(): Int {
        db.release(tokens)
        tokens = db.obtain()
        return tokens.size
    }

    fun t(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))
}