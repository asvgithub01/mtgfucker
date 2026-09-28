package io.asv.mtgocr.ocrreader

internal data class EdScanSessionTotal(val copies: Int, val amount: Double, val unpricedCopies: Int) {
    companion object {
        fun calculate(rows: List<Pair<Int, Double?>>): EdScanSessionTotal {
            var copies = 0; var total = 0.0; var missing = 0
            rows.forEach { (quantity, price) ->
                val count = quantity.coerceAtLeast(0)
                copies += count
                if (price != null && price.isFinite() && price >= 0) total += count * price else missing += count
            }
            return EdScanSessionTotal(copies, total, missing)
        }
    }
}
