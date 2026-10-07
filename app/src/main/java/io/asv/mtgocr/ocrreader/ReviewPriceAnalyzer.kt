package io.asv.mtgocr.ocrreader
import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit review job; never changes ownership, printing, language or saved valuation. */
internal class ReviewPriceAnalyzer(context:Context) {
    private val prefs=context.getSharedPreferences("review_price_ranges_v1",0)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val client=OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).build()
    @Volatile private var stopped=false
    @Volatile private var active:okhttp3.Call?=null
    private fun key(name:String,finish:String)=TripleReadingPolicy.identity(name)+"|"+finish
    fun cached(name:String,finish:String):ReviewPriceRanges.Result?=runCatching {
        val json=JSONObject(prefs.getString(key(name,finish),null) ?: return null)
        fun spread(key:String)=json.optJSONObject(key)?.let { ReviewPriceRanges.Spread(it.getDouble("euros"),if(it.isNull("percent")) null else it.getDouble("percent")) }
        ReviewPriceRanges.Result(spread("editions"),spread("languages"),json.getInt("priced"),json.getInt("total"))
    }.getOrNull()
    fun analyze(name:String,canonical:String,finish:String,callback:(ReviewPriceRanges.Result?,Throwable?)->Unit) {
        worker.execute {
            val result=runCatching {
                val query=java.net.URLEncoder.encode("!\"${canonical.replace("\"", "")}\" lang:any game:paper","UTF-8")
                var url:String?="https://api.scryfall.com/cards/search?q=$query&unique=prints&include_multilingual=true&include_extras=true"
                val quotes=mutableListOf<ReviewPriceRanges.Quote>();val ids=mutableSetOf<String>();var total=0;var pages=0
                val priceKey=when(finish) {"foil"->"eur_foil";"nonfoil","normal"->"eur";else->error("No comparable EUR feed for this finish")}
                while(url!=null) {
                    check(!stopped && ++pages<=30) { "Cancelled or incomplete catalog" }
                    check(url.startsWith("https://api.scryfall.com/cards/search?"))
                    val call=client.newCall(Request.Builder().url(url).header("User-Agent","MtgOcr-PriceReview/1.0").header("Accept","application/json").build())
                    active=call;if(stopped)call.cancel()
                    val json=call.execute().use { response->check(response.isSuccessful);JSONObject(requireNotNull(response.body).string()) }
                    val data=json.getJSONArray("data")
                    for(i in 0 until data.length()) {
                        val card=data.getJSONObject(i);if(!ids.add(card.getString("id")))continue
                        val finishes=card.optJSONArray("finishes") ?: continue
                        if((0 until finishes.length()).none { finishes.getString(it)==finish })continue
                        total++
                        val amount=card.optJSONObject("prices")?.optString(priceKey)?.toDoubleOrNull() ?: continue
                        if(amount.isFinite() && amount>=0) quotes+=ReviewPriceRanges.Quote(card.getString("set")+"|"+card.getString("collector_number"),card.getString("lang"),amount)
                    }
                    url=if(json.optBoolean("has_more"))json.getString("next_page") else null
                    if(url!=null)Thread.sleep(100)
                }
                val value=ReviewPriceRanges.calculate(quotes,total)
                fun spread(s:ReviewPriceRanges.Spread?):Any=s?.let { JSONObject().put("euros",it.euros).put("percent",it.percent ?: JSONObject.NULL) } ?: JSONObject.NULL
                if(!stopped)prefs.edit().putString(key(name,finish),JSONObject().put("editions",spread(value.editions)).put("languages",spread(value.languages)).put("priced",value.priced).put("total",value.total).put("at",System.currentTimeMillis()).toString()).apply()
                value
            }
            active=null;main.post { if(!stopped)callback(result.getOrNull(),result.exceptionOrNull()) }
        }
    }
    fun cancel() { active?.cancel() }
    fun close() { stopped=true;active?.cancel();worker.shutdownNow() }
}
