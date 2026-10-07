package io.asv.mtgocr.ocrreader

/** Conservative physical-language evidence, independent of printing/reference-image language. */
internal object TripleLanguageEvidence {
    fun scriptLanguage(text:String):String? {
        val kana=text.count { it.code in 0x3041..0x3096 || it.code in 0x30A1..0x30FA }
        val hangul=text.count { it.code in 0xAC00..0xD7A3 }
        return when {
            kana>=4 && hangul==0 -> "ja"
            hangul>=4 && kana==0 -> "ko"
            else -> null // Han alone does NOT distinguish Japanese from Chinese, nor ZHS/ZHT.
        }
    }
    fun scriptScore(text:String):Int {
        val native=text.count { it.code in 0x3041..0x30FA || it.code in 0x3400..0x9FFF || it.code in 0xAC00..0xD7A3 }
        // Latin is first: on ties keep it rather than replacing it with another model's Latin output.
        return if(native>=4 && native*4>=text.count(Char::isLetter)) native else 0
    }
}

/** Missing evidence is not contradictory evidence. One noisy frame must not erase a decision. */
internal class TripleLanguageConsensus {
    private var key=""
    private var pending:String?=null
    private var count=0
    private var confirmed:String?=null
    fun reset() { key="";pending=null;count=0;confirmed=null }
    fun confirmExactTitle(identity:String,code:String) { key=identity;confirmed=code;pending=null;count=0 }
    fun observe(identity:String,code:String?):String? {
        if(identity!=key) { reset();key=identity }
        if(code==null) return confirmed
        if(code==confirmed) { pending=null;count=0;return confirmed }
        if(pending!=code) { pending=code;count=0 }
        count++
        if(count >= if(confirmed==null) 2 else 3) { confirmed=code;pending=null;count=0 }
        return confirmed
    }
}

internal object TripleLanguageFusion {
    data class Decision(val code:String?,val conflict:Boolean,val source:String)
    fun decide(rules:String,statistical:String?,title:TitleLanguageIndex.Evidence):Decision {
        val script=TripleLanguageEvidence.scriptLanguage(rules)
        // Reuse the scanner's distinctive short mana-ability words; do not accept ambiguous Italian verbs alone.
        val short=ScanLanguagePolicy.shortRulesLanguage(rules)?.takeIf { it in setOf("es","pt","en") }
        val rule=script ?: short ?: statistical?.takeUnless { it in setOf("zh","zhs","zht") }
        val choices=title.candidates
        if(title.conflict || (rule!=null && choices.isNotEmpty() && rule !in choices))
            return Decision(null,true,"conflict")
        return Decision(rule ?: choices.singleOrNull(),false,
            if(script!=null) "script" else if(short!=null) "shortRules" else if(rule!=null) "rules" else if(choices.size==1) "exactTitle" else "unknown")
    }
}
