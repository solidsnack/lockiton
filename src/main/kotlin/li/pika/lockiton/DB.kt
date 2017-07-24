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
        DriverManager.getConnection(jdbcURI, jdbcProperties) as Connection
    }

    private val prepared by lazy {
        object {
            val obtain = cxn.prepareStatement("SELECT * FROM obtain()")
        }
    }

    @Synchronized
    fun obtain(): Array<String> {
        var results: Array<String> = arrayOf()
        val rows = prepared.obtain.executeQuery()
        while (rows.next()) {
            results += rows.getInt(1).toString()
        }
        rows.close()
        return results
    }
}