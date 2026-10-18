package io.asv.mtgocr.ocrreader

/** Single decision point for Triple live OCR and saved-photo language enrichment.
 * Missing title evidence is not a veto. Contradictions are; printing language is never input.
 * The OCR/ML adapters supply observations, not decisions about the owned card.
 */
internal object CardLanguageResolver {
    data class Decision(val code:String?,val conflict:Boolean,val source:String)
    fun resolve(text:String,statistical:String?,title:TitleLanguageIndex.Evidence):Decision {
        val script=TripleLanguageEvidence.scriptLanguage(text) ?: chineseRules(text)
        val short=ScanLanguagePolicy.shortRulesLanguage(text)?.takeIf { it in setOf("es","pt","en") }
        val rule=script ?: short ?: statistical?.takeIf { it in setOf("en","es","pt","it","fr","de","ja","ko","ru") }
        val choices=title.candidates
        if(title.conflict || (rule!=null && choices.isNotEmpty() && rule !in choices))
            return Decision(null,true,"conflict")
        return Decision(rule ?: choices.singleOrNull(),false,
            if(script!=null) "script" else if(short!=null) "shortRules" else if(rule!=null) "rules" else if(choices.size==1) "exactTitle" else "unknown")
    }

    data class Reading(val model:String,val text:String,val titles:List<String>)
    /** Each script-specific model can hallucinate on another script. Select native text only
     * with substantial script evidence; otherwise use Latin. Keep all readings in the report.
     * Han alone cannot certify Chinese variant or Japanese and requires an exact title.
     */
    fun select(readings:List<Reading>):Reading? = readings.filter {
        TripleLanguageEvidence.scriptScore(it.text)>0
    }.maxByOrNull { TripleLanguageEvidence.scriptScore(it.text) }
        ?: readings.firstOrNull { it.model=="latin" }
        ?: readings.firstOrNull()

    fun statisticalConsensus(codes:List<String?>):String? = codes.filterNotNull().distinct().singleOrNull()

    /** Han alone is insufficient. Require Chinese grammar/rules phrases AND at least two
     * distinct variant-specific characters. Japanese/Korean scripts and mixed variants veto.
     * This intentionally leaves blurred/short Chinese titles unknown rather than guessing.
     */
    internal fun chineseRules(text:String):String? {
        if(text.any { it.code in 0x3040..0x30FF || it.code in 0xAC00..0xD7AF }) return null
        if(text.count { it.code in 0x3400..0x9FFF }<20) return null
        val compact=text.filterNot(Char::isWhitespace)
        if(listOf("如果","將其","将其","移出","目標","目标","墳墓場","坟墓场")
                .count { it in compact }<2) return null
        val traditional="獨體須墳場將遊戲隻觀來儀過讓見與為戰對擊傷產點".count { it in compact }
        val simplified="独体须坟场将戏观来仪过让见与为战对击伤产点".count { it in compact }
        return when {
            traditional>=2 && simplified==0 -> "zht"
            simplified>=2 && traditional==0 -> "zhs"
            else -> null
        }
    }
}
