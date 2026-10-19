package io.asv.mtgocr.ocrreader

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.asv.mtgocr.ocrreader.data.SelectedPrices
import java.io.File

class PhotoGalleryActivity:AppCompatActivity() {
    private lateinit var image:PhotoPriceImageView
    private lateinit var status:TextView
    private var photos=emptyList<PhotoScanEntry>()
    private var index=0
    override fun onCreate(state:Bundle?) {
        MagicPalette.applyTheme(this);super.onCreate(state)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val id=intent.getStringExtra(PhotoScanDetailActivity.EXTRA_PHOTO_SCAN_ID).orEmpty()
        photos=PhotoGroups.members(this,id)
        if(photos.isEmpty()) { finish();return }
        index=(state?.getInt("index") ?: photos.indexOfFirst { it.id==id }.coerceAtLeast(0)).coerceIn(0,photos.lastIndex)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(android.graphics.Color.BLACK) }
        val controls=LinearLayout(this)
        fun button(text:String,action:()->Unit) { controls.addView(Button(this).apply { this.text=text;setOnClickListener { action() } },LinearLayout.LayoutParams(0,-2,1f)) }
        button("‹") { change(-1) };button(getString(R.string.close)) { finish() };button("›") { change(1) }
        root.addView(controls)
        status=TextView(this).apply { setTextColor(android.graphics.Color.WHITE);gravity=android.view.Gravity.CENTER }
        root.addView(status)
        image=PhotoPriceImageView(this).apply { onSwipe={ direction -> change(direction) } }
        root.addView(image,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);show()
    }
    private fun change(delta:Int) { index=(index+delta).coerceIn(0,photos.lastIndex);show() }
    private fun show() {
        val entry=photos[index]
        status.text="${index+1} / ${photos.size}"
        image.showPrices(entry.cards)
        Glide.with(this).load(File(entry.imagePath)).placeholder(CardLoadingDrawable()).dontAnimate().dontTransform().into(image)
        val cards=entry.cards.map { io.asv.mtgocr.ocrreader.model.CardInfo("","","","","1").apply { printingUuid=it.printingUuid;finish=it.finish } }
        SelectedPrices.ensure(this,cards,Runnable { if(!isFinishing && !isDestroyed && photos[index].id==entry.id) image.showPrices(entry.cards) })
    }
    override fun onSaveInstanceState(out:Bundle) { out.putInt("index",index);super.onSaveInstanceState(out) }
}
