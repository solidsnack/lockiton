package li.pika.lockiton

import java.net.URI
import java.sql.*
import java.util.*


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
        val userInfo = arrayOf("user, password") zip
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

    @Synchronized
    fun obtain(): Array<Int> {
        var results: Array<Int> = arrayOf()
        val rows = retry {
            prepared.obtain.executeQuery()
        }
        while (rows.next()) {
            results += rows.getInt(1)
        }
        rows.close()
        return results
    }

    @Synchronized
    fun release(tokens: Array<Int>) {
        retry {
            for (token in tokens) {
                prepared.release.setInt(1, token)
                prepared.release.addBatch()
            }
            prepared.release.executeBatch()
        }
    }

    fun<T> txn(block: () -> T): T {
        try {
            val data = block()
            cxn.commit()
            return data
        } catch (e: SQLException) {
            cxn.rollback()
            throw e
        }
    }

    fun<T> retry(block: () -> T): T {
        val start = System.nanoTime()
        val limit = 1024
        var n = 1
        while (n < limit) {
            n += 1
            try {
                return txn { block() }
            } catch (e: SQLException) {
                cxn.rollback()
                // The "class 40" exceptions are all transaction related,
                // including rollback, integrity, serializable...
                if (!e.sqlState.startsWith("40")) throw e
            }
            val duration = System.nanoTime() - start
            val millis = duration / 1000000
            val nanos = duration % 1000000
            Thread.sleep(millis, nanos.toInt())
        }
        return txn { block() }
    }
}