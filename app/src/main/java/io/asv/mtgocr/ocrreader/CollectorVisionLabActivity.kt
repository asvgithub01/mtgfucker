package io.asv.mtgocr.ocrreader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Isolated hosted-demo experiment. No repository, scanner, collection or JavaScript bridge. */
class CollectorVisionLabActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var pendingCamera: PermissionRequest? = null
    private var stopped = false
    private var loaded = false
    private var loadFailed = false
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingCamera
        pendingCamera = null
        if (request != null) {
            if (granted && !stopped && !isFinishing &&
                CollectorVisionWebPolicy.trustedOrigin(request.origin.toString()) &&
                CollectorVisionWebPolicy.trustedPage(web.url.orEmpty())) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                request.deny()
                status.setText(R.string.collector_vision_camera_denied)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector_vision_lab)
        supportActionBar?.hide()
        status = findViewById(R.id.collectorVisionStatus)
        web = findViewById(R.id.collectorVisionWeb)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                blockNavigation(request.url.toString())
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = blockNavigation(url)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loadFailed = true
                    status.setText(R.string.collector_vision_load_error)
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    loadFailed = true
                    status.setText(R.string.collector_vision_load_error)
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                // A loaded page is not evidence that its models or camera work.
                if (!loadFailed && CollectorVisionWebPolicy.trustedPage(url)) status.setText(R.string.collector_vision_runtime_note)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (stopped || isFinishing || !CollectorVisionWebPolicy.canRequestVideo(request.origin.toString(), web.url.orEmpty(), request.resources)) {
                    request.deny()
                    return
                }
                pendingCamera?.deny()
                pendingCamera = null
                if (ContextCompat.checkSelfPermission(this@CollectorVisionLabActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    pendingCamera = request
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingCamera === request) pendingCamera = null
            }
        }
        findViewById<View>(R.id.collectorVisionStart).setOnClickListener {
            loadFailed = false
            loaded = true
            status.setText(R.string.collector_vision_loading)
            web.loadUrl(CollectorVisionWebPolicy.DEMO_URL)
        }
        findViewById<View>(R.id.collectorVisionBrowser).setOnClickListener {
            // User action only. Never open redirects, intent:// links or arbitrary external URLs.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CollectorVisionWebPolicy.DEMO_URL)))
            } catch (_: android.content.ActivityNotFoundException) {
                status.setText(R.string.collector_vision_browser_missing)
            }
        }
        findViewById<View>(R.id.collectorVisionClose).setOnClickListener { finish() }
    }

    private fun blockNavigation(url: String): Boolean {
        if (CollectorVisionWebPolicy.trustedPage(url)) return false
        Toast.makeText(this, R.string.collector_vision_navigation_blocked, Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        web.onResume()
    }

    override fun onStop() {
        stopped = true
        pendingCamera?.deny()
        pendingCamera = null
        // Destroy the document and its getUserMedia streams; onPause alone does not release them.
        web.stopLoading()
        web.loadUrl("about:blank")
        web.onPause()
        if (loaded) status.setText(R.string.collector_vision_resume)
        super.onStop()
    }

    override fun onDestroy() {
        pendingCamera?.deny()
        pendingCamera = null
        web.stopLoading()
        web.removeAllViews()
        web.destroy()
        super.onDestroy()
    }
}
