package io.asv.mtgocr.ocrreader

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Shared collection palette, glass surfaces and gallery close affordance. */
object CollectionPopupChrome {
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun color(c: Context, attr: Int): Int = TypedValue().also { c.theme.resolveAttribute(attr, it, true) }.data
    fun panel(c: Context) = ArcaneGlassLayout(c).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10))
    }
    fun label(c: Context, text: CharSequence, size: Float = 16f) = TextView(c).apply {
        this.text=text;textSize=size;setTextColor(color(c,R.attr.appGlassTextColor))
        setPadding(0,dp(c,4),0,dp(c,4))
    }
    fun button(c: Context, text: Int, action: () -> Unit) = MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(text);isAllCaps=false;cornerRadius=dp(c,14)
        setTextColor(color(c,com.google.android.material.R.attr.colorSecondary));strokeColor=ColorStateList.valueOf(color(c,com.google.android.material.R.attr.colorSecondary))
        setOnClickListener { action() }
    }
    fun icon(c: Context, image: Int, description: Int, action: () -> Unit) = ImageButton(c).apply {
        setImageResource(image);contentDescription=c.getString(description)
        imageTintList=ColorStateList.valueOf(Color.WHITE)
        val attr=TypedValue();c.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,attr,true);setBackgroundResource(attr.resourceId)
        setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12))
        layoutParams=LinearLayout.LayoutParams(dp(c,48),dp(c,48));setOnClickListener { action() }
    }
    fun prepare(activity: AppCompatActivity, fullScreen: Boolean = false) {
        MagicPalette.applyTheme(activity)
        activity.theme.applyStyle(if(fullScreen) R.style.ThemeOverlay_Mtg_BuylistFullscreen else R.style.ThemeOverlay_Mtg_CollectionPopup,true)
    }
    fun install(activity: AppCompatActivity, title: Int, compact: Boolean = false, fullScreen: Boolean = false, headerContent: ((LinearLayout) -> Unit)? = null): LinearLayout {
        val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(activity,10),dp(activity,10),dp(activity,10),dp(activity,10)) }
        activity.setContentView(root)
        CollectionScreenBackground.install(activity)
        activity.window.setLayout(if(fullScreen) -1 else (activity.resources.displayMetrics.widthPixels*.96f).toInt(),if(fullScreen) -1 else if(compact) -2 else (activity.resources.displayMetrics.heightPixels*.92f).toInt())
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val header=panel(activity)
        val titleRow=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        titleRow.addView(label(activity,activity.getString(title),22f),LinearLayout.LayoutParams(0,-2,1f))
        titleRow.addView(icon(activity,android.R.drawable.ic_menu_close_clear_cancel,R.string.close) { activity.finish() }.apply { id=R.id.btnClosePopup })
        header.addView(titleRow)
        headerContent?.invoke(header)
        root.addView(header,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(activity,10) })
        return root
    }
}

object ShoppingAccess {
    @JvmStatic fun require(activity: android.app.Activity): Boolean {
        if(PremiumAccess.isEnabled(activity))return true
        Toast.makeText(activity,R.string.premium_required,Toast.LENGTH_LONG).show()
        return false
    }
}
