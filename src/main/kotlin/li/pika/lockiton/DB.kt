package li.pika.lockiton

import java.net.URI
import java.sql.*
import java.util.*

import com.github.kittinunf.result.*


class DB(val conninfo: URI) {
    private val jdbcURI: String by lazy {
        "jdbc:postgresql://" + if (conninfo.port >= 0) {
            "${conninfo.host}:${conninfo.port}${conninfo.path}"
        } else {
            "${conninfo.host}${conninfo.path}"
        }
    }

    private val jdbcProperties: Properties by lazy {
        val properties = Properties()
        val userInfo = arrayOf("user", "password") zip
                              (conninfo.userInfo.split(":", limit = 2))
        for ((k, v) in userInfo) {
            properties.setProperty(k, v)
        }

        properties
    }

    private val cxn: Connection by lazy {
        val cxn = DriverManager.getConnection(jdbcURI, jdbcProperties)
        cxn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
        cxn.autoCommit = false
        cxn
    }

    private val prepared by lazy {
        object {
            val obtain = cxn.prepareStatement("SELECT * FROM obtain()")
            val release = cxn.prepareStatement("SELECT * FROM release(?)")
        }
    }

    class API(val db: DB) {
        fun obtain(): Array<Int> {
            db.run {
                var results: Array<Int> = arrayOf()
                val rows = prepared.obtain.executeQuery()
                while (rows.next()) { results += rows.getInt(1) }
                rows.close()
                return results
            }
        }

        fun release(tokens: Array<Int>) {
            db.run {
                txn {
                    for (token in tokens) {
                        prepared.release.setInt(1, token)
                        prepared.release.addBatch()
                    }
                    prepared.release.executeBatch()
                }
            }
        }
    }

    val api = API(this)

    @Synchronized
    fun<T: Any> txn(block: API.() -> T): Result<T, SQLException> {
        return txnState.encapsulate { this.api.block() }
    }

    @Synchronized
    fun<T: Any> retry(block: API.() -> T): T {
        return retryState.encapsulate { this.api.block() }
    }

    private val txnState = object {
        private var active: Boolean = false
        private var transactions: Int = 0
        private var lastStart: Long = System.nanoTime()
        private var lastEnd: Long = lastStart
        private var tailWeightedAverage: Long = 0

        fun<T: Any> encapsulate(block: () -> T): Result<T, SQLException> {
            if (active) return Result.Success(block())

            val start = System.nanoTime()

            try {
                active = true
                val data = block()
                cxn.commit()
                return Result.Success(data)
            } catch (e: SQLException) {
                cxn.rollback()
                // The "class 40" exceptions are all transaction related,
                // including rollback, integrity, serializable...
                if (!e.sqlState.startsWith("40")) throw e
                return Result.error(e)
            } finally {
                updateState(start, System.nanoTime())
                active = false
            }
        }

        val nominalDuration: Long
            get() = tailWeightedAverage

        fun halfRateSpacing(): Long {
            val nominal = nominalDuration - (System.nanoTime() - lastEnd)
            return maxOf(0, nominal)
        }

        fun updateState(start: Long, end: Long) {
            val duration = end - start

            transactions += 1
            lastStart = start
            lastEnd = end
            tailWeightedAverage = if (tailWeightedAverage > 0) {
                (duration + tailWeightedAverage) / 2
            } else {
                duration
            }
        }
    }

    private val retryState = object {
        var retrying: Boolean = false

        fun<T: Any> attempt(block: () -> T): Result<T, SQLException> {
            SystemNanos.sleep(txnState.halfRateSpacing())
            return txn { block() }
        }

        fun<T: Any> encapsulate(block: () -> T): T {
            if (retrying) return block()

            try {
                retrying = true
                val limit = 1024
                var n = 0
                var res = attempt(block)

                while (n < limit) {
                    if (res.any { true }) break
                    n += 1
                    res = attempt(block)
                }

                return res.mapError({ RetriedTooManyTimes(it) }).get()
            } finally {
                retrying = false
            }
        }
    }

    class RetriedTooManyTimes(override val cause: SQLException):
            SQLException("Too many attempts for retriable transaction.",
                         cause.sqlState)
}