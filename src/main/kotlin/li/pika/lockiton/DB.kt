package li.pika.lockiton

import java.net.URI
import java.sql.*
import java.util.*

import com.github.kittinunf.result.Result


class DB(val conninfo: URI) {
    private var inTransaction: Boolean = false
    private var inRetry: Boolean = false

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
        val res = txn { prepared.obtain.executeQuery() }
        val rows = res.get()
        while (rows.next()) { results += rows.getInt(1) }
        rows.close()
        return results
    }

    @Synchronized
    fun release(tokens: Array<Int>) {
        txn {
            for (token in tokens) {
                prepared.release.setInt(1, token)
                prepared.release.addBatch()
            }
            prepared.release.executeBatch()
        }
    }

    @Synchronized
    fun<T: Any> txn(block: () -> T): Result<T, SQLException> {
        if (inTransaction) return Result.Success(block())

        inTransaction = true

        try {
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
            inTransaction = false
        }
    }

    @Synchronized
    fun<T: Any> retry(block: () -> T): T {
        if (inRetry) return block()

        inRetry = true

        try {
            val start = System.nanoTime()
            val limit = 1024
            var n = 1
            while (n < limit) {
                n += 1
                val res = txn { block() }
                when (res) {
                    is Result.Success -> return res.value
                }
                SystemNanos.sleep(System.nanoTime() - start)
            }
            val res = txn { block() }
            return res.get()
        } finally {
            inRetry = false
        }
    }
}