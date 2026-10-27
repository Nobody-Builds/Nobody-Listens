package com.nobodybuilds.listens.presentation.ytmusic.auth

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAuth
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicClients
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicRepository
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicSession
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.ui.theme.NobodyListensTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YouTubeMusicLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NobodyListensTheme {
                YouTubeMusicLoginScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        const val START_URL = "https://music.youtube.com/"
    }
}

@HiltViewModel
class YouTubeMusicLoginViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
) : ViewModel() {
    fun save(session: YouTubeMusicSession) {
        repository.saveSession(session)
    }
}

private data class YouTubeMusicWebUiState(
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoadingPage: Boolean = true,
    val pageProgress: Int = 0,
    val lastError: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeMusicLoginScreen(
    onClose: () -> Unit,
    viewModel: YouTubeMusicLoginViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // webView is null once the screen is gone, so the toast uses the app's context.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webUiState by remember { mutableStateOf(YouTubeMusicWebUiState()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }
    var poToken by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }

    BackHandler {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            else -> showExitDialog = true
        }
    }

    fun captureAndSubmit() {
        if (saving) return
        CookieManager.getInstance().flush()
        val cookie = YouTubeMusicAuth.mergeCookies(
            CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty(),
            CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty(),
            CookieManager.getInstance().getCookie("https://accounts.google.com").orEmpty(),
        )
        if (!YouTubeMusicAuth.hasLoginCookie(cookie)) {
            webUiState = webUiState.copy(lastError = "Sign in on YouTube Music first, then tap Done")
            scope.launch { snackbarHostState.showSnackbar("Sign in first, then tap Done") }
            return
        }
        saving = true
        runCatching {
            viewModel.save(
                YouTubeMusicSession(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    poToken = poToken,
                    accountName = accountName.ifBlank { webView?.title.orEmpty() },
                )
            )
        }.onSuccess {
            Toast.makeText(appContext, "YouTube Music connected", Toast.LENGTH_SHORT).show()
            onClose()
        }.onFailure { error ->
            saving = false
            val message = error.message ?: "Could not save the YouTube Music session"
            webUiState = webUiState.copy(lastError = message)
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Leave YouTube Music login?", fontFamily = GoogleSansRounded) },
            text = {
                Text(
                    "Your session is only saved after you tap Done.",
                    fontFamily = GoogleSansRounded
                )
            },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onClose() }) {
                    Text("Exit", fontFamily = GoogleSansRounded)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Stay", fontFamily = GoogleSansRounded)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "YouTube Music",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = {
                            if (webView?.canGoBack() == true) webView?.goBack() else showExitDialog = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = { webView?.goBack() },
                        enabled = webUiState.canGoBack && !saving,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Page back")
                    }
                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.goForward() },
                        enabled = webUiState.canGoForward && !saving,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Page forward")
                    }
                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.reload() },
                        enabled = !saving,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { webView?.loadUrl(YouTubeMusicLoginActivity.START_URL) },
                        enabled = !saving,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = "YouTube Music home")
                    }
                },
                floatingActionButton = {
                    SmallExtendedFloatingActionButton(
                        onClick = ::captureAndSubmit,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (saving) "Saving" else "Done",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (webUiState.isLoadingPage) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { (webUiState.pageProgress / 100f).coerceIn(0f, 1f) }
                )
            }
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = "Sign in with your Google account, then tap Done. Nobody Listens only stores the YouTube Music session on this device.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            webUiState.lastError?.let { errorText ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                webUiState = webUiState.copy(lastError = null)
                                webView?.reload()
                            }
                        ) {
                            Text("Retry", fontFamily = GoogleSansRounded)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = YouTubeMusicClients.WEB_USER_AGENT
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                allowFileAccess = false
                                allowContentAccess = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                settings.safeBrowsingEnabled = true
                            }
                            val mainHandler = Handler(Looper.getMainLooper())
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onVisitorData(value: String?) {
                                    if (!value.isNullOrBlank()) mainHandler.post { visitorData = value }
                                }

                                @JavascriptInterface
                                fun onDataSyncId(value: String?) {
                                    if (!value.isNullOrBlank()) mainHandler.post { dataSyncId = value }
                                }

                                @JavascriptInterface
                                fun onPoToken(value: String?) {
                                    if (!value.isNullOrBlank()) mainHandler.post { poToken = value }
                                }

                                @JavascriptInterface
                                fun onAccountName(value: String?) {
                                    if (!value.isNullOrBlank()) mainHandler.post { accountName = value }
                                }
                            }, "Android")
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webUiState = webUiState.copy(
                                        isLoadingPage = newProgress < 100,
                                        pageProgress = newProgress,
                                        canGoBack = view?.canGoBack() == true,
                                        canGoForward = view?.canGoForward() == true
                                    )
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    webUiState = webUiState.copy(
                                        isLoadingPage = false,
                                        canGoBack = view.canGoBack(),
                                        canGoForward = view.canGoForward()
                                    )
                                    view.evaluateJavascript(CAPTURE_SCRIPT, null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        webUiState = webUiState.copy(
                                            lastError = error?.description?.toString()?.ifBlank { "Page failed to load" }
                                                ?: "Page failed to load",
                                            isLoadingPage = false
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                        webUiState = webUiState.copy(
                                            lastError = "Page failed to load (${errorResponse?.statusCode})",
                                            isLoadingPage = false
                                        )
                                    }
                                }
                            }
                            loadUrl(YouTubeMusicLoginActivity.START_URL)
                            webView = this
                        }
                    }
                )
            }
        }
    }
}

private const val CAPTURE_SCRIPT = """
(function(){
  try {
    var visitor = '';
    var syncId = '';
    var token = '';
    var name = '';
    if (window.ytcfg && window.ytcfg.get) {
      visitor = window.ytcfg.get('VISITOR_DATA') || '';
      syncId = window.ytcfg.get('DATASYNC_ID') || window.ytcfg.get('DELEGATED_SESSION_ID') || '';
      token = window.ytcfg.get('PO_TOKEN') || window.ytcfg.get('WEB_PLAYER_CONTEXT_CONFIGS') || '';
      name = window.ytcfg.get('LOGGED_IN_USER_NAME') || window.ytcfg.get('USER_NAME') || '';
    }
    var cfg = window.yt && window.yt.config_ ? window.yt.config_ : {};
    visitor = visitor || cfg.VISITOR_DATA || '';
    syncId = syncId || cfg.DATASYNC_ID || '';
    if (!token) {
      var scripts = document.querySelectorAll('script');
      for (var i = 0; i < scripts.length; i++) {
        var match = (scripts[i].textContent || '').match(/"PO_TOKEN":"([^"]+)"/);
        if (match) { token = match[1]; break; }
      }
    }
    if (visitor) Android.onVisitorData(String(visitor));
    if (syncId) Android.onDataSyncId(String(syncId));
    if (token && typeof token === 'string' && token.length < 4000) Android.onPoToken(String(token));
    if (name) Android.onAccountName(String(name));
  } catch (e) {}
})()
"""
