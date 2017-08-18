package li.pika.lockiton


data class PercentileSpec(val value: Double) {
    init {
        val msg = "Percentile must be between 0.00 and 100.00"
        if (value <= 0 || value > 100) throw Err(msg)
    }

    constructor(value: Number): this(value.toDouble())
}
