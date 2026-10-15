package io.asv.mtgocr.ocrreader

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Account-bound listener, present only while its screen is started. Never writes inventory. */
internal class CloudBuylistObserver(private val activity: AppCompatActivity, private val updated: (String?) -> Unit): DefaultLifecycleObserver {
    private var registration: ListenerRegistration?=null
    private var auth: FirebaseAuth?=null
    private var authListener: FirebaseAuth.AuthStateListener?=null
    private var generation=0
    init { activity.lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) {
        if(!CloudLibrarySync.isConfigured(activity))return
        auth=FirebaseAuth.getInstance()
        authListener=FirebaseAuth.AuthStateListener { firebase ->
            registration?.remove();registration=null
            val expected=++generation
            val user=firebase.currentUser ?: return@AuthStateListener
            var seen:String?=null
            registration=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("shopping").document("state")
                .addSnapshotListener { head,error ->
                    if(expected!=generation || !PremiumAccess.isEnabled(activity) || !CloudLibrarySync.isAutoSyncEnabled(activity))return@addSnapshotListener
                    if(error!=null) { updated(activity.getString(R.string.buy_sync_error));return@addSnapshotListener }
                    val revision=head?.getString("activeSnapshotId").orEmpty()+":"+head?.getString("sha256").orEmpty()
                    if(seen==revision)return@addSnapshotListener
                    seen=revision
                    activity.lifecycleScope.launch {
                        val result=withContext(Dispatchers.IO) { runCatching { CloudLibrarySync.syncBuylists(activity,user.uid) } }
                        if(expected==generation && !activity.isFinishing && !activity.isDestroyed)
                            updated(result.exceptionOrNull()?.let { activity.getString(R.string.buy_sync_error) })
                    }
                }
        }.also { auth?.addAuthStateListener(it) }
    }
    override fun onStop(owner: LifecycleOwner) {
        ++generation;registration?.remove();registration=null
        authListener?.let { auth?.removeAuthStateListener(it) };authListener=null;auth=null
    }
}
