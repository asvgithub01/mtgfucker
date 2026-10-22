package io.asv.mtgocr.ocrreader.data.catalog

import io.asv.mtgocr.ocrreader.EditionSearch
import io.asv.mtgocr.ocrreader.domain.catalog.SetSearchMatcher
import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary

object LegacySetSearchMatcher : SetSearchMatcher {
    override fun matches(set: SetSummary, query: String) = EditionSearch.matches(set.code, set.name, query)
}
