package li.pika.lockiton

object Display {
    fun display(double: Double): String =  "%.2f".format(double)

    fun display(doubles: DoubleArray): String {
        return doubles.map { display(it) }.joinToString("/")
    }

    fun display(doubles: Collection<Double>): String {
        return doubles.map { display(it) }.joinToString("/")
    }

    fun display(endsAndMiddles: StatsReport.EndsAndMiddles): String {
        return endsAndMiddles.display
    }

    fun display(statsReport: StatsReport): String {
        return statsReport.display()
    }
}