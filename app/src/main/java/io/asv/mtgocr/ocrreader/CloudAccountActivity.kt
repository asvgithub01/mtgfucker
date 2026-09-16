package io.asv.mtgocr.ocrreader

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.asv.mtgocr.ocrreader.model.Biblio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random

/** Google account and explicit controls for the Premium Firestore backup. */
class CloudAccountActivity : AppCompatActivity() {
    private lateinit var accountStatus: TextView
    private lateinit var syncStatus: TextView
    private lateinit var signIn: Button
    private lateinit var signOut: Button
    private lateinit var syncNow: Button
    private lateinit var restore: Button
    private lateinit var autoSave: SwitchCompat
    private lateinit var progress: ProgressBar
    private lateinit var background: CardArtBackgroundView
    private lateinit var credentialManager: CredentialManager
    private var busy = false
    private var backgroundRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        MagicPalette.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_account)
        background = findViewById(R.id.imgCloudBackground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            background.setRenderEffect(RenderEffect.createBlurEffect(9f, 9f, Shader.TileMode.CLAMP))
        }
        loadCollectionBackground()
        credentialManager = CredentialManager.create(this)
        accountStatus = findViewById(R.id.txtCloudAccountStatus)
        syncStatus = findViewById(R.id.txtCloudSyncStatus)
        signIn = findViewById(R.id.btnGoogleSignIn)
        signOut = findViewById(R.id.btnGoogleSignOut)
        syncNow = findViewById(R.id.btnCloudSyncNow)
        restore = findViewById(R.id.btnCloudRestore)
        autoSave = findViewById(R.id.switchCloudAutoSave)
        progress = findViewById(R.id.cloudProgress)
        findViewById<View>(R.id.btnCloudBack).setOnClickListener { finish() }
        signIn.setOnClickListener { beginGoogleSignIn() }
        signOut.setOnClickListener { signOut() }
        syncNow.setOnClickListener { synchronize() }
        restore.setOnClickListener { confirmRestore() }
        autoSave.isChecked = CloudLibrarySync.isAutoSyncEnabled(this)
        autoSave.setOnCheckedChangeListener { _, enabled ->
            CloudLibrarySync.setAutoSyncEnabled(this, enabled)
            toast(getString(if (enabled) R.string.cloud_auto_save_enabled else R.string.cloud_auto_save_disabled))
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        backgroundRequest++
        Glide.clear(background)
        super.onDestroy()
    }

    private fun loadCollectionBackground() {
        val request = ++backgroundRequest
        lifecycleScope.launch {
            val selected = withContext(Dispatchers.IO) {
                val library = LibraryCatalog.active(this@CloudAccountActivity)
                val collection: Biblio? = DataUtils.readSerializable(
                    this@CloudAccountActivity,
                    library.fileName
                )
                LaunchBackgroundPolicy.choose(
                    collection?.cards.orEmpty(),
                    LibraryCatalog.pinnedBackgroundId(this@CloudAccountActivity, library.id),
                    PremiumAccess.isEnabled(this@CloudAccountActivity)
                ) { size -> Random.nextInt(size) }
            }
            if (request != backgroundRequest || isFinishing || isDestroyed) return@launch
            if (selected == null) {
                CardImageCache.display(this@CloudAccountActivity, null, background)
                background.setArtworkOnly(false)
                background.setImageResource(R.drawable.mtgback)
            } else {
                background.setArtworkOnly(LaunchArtworkUrl.isAvailable(selected.imgPath))
                CardImageCache.displayKeepingCurrent(
                    this@CloudAccountActivity,
                    LaunchArtworkUrl.resolve(selected.imgPath),
                    background
                )
            }
        }
    }

    private fun beginGoogleSignIn() {
        if (!PremiumAccess.isEnabled(this)) {
            toast(getString(R.string.cloud_premium_required))
            return
        }
        val clientId = resources.getIdentifier("default_web_client_id", "string", packageName)
            .takeIf { it != 0 }?.let(::getString).orEmpty()
        if (!ensureFirebaseReady() || clientId.isBlank()) {
            toast(getString(R.string.cloud_not_configured))
            return
        }
        setBusy(true, getString(R.string.cloud_google_opening))
        lifecycleScope.launch {
            runCatching {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val result = credentialManager.getCredential(
                    context = this@CloudAccountActivity,
                    request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                )
                val custom = result.credential as? CustomCredential
                    ?: error(getString(R.string.cloud_google_invalid))
                require(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    getString(R.string.cloud_google_invalid)
                }
                val google = GoogleIdTokenCredential.createFrom(custom.data)
                FirebaseAuth.getInstance().signInWithCredential(
                    GoogleAuthProvider.getCredential(google.idToken, null)
                ).await()
                setBusy(true, getString(R.string.cloud_reconciling))
                CloudLibrarySync.reconcile(this@CloudAccountActivity)
            }.onSuccess { result ->
                showResult(result)
            }.onFailure { error ->
                showError(error)
            }
        }
    }

    private fun synchronize() {
        setBusy(true, getString(R.string.cloud_syncing))
        lifecycleScope.launch {
            runCatching { CloudLibrarySync.reconcile(this@CloudAccountActivity) }
                .onSuccess(::showResult)
                .onFailure(::showError)
        }
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_restore_title)
            .setMessage(R.string.cloud_restore_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.cloud_restore_action) { _, _ -> restoreLastCopy() }
            .show()
    }

    private fun restoreLastCopy() {
        setBusy(true, getString(R.string.cloud_restoring))
        lifecycleScope.launch {
            runCatching { CloudLibrarySync.restoreLastCloudCopy(this@CloudAccountActivity) }
                .onSuccess(::showResult)
                .onFailure(::showError)
        }
    }

    private fun signOut() {
        if (ensureFirebaseReady()) FirebaseAuth.getInstance().signOut()
        lifecycleScope.launch {
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            setBusy(false)
            render()
            syncStatus.text = getString(R.string.cloud_signed_out_local_kept)
        }
    }

    private fun showResult(result: CloudSyncResult) {
        setBusy(false)
        render()
        syncStatus.text = getString(
            R.string.cloud_sync_complete,
            result.uploadedLibraries,
            result.restoredLibraries,
            formatDate(result.backupAtMillis.takeIf { it > 0L } ?: result.syncedAtMillis),
            result.enrichedCardmarketCards
        )
    }

    private fun showError(error: Throwable) {
        setBusy(false)
        render()
        syncStatus.text = error.localizedMessage ?: getString(R.string.cloud_sync_error)
    }

    private fun render() {
        val configured = CloudLibrarySync.isConfigured(this)
        val premium = PremiumAccess.isEnabled(this)
        val user = if (configured) CloudLibrarySync.currentUser(this) else null
        accountStatus.text = when {
            !configured -> getString(R.string.cloud_not_configured)
            user != null -> getString(
                R.string.cloud_signed_in_as,
                user.displayName ?: user.email ?: getString(R.string.cloud_google_account)
            )
            else -> getString(R.string.cloud_signed_out)
        }
        val allowed = !busy && configured && premium && user != null
        signIn.isEnabled = !busy && configured && premium && user == null
        signOut.isEnabled = !busy && user != null
        syncNow.isEnabled = allowed
        restore.isEnabled = allowed
        autoSave.isEnabled = !busy && premium
        if (!busy) {
            val lastSync = CloudLibrarySync.lastSyncMillis(this)
            syncStatus.text = when {
                !premium -> getString(R.string.cloud_paused_last_copy_kept)
                lastSync > 0 -> getString(R.string.cloud_last_sync, formatDate(lastSync))
                else -> getString(R.string.cloud_never_synced)
            }
        }
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        if (message != null) syncStatus.text = message
        signIn.isEnabled = !value
        signOut.isEnabled = !value
        syncNow.isEnabled = !value
        restore.isEnabled = !value
        autoSave.isEnabled = !value
    }

    private fun ensureFirebaseReady(): Boolean = runCatching {
        FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this)
    }.getOrNull() != null

    private fun formatDate(value: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
