package com.example

import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ActivityAuditFeedSheet
import com.example.ui.components.AiAssistantChatbotSheet
import com.example.ui.components.BiometricVaultModal
import com.example.ui.components.DocumentScannerModal
import com.example.ui.components.GuidedOnboardingTourBanner
import com.example.ui.components.ImageEditorModal
import com.example.ui.components.MediaLightboxModal
import com.example.ui.components.QrCodeScannerModal
import com.example.ui.components.ServerEndpointConfigDialog
import com.example.ui.components.ShareFileLinkDialog
import com.example.ui.components.ShareFolderCollaboratorsDialog
import com.example.ui.components.StorageCleanerModal
import com.example.ui.components.UploadMediaBottomSheet
import com.example.widget.GalleryHubDashboardWidget
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FoldersTabScreen
import com.example.ui.screens.GalleryTabScreen
import com.example.ui.screens.SettingsTabScreen
import com.example.ui.screens.ShareLinksTabScreen
import com.example.ui.screens.TrashTabScreen
import com.example.data.remote.FirebaseAuthManager
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.GalleryHubTheme
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.GalleryHubViewModel
import com.example.ui.viewmodel.MainNavTab
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {
    private val pendingDeepLinkState = mutableStateOf<String?>(null)
    private val pendingExternalShareUrisState = mutableStateOf<List<Uri>>(emptyList())
    private val pendingWidgetActionState = mutableStateOf<String?>(null)
    private lateinit var appViewModel: GalleryHubViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CRITICAL: Configure Firebase App Check synchronously before any ViewModel, Compose UI, or Firebase call
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        appViewModel = androidx.lifecycle.ViewModelProvider(this)[GalleryHubViewModel::class.java]
        captureIncomingIntent(intent)
        setContent {
            val viewModel = appViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val pendingDeepLink by pendingDeepLinkState
            val pendingExternalShareUris by pendingExternalShareUrisState
            val pendingWidgetAction by pendingWidgetActionState

            LaunchedEffect(pendingDeepLink) {
                val url = pendingDeepLink
                if (!url.isNullOrBlank()) {
                    pendingDeepLinkState.value = null
                    viewModel.handleIncomingShareLink(url)
                }
            }

            LaunchedEffect(pendingExternalShareUris, uiState.isInitialized) {
                if (uiState.isInitialized && pendingExternalShareUris.isNotEmpty()) {
                    val uris = pendingExternalShareUris
                    pendingExternalShareUrisState.value = emptyList()
                    viewModel.handleIncomingExternalShare(uris)
                }
            }

            LaunchedEffect(pendingWidgetAction, uiState.isInitialized) {
                val action = pendingWidgetAction
                if (uiState.isInitialized && !action.isNullOrBlank()) {
                    pendingWidgetActionState.value = null
                    when (action) {
                        GalleryHubDashboardWidget.ACTION_WIDGET_UPLOAD -> viewModel.setUploadModalOpen(true)
                        GalleryHubDashboardWidget.ACTION_WIDGET_SCAN_PDF -> viewModel.setDocumentScannerOpen(true)
                        GalleryHubDashboardWidget.ACTION_WIDGET_SCAN_QR -> viewModel.setQrScannerOpen(true)
                    }
                }
            }

            GalleryHubTheme(
                themeMode = uiState.themeMode,
                accentHex = uiState.accentHex
            ) {
                GalleryHubRootApp(
                    viewModel = viewModel,
                    onEnterVideoPipMode = { enterVideoPipMode() }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && ::appViewModel.isInitialized) {
            appViewModel.lockVault()
            appViewModel.applyPendingDynamicAppIconOnBackground(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureIncomingIntent(intent)
    }

    private fun enterVideoPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }.onFailure {
                if (::appViewModel.isInitialized) {
                    appViewModel.showToastMessage("Picture-in-Picture is not supported on this device")
                }
            }
        } else if (::appViewModel.isInitialized) {
            appViewModel.showToastMessage("Picture-in-Picture requires Android 8.0 or newer")
        }
    }

    private fun captureIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Home Screen Widget shortcut actions
        val widgetAction = intent.getStringExtra(GalleryHubDashboardWidget.EXTRA_WIDGET_ACTION)
        if (!widgetAction.isNullOrBlank()) {
            pendingWidgetActionState.value = widgetAction
            return
        }

        when (intent.action) {
            // 2. Public Share Deep-Link URLs
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: return
                if (com.example.util.ShareLinkCodec.isShareDeepLinkUri(dataUri)) {
                    pendingDeepLinkState.value = dataUri.toString()
                }
            }
            // 3. Receive single file from another app (ACTION_SEND)
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (streamUri != null) {
                    pendingExternalShareUrisState.value = listOf(streamUri)
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank() &&
                        com.example.util.ShareLinkCodec.parseShareInput(sharedText) != null
                    ) {
                        pendingDeepLinkState.value = sharedText.trim()
                    }
                }
            }
            // 4. Receive multiple files from another app (ACTION_SEND_MULTIPLE)
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.filterNotNull()
                    .orEmpty()
                if (uris.isNotEmpty()) {
                    pendingExternalShareUrisState.value = uris
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryHubRootApp(
    viewModel: GalleryHubViewModel,
    onEnterVideoPipMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredMedia by viewModel.filteredMedia.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isStudioPaletteSheetOpen by remember { mutableStateOf(false) }

    // Companion Firebase Auth listener for Firestore metadata sync (leaves Render login untouched)
    DisposableEffect(Unit) {
        val auth = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                viewModel.onFirebaseGoogleSignedIn(user.uid, user.email)
            } else {
                viewModel.onFirebaseGoogleSignedOut()
            }
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
        }
    }

    // Silent Credential Manager sign-in check on startup for returning Google Cloud Sync users
    LaunchedEffect(Unit) {
        FirebaseAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = coroutineScope,
            onAuthSuccess = { user ->
                viewModel.onFirebaseGoogleSignedIn(user.uid, user.email)
            }
        )
    }

    fun launchIntentSafely(intent: android.content.Intent, fallbackError: String = "No compatible app found on this device") {
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            viewModel.showToastMessage(fallbackError)
        }
    }

    // Zero-Permission Android Photo Picker for Images
    val pickPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUploadUris(uris)
        }
    }

    // Zero-Permission Android Video Picker for Videos
    val pickVideosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUploadUris(uris)
        }
    }

    // Zero-Permission Document Picker for PDF, Office, Code, Archives
    val pickDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUploadUris(uris)
        }
    }

    // Zero-Permission Profile Avatar Picker
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatarImage(uri)
        }
    }

    // Zero-Permission Pickers for Re-linking Missing Server Files (HTTP 404 recovery)
    var pendingRelinkTarget by remember { mutableStateOf<com.example.data.model.UnifiedMediaItem?>(null) }
    val relinkVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingRelinkTarget
        pendingRelinkTarget = null
        if (uri != null && target != null) {
            viewModel.relinkMissingMediaFromUri(target, uri)
        }
    }
    val relinkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingRelinkTarget
        pendingRelinkTarget = null
        if (uri != null && target != null) {
            viewModel.relinkMissingMediaFromUri(target, uri)
        }
    }

    // Display Toast / Snackbar notifications
    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToastMessage()
        }
    }

    // BackHandler for secondary tabs or open folders
    BackHandler(
        enabled = uiState.userSession != null &&
            (uiState.currentTab != MainNavTab.GALLERY || uiState.activeFolder != null || uiState.isSelectionMode)
    ) {
        when {
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.activeFolder != null -> viewModel.openFolderInGallery(null)
            uiState.currentTab != MainNavTab.GALLERY -> viewModel.selectMainTab(MainNavTab.GALLERY)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    if (uiState.userSession == null) {
        AuthScreen(
            serverHealth = uiState.serverHealth,
            isLoading = uiState.authLoading,
            errorMessage = uiState.authError,
            themeMode = uiState.themeMode,
            accentHex = uiState.accentHex,
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onEnterSandbox = viewModel::enterInteractiveSandbox,
            onSelectThemeMode = viewModel::setThemeMode,
            onSelectAccentColor = viewModel::setAccentColor,
            onClearError = viewModel::clearAuthError,
            onOpenServerConfig = { viewModel.setServerConfigDialogOpen(true) },
            onPingServer = { viewModel.pingServer() }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 640.dp
            val isGuestMode = uiState.userSession?.isOfflineSandbox == true

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.example.ui.components.GalleryHubLogoMark(
                                    size = 38.dp,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    testTag = "topbar_logo_mark"
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "GalleryHub",
                                            fontFamily = PlayfairDisplayFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 19.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "· ${uiState.allMedia.count { !it.isDeleted }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        com.example.ui.components.CloudConnectionStatusIndicator(
                                            isCloudConnected = uiState.isCloudConnected,
                                            isChecking = uiState.serverHealth.isChecking || uiState.isSyncing,
                                            isGuestMode = isGuestMode,
                                            onClick = {
                                                if (!isGuestMode) {
                                                    viewModel.pingServer()
                                                }
                                            }
                                        )
                                        com.example.ui.components.SyncNowStatusButton(
                                            isSyncing = uiState.isSyncing,
                                            pendingSyncCount = uiState.pendingSyncCount,
                                            onSyncNow = viewModel::syncNow
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            // 1-Tap Studio Look & Suggestions Switcher
                            IconButton(
                                onClick = { isStudioPaletteSheetOpen = true },
                                modifier = Modifier.testTag("topbar_studio_palette_button")
                            ) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = "Studio Look Suggestions",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Studio Assistant Trigger
                            IconButton(
                                onClick = { viewModel.setChatbotOpen(true) },
                                modifier = Modifier.testTag("fab_ai_assistant")
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Studio Guide",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.setActivityDrawerOpen(true) },
                                modifier = Modifier.testTag("topbar_activity_feed_button")
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "Recent Activity",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (uiState.unreadActivityCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(16.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.background,
                                                    shape = CircleShape
                                                )
                                                .testTag("recent_activity_unread_badge")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${uiState.unreadActivityCount.coerceAtMost(99)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        lineHeight = 9.sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen && !uiState.isSelectionMode) {
                        ModularStudioBottomDock(
                            currentTab = uiState.currentTab,
                            trashCount = uiState.trashMedia.size,
                            isUploading = uiState.isUploading,
                            isGuestMode = isGuestMode,
                            onSelectTab = viewModel::selectMainTab,
                            onUploadClick = { viewModel.setUploadModalOpen(true) }
                        )
                    }
                },
                floatingActionButton = {
                    if (isExpandedScreen && !uiState.isSelectionMode) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.setUploadModalOpen(true) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text("Upload", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("fab_upload_media")
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        val railHaptic = LocalHapticFeedback.current
                        val railView = LocalView.current
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            MainNavTab.entries.forEach { tab ->
                                val icon = when (tab) {
                                    MainNavTab.GALLERY -> Icons.Default.PhotoLibrary
                                    MainNavTab.FOLDERS -> Icons.Default.Folder
                                    MainNavTab.LINKS -> Icons.Default.Link
                                    MainNavTab.TRASH -> Icons.Default.DeleteOutline
                                    MainNavTab.SETTINGS -> Icons.Default.Settings
                                }
                                NavigationRailItem(
                                    selected = uiState.currentTab == tab,
                                    onClick = {
                                        railHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        railView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        viewModel.selectMainTab(tab)
                                    },
                                    icon = { Icon(icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.key}")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (uiState.isTourActive) {
                            GuidedOnboardingTourBanner(
                                stepIndex = uiState.tourStepIndex,
                                onNext = viewModel::nextOnboardingStep,
                                onPrev = viewModel::previousOnboardingStep,
                                onDismiss = viewModel::completeOnboardingTour
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = uiState.currentTab,
                                transitionSpec = {
                                    val isForward = targetState.ordinal >= initialState.ordinal
                                    val slideOffset = { fullWidth: Int ->
                                        if (isForward) fullWidth / 10 else -fullWidth / 10
                                    }
                                    (fadeIn(animationSpec = tween(230)) +
                                        slideInHorizontally(
                                            animationSpec = tween(250),
                                            initialOffsetX = slideOffset
                                        ) +
                                        scaleIn(
                                            initialScale = 0.98f,
                                            animationSpec = tween(230)
                                        )).togetherWith(
                                        fadeOut(animationSpec = tween(170)) +
                                            slideOutHorizontally(
                                                animationSpec = tween(200),
                                                targetOffsetX = { fullWidth ->
                                                    if (isForward) -fullWidth / 12 else fullWidth / 12
                                                }
                                            ) +
                                            scaleOut(
                                                targetScale = 0.98f,
                                                animationSpec = tween(170)
                                            )
                                    )
                                },
                                label = "studio_tab_transition"
                            ) { targetTab ->
                                when (targetTab) {
                                    MainNavTab.GALLERY -> {
                                        GalleryTabScreen(
                                            mediaItems = filteredMedia,
                                            folders = uiState.myFolders,
                                            sharedFolders = uiState.sharedFolders,
                                            activeFolder = uiState.activeFolder,
                                            activeCategory = uiState.activeCategory,
                                            searchQuery = uiState.searchQuery,
                                            sortField = uiState.sortField,
                                            sortOrder = uiState.sortOrder,
                                            viewMode = uiState.viewMode,
                                            isLoading = uiState.isLoadingMedia,
                                            isSyncing = uiState.isSyncing,
                                            isUploading = uiState.isUploading,
                                            isGuestMode = isGuestMode,
                                            isCloudConnected = uiState.isCloudConnected,
                                            isCheckingConnection = uiState.serverHealth.isChecking,
                                            isSelectionMode = uiState.isSelectionMode,
                                            selectedIds = uiState.selectedIds,
                                            downloadingOfflineIds = uiState.downloadingOfflineIds,
                                            missingServerMediaIds = uiState.missingServerMediaIds,
                                            pendingSyncCount = uiState.pendingSyncCount,
                                            isZipExporting = uiState.isZipExporting,
                                            serverBaseUrl = uiState.serverHealth.baseUrl,
                                            smartCollectionFilter = uiState.smartCollectionFilter,
                                            hiddenVaultCount = uiState.hiddenMediaIds.size,
                                            isOcrIndexing = uiState.isOcrIndexing,
                                            ocrTextByMediaId = uiState.ocrTextByMediaId,
                                            onThisDayMemories = uiState.onThisDayMemories,
                                            uploadQueue = uiState.uploadQueue,
                                            wifiOnlyUploads = uiState.wifiOnlyUploads,
                                            onSearchQueryChange = viewModel::setSearchQuery,
                                            onCategoryChange = viewModel::setCategoryFilter,
                                            onSmartCollectionChange = viewModel::setSmartCollectionFilter,
                                            onSortFieldChange = viewModel::setSortField,
                                            onToggleSortOrder = viewModel::toggleSortOrder,
                                            onViewModeChange = viewModel::setViewMode,
                                            onSelectFolder = viewModel::openFolderInGallery,
                                            onOpenUploadModal = { viewModel.setUploadModalOpen(true) },
                                            onOpenPrivateVault = viewModel::openVaultSheet,
                                            onOpenStorageCleaner = viewModel::openCleanerSheet,
                                            onOpenDocumentScanner = { viewModel.setDocumentScannerOpen(true) },
                                            onOpenQrScanner = { viewModel.setQrScannerOpen(true) },
                                            onIndexOcrLibrary = viewModel::indexLibraryForOcr,
                                            onRetryFailedUploads = viewModel::retryFailedOrQueuedUploads,
                                            onClearCompletedUploads = viewModel::clearCompletedUploadQueue,
                                            onRefreshMedia = { viewModel.refreshWorkspaceData() },
                                            onSyncNow = viewModel::syncNow,
                                            onPingConnection = { viewModel.pingServer() },
                                            onDirectPhotoUpload = viewModel::uploadImagesFromPicker,
                                            onUpdateItemTags = viewModel::updateItemTags,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onToggleOfflinePin = viewModel::toggleOfflinePin,
                                            onRecoverMissingFile = viewModel::openMissingFileRecovery,
                                            onRenameItem = viewModel::renameMediaItem,
                                            onMediaClick = viewModel::openMediaPreview,
                                            onToggleSelectionMode = viewModel::toggleSelectionMode,
                                            onToggleItemSelected = viewModel::toggleItemSelected,
                                            onSelectAll = viewModel::selectAllVisible,
                                            onClearSelection = viewModel::clearSelection,
                                            onBulkPinOffline = viewModel::bulkPinSelectedOffline,
                                            onBulkDownloadZip = {
                                                viewModel.exportSelectedAsZip { shareIntent ->
                                                    launchIntentSafely(shareIntent, "No app available to export ZIP archive")
                                                }
                                            },
                                            onBulkShare = {
                                                viewModel.shareSelectedMediaBatch { shareIntent ->
                                                    launchIntentSafely(shareIntent, "No app available to share files")
                                                }
                                            },
                                            onBulkDelete = viewModel::bulkDeleteSelected,
                                            onBulkMoveToFolder = viewModel::bulkMoveSelectedToFolder,
                                            onBulkHideInVault = viewModel::bulkHideSelectedInVault,
                                            onSingleShareLink = viewModel::openShareModalForMedia,
                                            onSingleMoveToFolder = viewModel::moveSingleMediaToFolder,
                                            onSingleSoftDelete = viewModel::softDeleteSingleMedia,
                                            onSingleHideInVault = viewModel::toggleHideInVault
                                        )
                                    }

                                    MainNavTab.FOLDERS -> {
                                        FoldersTabScreen(
                                            myFolders = uiState.myFolders,
                                            sharedFolders = uiState.sharedFolders,
                                            hiddenVaultCount = uiState.hiddenMediaIds.size,
                                            onOpenFolder = viewModel::openFolderInGallery,
                                            onCreateFolder = viewModel::createFolder,
                                            onUpdateFolder = viewModel::updateFolder,
                                            onDeleteFolder = viewModel::deleteFolder,
                                            onManageCollaborators = viewModel::setManagingCollaboratorsFolder,
                                            onOpenPrivateVault = viewModel::openVaultSheet,
                                            onOpenStorageCleaner = viewModel::openCleanerSheet,
                                            onOpenDocumentScanner = { viewModel.setDocumentScannerOpen(true) }
                                        )
                                    }

                                    MainNavTab.LINKS -> {
                                        ShareLinksTabScreen(
                                            myShareLinks = uiState.myShareLinks,
                                            publicShareState = uiState.publicShareViewState,
                                            serverBaseUrl = uiState.serverHealth.baseUrl,
                                            onInspectPublicToken = viewModel::inspectPublicShareToken,
                                            onOpenFullScreenShareLink = { url ->
                                                viewModel.handleIncomingShareLink(url)
                                            },
                                            onResetPublicInspector = viewModel::resetPublicShareExplorer,
                                            onRevokeLink = viewModel::revokeShareLink,
                                            onShareViaAndroidSheet = { url ->
                                                viewModel.shareMediaOrLinkViaAndroidSheet(
                                                    item = null,
                                                    shareUrl = url,
                                                    onLaunchIntent = { launchIntentSafely(it, "No app available to share link") }
                                                )
                                            },
                                            onShareQrCode = { fileName, url ->
                                                viewModel.shareQrCodeForUrl(
                                                    fileName = fileName,
                                                    shareUrl = url,
                                                    onLaunchIntent = { launchIntentSafely(it, "No app available to share QR code") }
                                                )
                                            },
                                            onPreviewUnlockedMedia = viewModel::openMediaPreview,
                                            onOpenQrScanner = { viewModel.setQrScannerOpen(true) }
                                        )
                                    }

                                    MainNavTab.TRASH -> {
                                        TrashTabScreen(
                                            trashItems = uiState.trashMedia,
                                            serverBaseUrl = uiState.serverHealth.baseUrl,
                                            onRestoreItem = viewModel::restoreTrashItem,
                                            onPermanentDeleteItem = viewModel::permanentDeleteTrashItem,
                                            onEmptyTrashAll = viewModel::emptyTrashAll
                                        )
                                    }

                                    MainNavTab.SETTINGS -> {
                                        SettingsTabScreen(
                                            userSession = uiState.userSession,
                                            serverHealth = uiState.serverHealth,
                                            totalUsedBytes = uiState.totalUsedBytes,
                                            usagePercent = uiState.usagePercent,
                                            storageBreakdown = uiState.storageBreakdown,
                                            offlineVaultStats = uiState.offlineVaultStats,
                                            pendingSyncCount = uiState.pendingSyncCount,
                                            isSyncing = uiState.isSyncing,
                                            themeMode = uiState.themeMode,
                                            accentHex = uiState.accentHex,
                                            dynamicAppIconEnabled = uiState.dynamicAppIconEnabled,
                                            hasPendingAppIconChange = uiState.hasPendingAppIconChange,
                                            exifScrubDefaultOn = uiState.exifScrubDefaultOn,
                                            wifiOnlyUploads = uiState.wifiOnlyUploads,
                                            vaultBiometricEnabled = uiState.vaultBiometricEnabled,
                                            hasVaultPin = uiState.hasVaultPin,
                                            hiddenVaultCount = uiState.hiddenMediaIds.size,
                                            isCloudSyncConnected = uiState.cloudSyncUserUid != null,
                                            cloudSyncUserEmail = uiState.cloudSyncUserEmail,
                                            cloudSyncStatusMessage = uiState.cloudSyncStatusMessage,
                                            isCloudSyncingMetadata = uiState.isCloudSyncingMetadata,
                                            onGoogleCloudSignIn = {
                                                FirebaseAuthManager.onGoogleSignInClicked(
                                                    context = context,
                                                    credentialManager = credentialManager,
                                                    scope = coroutineScope,
                                                    onAuthSuccess = { user ->
                                                        viewModel.onFirebaseGoogleSignedIn(user.uid, user.email)
                                                        viewModel.showToastMessage("Google Cloud Metadata Sync connected (${user.email ?: "Google"})")
                                                    },
                                                    onAuthError = { errMsg ->
                                                        viewModel.showToastMessage(errMsg)
                                                    }
                                                )
                                            },
                                            onGoogleCloudSignOut = {
                                                FirebaseAuthManager.signOut(
                                                    credentialManager = credentialManager,
                                                    scope = coroutineScope,
                                                    onBeforeSignOut = { viewModel.cancelCloudSyncListeners() },
                                                    onSignOutComplete = {
                                                        viewModel.onFirebaseGoogleSignedOut()
                                                        viewModel.showToastMessage("Disconnected Google Cloud Metadata Sync")
                                                    }
                                                )
                                            },
                                            onPickAvatar = {
                                                pickAvatarLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            onUpdateProfileAndPassword = viewModel::updateProfileInfo,
                                            onSelectThemeMode = viewModel::setThemeMode,
                                            onSelectAccentColor = viewModel::setAccentColor,
                                            onToggleDynamicAppIcon = viewModel::setDynamicAppIconEnabled,
                                            onToggleExifScrubDefault = viewModel::setExifScrubDefaultOn,
                                            onToggleWifiOnlyUploads = viewModel::setWifiOnlyUploads,
                                            onToggleVaultBiometric = viewModel::setVaultBiometricEnabled,
                                            onResetVaultPin = viewModel::resetVaultPin,
                                            onOpenPrivateVault = viewModel::openVaultSheet,
                                            onOpenStorageCleaner = viewModel::openCleanerSheet,
                                            onSyncNow = viewModel::syncNow,
                                            onToggleAutoCache = viewModel::setAutoCacheEnabled,
                                            onDownloadFavoritesOffline = viewModel::downloadAllFavoritesOffline,
                                            onClearAutoCache = viewModel::clearAutoCacheOffline,
                                            onClearAllOfflineFiles = viewModel::clearAllOfflineVaultFiles,
                                            onOpenServerConfig = { viewModel.setServerConfigDialogOpen(true) },
                                            onRelaunchOnboardingTour = viewModel::startOnboardingTour,
                                            onDangerZoneDeleteAllFiles = viewModel::executeDangerZoneDeleteAllFiles,
                                            onLogout = viewModel::logout
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Interactive Studio Look & Aesthetic Suggestions Dialog ---
    if (isStudioPaletteSheetOpen) {
        AlertDialog(
            onDismissRequest = { isStudioPaletteSheetOpen = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.example.ui.components.GalleryHubLogoMark(
                        size = 42.dp,
                        testTag = "studio_palette_live_logo"
                    )
                    Column {
                        Text(
                            text = "Studio Look Suggestions",
                            fontFamily = PlayfairDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (uiState.dynamicAppIconEnabled) {
                                "Logo & app drawer icon adapt live"
                            } else {
                                "In-app logo adapts live"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose a crafted visual direction and surface tone for your studio:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    AccentPreset.entries.forEach { preset ->
                        val selected = uiState.accentHex.equals(preset.hex, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) preset.color.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) preset.color else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.setAccentColor(preset.hex) }
                                .testTag("studio_suggestion_${preset.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(preset.color)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = preset.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = preset.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { isStudioPaletteSheetOpen = false }) {
                    Text("Done")
                }
            }
        )
    }

    // --- Overlays, Sheets & Studio Modals ---

    if (uiState.isUploadModalOpen) {
        UploadMediaBottomSheet(
            candidates = uiState.uploadCandidates,
            activeFolder = uiState.activeFolder,
            isUploading = uiState.isUploading,
            wifiOnlyUploads = uiState.wifiOnlyUploads,
            onPickPhotos = {
                pickPhotosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickVideos = {
                pickVideosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPickDocuments = {
                pickDocumentsLauncher.launch(arrayOf("*/*"))
            },
            onOpenDocumentScanner = {
                viewModel.setUploadModalOpen(false)
                viewModel.setDocumentScannerOpen(true)
            },
            onToggleWifiOnlyUploads = viewModel::setWifiOnlyUploads,
            onRemoveCandidate = viewModel::removeUploadCandidate,
            onExecuteUpload = { viewModel.executeBatchUpload() },
            onDismiss = { viewModel.setUploadModalOpen(false) }
        )
    }

    if (uiState.isGuestRestrictionDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setGuestRestrictionDialogOpen(false) },
            title = {
                Text(
                    text = "Sign In or Create Account Required",
                    fontFamily = PlayfairDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                )
            },
            text = {
                Text(
                    text = "Guest Mode is for previewing the studio interface only. Please sign in or create an account to upload photos, videos, or files to your personal archive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGuestRestrictionDialogOpen(false)
                        viewModel.logout()
                    },
                    modifier = Modifier.testTag("guest_restriction_sign_in_button")
                ) {
                    Text("Sign In / Sign Up", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setGuestRestrictionDialogOpen(false) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Missing Server File (HTTP 404) Actionable Recovery Dialog ---
    uiState.recoveringMissingMediaItem?.let { missingItem ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMissingFileRecovery,
            title = {
                Text(
                    text = "File Missing on Server",
                    fontFamily = PlayfairDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "\"${missingItem.originalName}\" could not be downloaded from the cloud server (HTTP 404). Free cloud hosts clear temporary disk uploads on restart, while keeping old file records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Re-link the file from your device to restore it offline and re-upload it to the cloud, or remove this broken entry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            pendingRelinkTarget = missingItem
                            viewModel.dismissMissingFileRecovery()
                            when (missingItem.mediaType) {
                                com.example.data.model.MediaType.IMAGE -> relinkVisualMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                                com.example.data.model.MediaType.VIDEO -> relinkVisualMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                                com.example.data.model.MediaType.DOCUMENT -> relinkDocumentLauncher.launch(arrayOf("*/*"))
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("missing_file_relink_button")
                    ) {
                        Text("Re-link from Device", fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.removeMissingMediaItem(missingItem) },
                        shape = RoundedCornerShape(13.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("missing_file_remove_button")
                    ) {
                        Text("Remove Missing Entry", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissMissingFileRecovery,
                    modifier = Modifier.testTag("missing_file_cancel_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    uiState.previewItem?.let { previewItem ->
        val isPreviewHidden = previewItem.id in uiState.hiddenMediaIds
        val previewItemsList = if (isPreviewHidden) {
            uiState.hiddenMediaItems.ifEmpty { listOf(previewItem) }
        } else {
            filteredMedia
        }
        MediaLightboxModal(
            item = previewItem,
            mediaItems = previewItemsList,
            downloadingOfflineIds = uiState.downloadingOfflineIds,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            folders = uiState.myFolders,
            isSlideshowPlaying = uiState.isSlideshowPlaying,
            onDismiss = { viewModel.openMediaPreview(null) },
            onPrev = { viewModel.navigatePreview(-1) },
            onNext = { viewModel.navigatePreview(1) },
            onSelectPageItem = viewModel::selectPreviewItem,
            onToggleSlideshow = viewModel::toggleSlideshow,
            onPauseSlideshow = viewModel::pauseSlideshow,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleOfflinePin = viewModel::toggleOfflinePin,
            onRecoverMissingFile = viewModel::openMissingFileRecovery,
            onRenameItem = viewModel::renameMediaItem,
            onUpdateTags = viewModel::updateItemTags,
            onOpenImageEditor = { viewModel.openImageEditor(it) },
            onMoveToFolder = viewModel::moveSingleMediaToFolder,
            onOpenShareModal = viewModel::openShareModalForMedia,
            onNativeShare = { item ->
                viewModel.shareMediaOrLinkViaAndroidSheet(
                    item = item,
                    shareUrl = null,
                    onLaunchIntent = { launchIntentSafely(it, "No app available to open or share this file") }
                )
            },
            onSoftDelete = viewModel::softDeleteSingleMedia,
            onScanOcrText = viewModel::scanOcrForSingleItem,
            onToggleHideInVault = viewModel::toggleHideInVault,
            isItemHiddenInVault = previewItem.id in uiState.hiddenMediaIds,
            onEnterPipMode = onEnterVideoPipMode,
            onVideoFrameGrabbed = viewModel::saveVideoFrameAsPhoto,
            onTrimmedVideoSaved = viewModel::saveTrimmedVideoClip
        )
    }

    uiState.editingImageItem?.let { editingItem ->
        ImageEditorModal(
            item = editingItem,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            isSaving = uiState.isUploading,
            onDismiss = { viewModel.openImageEditor(null) },
            onSaveEditedBitmap = viewModel::saveEditedImageCopy
        )
    }

    uiState.sharingMediaItem?.let { sharingItem ->
        ShareFileLinkDialog(
            item = sharingItem,
            createdLink = uiState.lastCreatedShareLink,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            exifScrubDefaultOn = uiState.exifScrubDefaultOn,
            onGenerateLink = viewModel::generateShareLink,
            onShareViaAndroidSheet = { url ->
                viewModel.shareMediaOrLinkViaAndroidSheet(
                    item = sharingItem,
                    shareUrl = url,
                    onLaunchIntent = { launchIntentSafely(it, "No app available to share link") }
                )
            },
            onShareFileDirectly = { scrubExif ->
                viewModel.shareMediaOrLinkViaAndroidSheet(
                    item = sharingItem,
                    shareUrl = null,
                    onLaunchIntent = { launchIntentSafely(it, "No app available to share file") },
                    scrubExif = scrubExif
                )
            },
            onShareQrCode = { fileName, url ->
                viewModel.shareQrCodeForUrl(
                    fileName = fileName,
                    shareUrl = url,
                    onLaunchIntent = { launchIntentSafely(it, "No app available to share QR code") }
                )
            },
            onDismiss = { viewModel.openShareModalForMedia(null) }
        )
    }

    uiState.managingCollaboratorsFolder?.let { collabFolder ->
        ShareFolderCollaboratorsDialog(
            folder = collabFolder,
            onInviteCollaborator = { email, perm ->
                viewModel.inviteFolderCollaborator(collabFolder.id, email, perm)
            },
            onRevokeCollaborator = { userId ->
                viewModel.revokeFolderCollaborator(collabFolder.id, userId)
            },
            onDismiss = { viewModel.setManagingCollaboratorsFolder(null) }
        )
    }

    if (uiState.isActivityDrawerOpen) {
        ActivityAuditFeedSheet(
            activities = uiState.activities,
            onClearAll = viewModel::clearActivityHistory,
            onDismiss = { viewModel.setActivityDrawerOpen(false) }
        )
    }

    if (uiState.isChatbotOpen) {
        AiAssistantChatbotSheet(
            messages = uiState.chatMessages,
            isSending = uiState.isChatSending,
            onSendPrompt = viewModel::sendChatMessage,
            onDismiss = { viewModel.setChatbotOpen(false) }
        )
    }

    if (uiState.isServerConfigDialogOpen) {
        ServerEndpointConfigDialog(
            serverHealth = uiState.serverHealth,
            onTestAndSaveUrl = { url -> viewModel.pingServer(url) },
            onDismiss = { viewModel.setServerConfigDialogOpen(false) }
        )
    }

    // --- Pro Suite Modals: Biometric Vault, Cleaner, Document Scanner, QR Scanner & OCR Dialog ---
    if (uiState.isVaultSheetOpen) {
        BiometricVaultModal(
            hiddenItems = uiState.hiddenMediaItems,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            isUnlocked = uiState.isVaultUnlocked,
            hasPinConfigured = uiState.hasVaultPin,
            biometricEnabled = uiState.vaultBiometricEnabled,
            onUnlockSuccess = viewModel::unlockVault,
            onSetupPin = viewModel::setupVaultPin,
            onVerifyPin = viewModel::verifyVaultPin,
            onLockNow = viewModel::lockVault,
            onUnhideItem = viewModel::toggleHideInVault,
            onPreviewItem = { item ->
                viewModel.dismissVaultSheet()
                viewModel.openMediaPreview(item)
            },
            onDismiss = viewModel::dismissVaultSheet
        )
    }

    if (uiState.isCleanerSheetOpen) {
        StorageCleanerModal(
            report = uiState.storageAuditReport,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            onTrashItems = viewModel::trashItemsFromCleaner,
            onUnpinOfflineItems = viewModel::unpinOfflineItemsFromCleaner,
            onPreviewItem = { item ->
                viewModel.dismissCleanerSheet()
                viewModel.openMediaPreview(item)
            },
            onDismiss = viewModel::dismissCleanerSheet
        )
    }

    if (uiState.isDocumentScannerOpen) {
        DocumentScannerModal(
            targetFolderName = uiState.activeFolder?.name,
            onPdfCreatedAndUpload = viewModel::uploadScannedPdf,
            onDismiss = { viewModel.setDocumentScannerOpen(false) }
        )
    }

    if (uiState.isQrScannerOpen) {
        QrCodeScannerModal(
            onQrCodeDetected = { raw ->
                viewModel.setQrScannerOpen(false)
                viewModel.handleIncomingShareLink(raw)
            },
            onDismiss = { viewModel.setQrScannerOpen(false) }
        )
    }

    uiState.ocrExtractedDialogContent?.let { (fileName, extractedText) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOcrDialog,
            title = {
                Text(
                    text = "Extracted Text · $fileName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = extractedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                        viewModel.showToastMessage("Copied extracted text to clipboard")
                        viewModel.dismissOcrDialog()
                    },
                    modifier = Modifier.testTag("ocr_copy_text_button")
                ) {
                    Text("Copy Text")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOcrDialog) {
                    Text("Close")
                }
            }
        )
    }

    // Full-Screen Deep-Link Public Share Viewer (renders above AuthScreen & workspace, works even when signed out!)
    uiState.activeDeepLinkUrl?.let { activeLink ->
        com.example.ui.components.PublicShareDeepLinkModal(
            incomingLinkSource = activeLink,
            shareState = uiState.deepLinkShareState,
            serverBaseUrl = uiState.serverHealth.baseUrl,
            isSavingOffline = uiState.isSavingDeepLinkOffline,
            isSavedOffline = uiState.isDeepLinkSavedOffline,
            onSubmitPassword = { tokenOrUrl, password ->
                viewModel.handleIncomingShareLink(tokenOrUrl, password)
            },
            onRetry = { tokenOrUrl ->
                viewModel.handleIncomingShareLink(tokenOrUrl, null)
            },
            onSaveOffline = viewModel::saveDeepLinkMediaOffline,
            onExportSharedMedia = { item, fallbackUrl ->
                viewModel.exportDeepLinkMedia(
                    item = item,
                    fallbackUrl = fallbackUrl,
                    onLaunchIntent = { launchIntentSafely(it, "No app available to export this file") }
                )
            },
            onDismiss = viewModel::dismissDeepLinkViewer
        )
    }

    // Ensure Snackbars (e.g. Offline Save confirmation inside Deep-Link Viewer when signed out) are always visible
    if (uiState.userSession == null || uiState.activeDeepLinkUrl != null) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (uiState.activeDeepLinkUrl != null) 76.dp else 16.dp)
        )
    }
    } // End Root Box
}

/**
 * Modular Glassmorphic Floating Pill Dock + Detached Circular Upload Pod
 */
@Composable
fun ModularStudioBottomDock(
    currentTab: MainNavTab,
    trashCount: Int,
    isUploading: Boolean,
    isGuestMode: Boolean,
    onSelectTab: (MainNavTab) -> Unit,
    onUploadClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val frostedGlassBase = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    val specularHighlight = Color.White.copy(alpha = 0.24f)
    val dockBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("modular_bottom_dock")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Module: Glassmorphic Floating Translucent Pill Dock (5 Studio Destinations)
            Surface(
                shape = RoundedCornerShape(50),
                color = frostedGlassBase,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                specularHighlight,
                                primaryColor.copy(alpha = 0.42f),
                                dockBorderColor,
                                specularHighlight.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.09f),
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.05f)
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            val pillBgColor by animateColorAsState(
                                targetValue = if (isSelected) primaryColor else Color.Transparent,
                                animationSpec = tween(220),
                                label = "tab_pill_bg"
                            )
                            val pillScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.0f else 0.95f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "tab_pill_scale"
                            )
                            val icon = when (tab) {
                                MainNavTab.GALLERY -> Icons.Default.PhotoLibrary
                                MainNavTab.FOLDERS -> Icons.Default.Folder
                                MainNavTab.LINKS -> Icons.Default.Link
                                MainNavTab.TRASH -> Icons.Default.DeleteOutline
                                MainNavTab.SETTINGS -> Icons.Default.Settings
                            }
                            val shortLabel = when (tab) {
                                MainNavTab.LINKS -> "Links"
                                else -> tab.label
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = pillBgColor,
                                modifier = Modifier
                                    .height(46.dp)
                                    .graphicsLayer(
                                        scaleX = pillScale,
                                        scaleY = pillScale
                                    )
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onSelectTab(tab)
                                    }
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .testTag("nav_tab_${tab.key}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(
                                        horizontal = if (isSelected) 14.dp else 11.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    if (tab == MainNavTab.TRASH && trashCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = if (isSelected) onPrimaryColor else primaryColor,
                                                    contentColor = if (isSelected) primaryColor else onPrimaryColor
                                                ) {
                                                    Text("$trashCount")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = tab.label,
                                                tint = if (isSelected) onPrimaryColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = tab.label,
                                            tint = if (isSelected) onPrimaryColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = onPrimaryColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Module: Glassmorphic Detached Circular Upload Pod
            Surface(
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.94f),
                tonalElevation = 10.dp,
                shadowElevation = 18.dp,
                modifier = Modifier
                    .size(62.dp)
                    .border(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                primaryColor,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onUploadClick()
                    }
                    .testTag("gallery_photo_picker_fab")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .testTag("fab_upload_media")
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            color = onPrimaryColor,
                            strokeWidth = 2.2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isGuestMode) Icons.Default.Lock else Icons.Default.Add,
                            contentDescription = "Upload Media",
                            tint = onPrimaryColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
