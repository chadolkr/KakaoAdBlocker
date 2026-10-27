package com.example.kakaoadblocker.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.kakaoadblocker.MainActivity
import com.example.kakaoadblocker.R
import java.lang.ref.WeakReference

/**
 * Drives the KakaoTalk UI to block the channel that sent an ad and/or leave its chat room
 * (which deletes the ad messages). KakaoTalk has no API for this, so the service finds buttons by their
 * labels. If KakaoTalk changes those labels, update [Labels].
 *
 * Flow: open chat → verify it is a channel chat → block via the channel notice → leave via the menu.
 * Labels were taken from KakaoTalk 26.9.1 (Android 17); chat titles and previews live in content descriptions.
 *
 * Scan mode (requested from the app) cleans up ad chats received before the feature was enabled:
 * open the chat list → find a row whose preview is an ad → run the flow above → back to the list top → repeat.
 */
class KakaoCleanupAccessibilityService : AccessibilityService() {

    /** UI labels (text or content description) of the KakaoTalk elements this service clicks. */
    private object Labels {
        val MENU = listOf("메뉴 더보기", "메뉴", "Menu")
        // Bottom navigation tab of the chat list ("채팅 탭 3개의 새로운 업데이트"); only visible outside chat rooms
        val CHAT_TAB = listOf("채팅 탭", "Chats tab")
        // Only channel chat rooms show these; used to avoid touching friends' chats
        val CHANNEL_HINTS = listOf("채널을 차단", "채널로 이동", "채널 메뉴", "인증된 채널")
        // Notice at the top of a channel chat: "...수신을 원하지 않을 경우 여기를 눌러 채널을 차단해주세요."
        val BLOCK_NOTICE = listOf("채널을 차단")
        val BLOCK_DIALOG_TITLE = listOf("채널 차단")
        val CONFIRM_BLOCK = listOf("차단", "Block")
        val LEAVE = listOf("채팅방 나가기", "Leave chat")
        val CONFIRM_LEAVE = listOf("나가기", "Leave")
    }

    private enum class Match { EXACT, PREFIX, CONTAINS }

    private enum class Step(val timeoutMs: Long) {
        SCAN_OPEN_LIST(8_000),
        SCAN(6_000),
        OPEN_CHAT(8_000),
        VERIFY_CHANNEL(3_000),
        BLOCK(3_000),
        CONFIRM_BLOCK(3_000),
        LEAVE(4_000),
        CONFIRM_LEAVE(3_000)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: BlockLogRepository

    private var task: CleanupTask? = null
    private var step = Step.OPEN_CHAT
    private var stepStartedAt = 0L
    private var lastActionAt = 0L
    private var lastClickedBounds: Rect? = null
    private var blockFailed = false
    private var blockScrolls = 0
    private var autoBlock = false
    private var autoLeave = false

    private var scanning = false
    private var scanPending = false
    private var scanAtTop = false
    private var scanScrolls = 0
    private var scanCleaned = 0
    // Chats that must not be picked again in this scan (not a channel, failed, or kept after block-only)
    private val scanSkipped = mutableSetOf<String>()

    private val isBusy get() = task != null || scanning

    private val tick = object : Runnable {
        override fun run() {
            if (!isBusy) return
            advance()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            startNextIfIdle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = SharedPreferencesBlockLogRepository(applicationContext)
        // ACTION_USER_PRESENT is a protected broadcast that only the system can send
        ContextCompat.registerReceiver(
            this, userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED
        )
        CleanupQueue.onTaskAdded = { handler.post { startNextIfIdle() } }
        instance = WeakReference(this)
        isRunning = true
        startNextIfIdle()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (!isRunning) return
        isRunning = false
        instance = null
        scanning = false
        scanPending = false
        CleanupQueue.onTaskAdded = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(userPresentReceiver) }
        // Nobody will process these anymore
        task?.let { repository.updateCleanupStatus(it.logId, CleanupStatus.FAILED) }
        task = null
        CleanupQueue.drain().forEach { repository.updateCleanupStatus(it.logId, CleanupStatus.FAILED) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isBusy) advance()
    }

    override fun onInterrupt() {}

    private fun startNextIfIdle() {
        if (isBusy || !isRunning) return
        // Don't take over the screen while the device is locked; wait for ACTION_USER_PRESENT
        if (isDeviceLocked()) return

        val next = CleanupQueue.poll()
        if (next == null) {
            if (scanPending) startScan()
            return
        }
        loadSettings()
        if (!autoBlock && !autoLeave) {
            repository.updateCleanupStatus(next.logId, CleanupStatus.NONE)
            startNextIfIdle()
            return
        }

        Log.d(TAG, "Cleaning up chat '${next.chatTitle}' (block=$autoBlock, leave=$autoLeave)")
        task = next
        blockFailed = false
        lastClickedBounds = null
        enterStep(Step.OPEN_CHAT)
        if (!openChat(next)) {
            finish(CleanupStatus.FAILED)
            return
        }
        handler.postDelayed(tick, TICK_MS)
    }

    private fun isDeviceLocked(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        return keyguard.isKeyguardLocked || !power.isInteractive
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("block_logs_pref", Context.MODE_PRIVATE)
        autoBlock = prefs.getBoolean("auto_block_channel", false)
        autoLeave = prefs.getBoolean("auto_leave_chat", false)
    }

    private fun startScan() {
        scanPending = false
        loadSettings()
        if (!autoBlock && !autoLeave) return

        Log.d(TAG, "Scanning chat list (block=$autoBlock, leave=$autoLeave)")
        scanning = true
        scanCleaned = 0
        scanSkipped.clear()
        if (!launchKakao()) {
            endScan()
            return
        }
        markAction()
        enterStep(Step.SCAN_OPEN_LIST)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun endScan() {
        if (!scanning) return
        Log.d(TAG, "Scan finished: $scanCleaned chats cleaned up")
        scanning = false
        handler.removeCallbacks(tick)
        Toast.makeText(this, getString(R.string.scan_finished_format, scanCleaned), Toast.LENGTH_LONG).show()
        // Bring the user back to the app to see the results
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        handler.postDelayed({ startNextIfIdle() }, NEXT_TASK_DELAY_MS)
    }

    private fun launchKakao(): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(KakaoAdBlockerService.KAKAO_PACKAGE) ?: return false
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun openChat(task: CleanupTask): Boolean {
        val pendingIntent = task.contentIntent
        if (pendingIntent != null) {
            try {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
                return true
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "Notification intent canceled, falling back to the chat list", e)
            }
        }
        // Fallback: open KakaoTalk and pick the chat room from the list in OPEN_CHAT
        return launchKakao()
    }

    private fun advance() {
        val now = SystemClock.uptimeMillis()
        // Let the UI settle after each click
        if (now - lastActionAt < SETTLE_MS) return

        if (now - stepStartedAt > step.timeoutMs) {
            onTimeout()
            return
        }

        val roots = kakaoRoots()
        if (step == Step.SCAN_OPEN_LIST || step == Step.SCAN) {
            advanceScan(roots)
            return
        }
        val current = task ?: return
        if (roots.isEmpty()) return

        when (step) {
            Step.SCAN_OPEN_LIST, Step.SCAN -> Unit
            Step.OPEN_CHAT -> {
                val onChatList = roots.any { it.findByLabel(Labels.CHAT_TAB, Match.PREFIX) != null }
                if (!onChatList &&
                    roots.any { it.findByTitle(current.chatTitle) != null } &&
                    roots.any { it.findByLabel(Labels.MENU) != null }
                ) {
                    blockScrolls = 0
                    enterStep(Step.VERIFY_CHANNEL)
                } else if (onChatList) {
                    // Fallback path: open the chat room from the list by its title
                    roots.firstNotNullOfOrNull { it.findByTitle(current.chatTitle) }?.let { click(it) }
                }
            }
            Step.VERIFY_CHANNEL -> {
                // Ignore the chat title itself so a friend named e.g. "채널 메뉴" doesn't pass
                val channelHint = roots.firstNotNullOfOrNull { root ->
                    root.findByLabel(Labels.CHANNEL_HINTS, Match.CONTAINS, excluding = current.chatTitle)
                }
                if (channelHint != null) {
                    enterStep(if (autoBlock) Step.BLOCK else Step.LEAVE)
                }
            }
            Step.BLOCK -> {
                val notice = roots.firstNotNullOfOrNull { it.findByLabel(Labels.BLOCK_NOTICE, Match.CONTAINS) }
                if (notice != null) {
                    click(notice)
                    enterStep(Step.CONFIRM_BLOCK)
                } else if (blockScrolls < MAX_CHAT_SCROLLS && scrollChatLogUp(roots)) {
                    // The notice sits above the first message; scroll up to reveal it
                    blockScrolls++
                    enterStep(Step.BLOCK)
                }
            }
            Step.CONFIRM_BLOCK -> {
                val inDialog = roots.any { it.findByLabel(Labels.BLOCK_DIALOG_TITLE) != null }
                val confirm = roots.firstNotNullOfOrNull {
                    it.findByLabel(Labels.CONFIRM_BLOCK, skipBounds = lastClickedBounds)
                }
                if (inDialog && confirm != null) {
                    click(confirm)
                    afterBlock()
                }
            }
            Step.LEAVE -> {
                val leave = roots.firstNotNullOfOrNull { it.findByLabel(Labels.LEAVE) }
                if (leave != null) {
                    click(leave)
                    enterStep(Step.CONFIRM_LEAVE)
                } else {
                    roots.firstNotNullOfOrNull { it.findByLabel(Labels.MENU) }?.let { click(it) }
                }
            }
            Step.CONFIRM_LEAVE -> {
                roots.firstNotNullOfOrNull { it.findByLabel(Labels.CONFIRM_LEAVE, skipBounds = lastClickedBounds) }?.let {
                    click(it)
                    finish(if (blockFailed) CleanupStatus.PARTIAL else CleanupStatus.DONE)
                }
            }
        }
    }

    private fun advanceScan(roots: List<AccessibilityNodeInfo>) {
        if (isDeviceLocked()) {
            endScan()
            return
        }
        when (step) {
            Step.SCAN_OPEN_LIST -> {
                if (roots.isEmpty()) {
                    // KakaoTalk went to the background (e.g. BACK on the chat list); bring it back
                    launchKakao()
                    markAction()
                    return
                }
                val chatTab = roots.firstNotNullOfOrNull { it.findByLabel(Labels.CHAT_TAB, Match.PREFIX) }
                if (chatTab != null) {
                    click(chatTab)
                    scanAtTop = false
                    scanScrolls = 0
                    enterStep(Step.SCAN)
                } else {
                    // Still inside a chat room or its drawer
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    markAction()
                }
            }
            Step.SCAN -> {
                val list = roots.firstNotNullOfOrNull { it.findChatList() }
                // Rows shift after leaving a chat, so always restart from the top
                if (!scanAtTop) {
                    if (scanScrolls < MAX_SCAN_SCROLLS &&
                        list?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
                    ) {
                        scanScrolls++
                        markAction()
                        enterStep(Step.SCAN)
                        return
                    }
                    scanAtTop = true
                    scanScrolls = 0
                }
                val hit = roots.firstNotNullOfOrNull { findAdRow(it) }
                if (hit != null) {
                    startScanTask(hit)
                    return
                }
                if (scanScrolls < MAX_SCAN_SCROLLS &&
                    list?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                ) {
                    scanScrolls++
                    markAction()
                    enterStep(Step.SCAN)
                    return
                }
                endScan()
            }
            else -> Unit
        }
    }

    private class AdRow(val row: AccessibilityNodeInfo, val title: String, val preview: String)

    /** Finds a visible chat list row whose last message preview is an ad. */
    private fun findAdRow(root: AccessibilityNodeInfo): AdRow? {
        // The preview is exposed as a content description; the row itself only has a combined description
        val previews = root.findAll { node ->
            node.isVisibleToUser && !node.isClickable && (AdDetector.isAd(node.text) || AdDetector.isAd(node.contentDescription))
        }
        for (previewNode in previews) {
            val preview = (previewNode.contentDescription ?: previewNode.text).toString()
            var row: AccessibilityNodeInfo? = previewNode
            while (row != null && !row.isClickable) row = row.parent
            if (row == null) continue
            val title = row.find { it.viewIdResourceName?.endsWith(":id/name") == true }?.text?.toString()?.trim()
                ?: ChatListParser.findTitle(row.collectTexts(), preview)
                ?: continue
            if (title in scanSkipped) continue
            return AdRow(row, title, preview)
        }
        return null
    }

    private fun startScanTask(hit: AdRow) {
        if (scanCleaned + scanSkipped.size >= MAX_SCAN_TASKS) {
            endScan()
            return
        }
        Log.d(TAG, "Found ad chat '${hit.title}' in the chat list")
        val logId = repository.addLog(hit.title, hit.preview, CleanupStatus.PENDING)
        task = CleanupTask(hit.title, logId, null)
        blockFailed = false
        lastClickedBounds = null
        click(hit.row)
        enterStep(Step.OPEN_CHAT)
    }

    private fun onTimeout() {
        Log.w(TAG, "Step $step timed out")
        when (step) {
            Step.SCAN_OPEN_LIST, Step.SCAN -> endScan()
            Step.VERIFY_CHANNEL -> {
                // Not a channel chat: leave it alone
                performGlobalAction(GLOBAL_ACTION_BACK)
                finish(CleanupStatus.NOT_CHANNEL)
            }
            Step.BLOCK, Step.CONFIRM_BLOCK -> {
                blockFailed = true
                if (step == Step.CONFIRM_BLOCK) performGlobalAction(GLOBAL_ACTION_BACK)
                afterBlock()
            }
            else -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                finish(CleanupStatus.FAILED)
            }
        }
    }

    private fun afterBlock() {
        if (autoLeave) {
            enterStep(Step.LEAVE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            finish(if (blockFailed) CleanupStatus.FAILED else CleanupStatus.DONE)
        }
    }

    private fun markAction() {
        lastActionAt = SystemClock.uptimeMillis()
    }

    private fun enterStep(next: Step) {
        step = next
        stepStartedAt = SystemClock.uptimeMillis()
    }

    private fun finish(status: CleanupStatus) {
        val finished = task ?: return
        Log.d(TAG, "Cleanup of '${finished.chatTitle}' finished: $status")
        repository.updateCleanupStatus(finished.logId, status)
        task = null
        if (scanning) {
            val cleaned = status == CleanupStatus.DONE || status == CleanupStatus.PARTIAL
            if (cleaned) scanCleaned++
            // A block-only cleanup keeps the chat (and its ad preview) in the list
            if (!cleaned || !autoLeave) scanSkipped.add(finished.chatTitle)
            markAction()
            enterStep(Step.SCAN_OPEN_LIST)
            return
        }
        handler.removeCallbacks(tick)
        if (status == CleanupStatus.DONE || status == CleanupStatus.PARTIAL) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        handler.postDelayed({ startNextIfIdle() }, NEXT_TASK_DELAY_MS)
    }

    /** Roots of all KakaoTalk windows, top-most (e.g. dialogs) first. Never touch other apps. */
    private fun kakaoRoots(): List<AccessibilityNodeInfo> {
        val roots = windows.mapNotNull { it.root }.toMutableList()
        rootInActiveWindow?.let { active -> if (roots.none { it == active }) roots.add(0, active) }
        return roots.filter { it.packageName == KakaoAdBlockerService.KAKAO_PACKAGE }
    }

    private fun scrollChatLogUp(roots: List<AccessibilityNodeInfo>): Boolean {
        val chatLog = roots.firstNotNullOfOrNull { root ->
            root.find { it.isScrollable && it.viewIdResourceName?.endsWith(":id/chat_log_recycler_list") == true }
                ?: root.findChatList()
        } ?: return false
        markAction()
        return chatLog.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun click(node: AccessibilityNodeInfo) {
        markAction()
        lastClickedBounds = Rect().also { node.getBoundsInScreen(it) }

        // Labels are often plain text inside a clickable row; only climb a few levels so we don't hit a container
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < MAX_CLICK_PARENT_DEPTH) {
            target = target.parent
            depth++
        }
        if (target?.isClickable == true && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        // Some custom views ignore ACTION_CLICK; tap the center of the label instead
        val bounds = lastClickedBounds ?: return
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(),
            null,
            null
        )
    }

    /** The chat room title is a content description in the chat room and a text in the chat list. */
    private fun AccessibilityNodeInfo.findByTitle(title: String): AccessibilityNodeInfo? = find { node ->
        node.isVisibleToUser &&
            (node.text?.toString()?.trim() == title || node.contentDescription?.toString()?.trim() == title)
    }

    private fun AccessibilityNodeInfo.findByLabel(
        labels: List<String>,
        match: Match = Match.EXACT,
        skipBounds: Rect? = null,
        excluding: String? = null
    ): AccessibilityNodeInfo? = find { node ->
        if (!node.isVisibleToUser) return@find false
        if (skipBounds != null && Rect().also { node.getBoundsInScreen(it) } == skipBounds) return@find false
        val nodeLabels = listOfNotNull(node.text?.toString()?.trim(), node.contentDescription?.toString()?.trim())
            .filter { excluding == null || !it.contains(excluding) }
        nodeLabels.any { label ->
            labels.any {
                when (match) {
                    Match.EXACT -> label.equals(it, ignoreCase = true)
                    Match.PREFIX -> label.startsWith(it, ignoreCase = true)
                    Match.CONTAINS -> label.contains(it, ignoreCase = true)
                }
            }
        }
    }

    /** Breadth-first search so that the shallowest match (usually the real button) wins. */
    private fun AccessibilityNodeInfo.find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
        findAll(limit = 1, predicate).firstOrNull()

    private fun AccessibilityNodeInfo.findAll(
        limit: Int = Int.MAX_VALUE,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(this@findAll) }
        while (queue.isNotEmpty() && matches.size < limit) {
            val node = queue.removeFirst()
            if (predicate(node)) matches.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return matches
    }

    /** Texts of this node and its descendants in layout (depth-first) order. */
    private fun AccessibilityNodeInfo.collectTexts(): List<String> {
        val texts = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo) {
            node.text?.toString()?.let { texts.add(it) }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it) }
        }
        visit(this)
        return texts
    }

    /** The vertical chat list. Tab pagers are also scrollable, so only list-like views qualify. */
    private fun AccessibilityNodeInfo.findChatList(): AccessibilityNodeInfo? = find { node ->
        val className = node.className?.toString() ?: ""
        node.isScrollable && node.isVisibleToUser &&
            (className.contains("RecyclerView") || className.contains("ListView"))
    }

    companion object {
        private const val TAG = "KakaoCleanup"
        private const val TICK_MS = 400L
        private const val SETTLE_MS = 700L
        private const val NEXT_TASK_DELAY_MS = 1_500L
        private const val MAX_SCAN_SCROLLS = 50
        private const val MAX_SCAN_TASKS = 100
        private const val MAX_CHAT_SCROLLS = 30
        private const val MAX_CLICK_PARENT_DEPTH = 3

        @Volatile
        var isRunning = false
            private set

        @Volatile
        private var instance: WeakReference<KakaoCleanupAccessibilityService>? = null

        /**
         * Starts cleaning up existing ad chats from the chat list, after any queued notification tasks.
         * Returns false if the accessibility service is not running.
         */
        fun requestScan(): Boolean {
            val service = instance?.get() ?: return false
            service.handler.post {
                service.scanPending = true
                service.startNextIfIdle()
            }
            return true
        }
    }
}
