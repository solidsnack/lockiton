package li.pika.lockiton

import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId


data class PowerDemonstration(val conninfo: URI = URI("postgres:///"),
                              val duration: Duration = Duration.ofSeconds(100),
                              val batchSize: Int = 8): Runnable {
    var count: Int = 0

    override fun run() {
        // val db = TODO("Connect to Database")
        val start = t()
        while (Duration.between(start, t()) < duration) {
            val start = System.nanoTime()
            count += 1
            // val locks = lockSome()
            val duration = System.nanoTime() - start
            Thread.sleep(duration / 1000, (duration % 1000).toInt())
        }
    }

    fun lockSome(): Int {
        TODO("Lock and count locks.")
    }

    fun t(): OffsetDateTime {
        return OffsetDateTime.now(ZoneId.of("UTC"))
    }
}