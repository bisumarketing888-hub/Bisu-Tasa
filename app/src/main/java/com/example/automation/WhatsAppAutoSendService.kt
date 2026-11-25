package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.text.Normalizer

/**
 * AccessibilityService strictly scoped to WhatsApp Business (com.whatsapp.w4b) and WhatsApp
 * (com.whatsapp). Remains completely dormant unless [AutoSendOrchestrator.isRunning] is active.
 *
 * Key Xiaomi HyperOS & WhatsApp Business 2025/2026 reliability guarantees:
 * 1) Inspects `windows` (not only `rootInActiveWindow`) so the WhatsApp Business window is always
 *    found even when the soft keyboard (IME) currently holds input focus after typing a search.
 * 2) Uses real physical-equivalent touch gestures (`dispatchGesture`) to tap the group row and
 *    the green Next / Send buttons, bypassing RecyclerView/TouchDelegate views that ignore virtual clicks.
 * 3) Measures the exact bottom Y of the search bar instead of a fixed screen percentage, and
 *    detects the green Send/Next FAB even when pushed up above an open keyboard.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!AutoSendOrchestrator.isRunning()) return
            val waRoot = findWhatsAppWindowRoot()
            if (waRoot != null) {
                processActiveWindow(waRoot)
            }
            if (AutoSendOrchestrator.isRunning()) {
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        stopActivePollingLoop()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onInterrupt() {
        stopActivePollingLoop()
    }

    fun startActivePollingLoop() {
        mainHandler.removeCallbacks(pollingRunnable)
        mainHandler.postDelayed(pollingRunnable, 450L)
    }

    fun stopActivePollingLoop() {
        mainHandler.removeCallbacks(pollingRunnable)
    }

    fun triggerActiveWindowScan() {
        mainHandler.post {
            val waRoot = findWhatsAppWindowRoot() ?: return@post
            processActiveWindow(waRoot)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutoSendOrchestrator.isRunning()) return
        val pkg = event?.packageName?.toString()
        if (pkg != null && pkg != "com.whatsapp.w4b" && pkg != "com.whatsapp") {
            // Even if IME fired an event, check WhatsApp window underneath during search
            if (!AutoSendOrchestrator.searchTextTypedInCurrentPhase) return
        }

        val waRoot = findWhatsAppWindowRoot() ?: return
        processActiveWindow(waRoot)
    }

    /**
     * Finds the WhatsApp Business / WhatsApp application window root even if the soft keyboard
     * (IME) is currently the focused window.
     */
    private fun findWhatsAppWindowRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        val activePkg = active?.packageName?.toString().orEmpty()
        if (active != null && (activePkg == "com.whatsapp.w4b" || activePkg == "com.whatsapp")) {
            return active
        }
        val allWindows = runCatching { windows }.getOrNull().orEmpty()
        for (win in allWindows) {
            if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = win.root ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg == "com.whatsapp.w4b" || pkg == "com.whatsapp") {
                    return root
                }
            }
        }
        return null
    }

    private fun processActiveWindow(root: AccessibilityNodeInfo) {
        val job = AutoSendOrchestrator.currentJob ?: return
        val phase = AutoSendOrchestrator.status.value.phase
        val now = System.currentTimeMillis()
        if (now - AutoSendOrchestrator.lastActionTimestampMs < MIN_ACTION_INTERVAL_MS) return

        val pkg = root.packageName?.toString() ?: job.targetPackage

        when (phase) {
            AutoSendPhase.STEP1_SELECTING_GROUP -> {
                handleGroupSelectionScreen(
                    root = root,
                    pkg = pkg,
                    groupName = job.targetGroupName,
                    isStep1 = true,
                )
            }

            AutoSendPhase.STEP1_CONFIRMING_MEDIA -> {
                handleMediaPreviewSend(root = root, pkg = pkg, caption = job.imageCaption)
            }

            AutoSendPhase.STEP2_SENDING_DIFF_IN_CHAT -> {
                handleDirectChatDifferentialSend(
                    root = root,
                    pkg = pkg,
                    diffMessage = job.differentialMessage,
                )
            }

            AutoSendPhase.STEP2_SELECTING_GROUP_FALLBACK -> {
                handleGroupSelectionScreen(
                    root = root,
                    pkg = pkg,
                    groupName = job.targetGroupName,
                    isStep1 = false,
                )
            }

            AutoSendPhase.STEP2_CONFIRMING_TEXT_FALLBACK -> {
                handleDirectChatDifferentialSend(
                    root = root,
                    pkg = pkg,
                    diffMessage = job.differentialMessage,
                )
            }

            AutoSendPhase.STEP1_SENT_WAITING_STEP2,
            AutoSendPhase.IDLE -> Unit
        }
    }

    private fun handleGroupSelectionScreen(
        root: AccessibilityNodeInfo,
        pkg: String,
        groupName: String,
        isStep1: Boolean,
    ) {
        // 1. Check if WhatsApp Business ALREADY moved to Media Preview or Chat screen
        if (isStep1 && isMediaPreviewScreen(root, pkg)) {
            AutoSendOrchestrator.onStep1GroupConfirmed()
            return
        }
        if (!isStep1 && findChatEntryInput(root, pkg) != null) {
            AutoSendOrchestrator.onStep2GroupConfirmedFallback()
            return
        }

        val now = System.currentTimeMillis()

        // 2. If we already tapped the group row, look for the green Next / Send FAB right away
        if (AutoSendOrchestrator.groupRowClickedInCurrentPhase) {
            val confirmFab = findSendOrNextButton(root, pkg, allowKeyboardAwareFabFallback = true)
            if (confirmFab != null) {
                AutoSendOrchestrator.updateStatusText("Grupo seleccionado ✓ · Confirmando envío...")
                if (tapNodeWithGestureOrClick(confirmFab)) {
                    if (isStep1) {
                        AutoSendOrchestrator.onStep1GroupConfirmed()
                    } else {
                        AutoSendOrchestrator.onStep2GroupConfirmedFallback()
                    }
                    return
                }
            }

            // If 1.6s passed since tapping the row and no FAB appeared yet, allow a clean retry
            if (now - AutoSendOrchestrator.lastRowTapTimeMs > 1_600L &&
                AutoSendOrchestrator.groupRowTapAttempts < 4
            ) {
                AutoSendOrchestrator.groupRowClickedInCurrentPhase = false
            } else {
                return
            }
        }

        // 3. Measure exact search bar bottom so we NEVER tap inside the search box or top bar
        val searchEditText = findSearchEditText(root, pkg)
        val searchBarBottomY = if (searchEditText != null) {
            val r = Rect()
            searchEditText.getBoundsInScreen(r)
            (r.bottom + 8).coerceAtLeast(140)
        } else {
            val screen = Rect()
            root.getBoundsInScreen(screen)
            (screen.height() * 0.11f).toInt().coerceAtLeast(140)
        }

        // 4. Find the matching group row strictly BELOW the search bar
        val matchingRow = findSelectableGroupRow(
            root = root,
            pkg = pkg,
            groupName = groupName,
            minTopY = searchBarBottomY,
            allowSingleResultFallback = AutoSendOrchestrator.searchTextTypedInCurrentPhase,
        )
        if (matchingRow != null) {
            AutoSendOrchestrator.updateStatusText("Tocando grupo \"$groupName\"...")
            if (tapNodeWithGestureOrClick(matchingRow)) {
                AutoSendOrchestrator.groupRowClickedInCurrentPhase = true
                AutoSendOrchestrator.groupRowTapAttempts += 1
                AutoSendOrchestrator.lastRowTapTimeMs = now
                AutoSendOrchestrator.lastActionTimestampMs = now
                return
            }
        }

        // 5. If group isn't visible yet, type the group name into the Search bar
        if (searchEditText != null) {
            if (!AutoSendOrchestrator.searchTextTypedInCurrentPhase) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        groupName,
                    )
                }
                if (searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    AutoSendOrchestrator.searchTextTypedInCurrentPhase = true
                    AutoSendOrchestrator.lastActionTimestampMs = now
                    AutoSendOrchestrator.updateStatusText("Buscando \"$groupName\" en WhatsApp Business...")
                }
            }
            return
        }

        if (!AutoSendOrchestrator.searchTriggeredInCurrentPhase) {
            val searchButton = findSearchButton(root, pkg)
            if (searchButton != null && tapNodeWithGestureOrClick(searchButton)) {
                AutoSendOrchestrator.searchTriggeredInCurrentPhase = true
                AutoSendOrchestrator.lastActionTimestampMs = now
            }
        }
    }

    private fun handleMediaPreviewSend(
        root: AccessibilityNodeInfo,
        pkg: String,
        caption: String,
    ) {
        // Wait until ContactPicker has actually transitioned to the Media Preview screen
        val stillOnContactPicker =
            findSearchEditText(root, pkg) != null ||
                root.findAccessibilityNodeInfosByViewId("$pkg:id/contactpicker_row_name")
                    ?.isNotEmpty() == true
        if (stillOnContactPicker && !isMediaPreviewScreen(root, pkg)) {
            return
        }

        // Wait a short beat after entering Media Preview so WhatsApp finishes loading image & caption
        if (System.currentTimeMillis() - AutoSendOrchestrator.lastActionTimestampMs < 950L) {
            return
        }

        // Ensure caption is populated if WhatsApp didn't pre-fill EXTRA_TEXT
        val captionNode = findCaptionInput(root, pkg)
        if (captionNode != null) {
            val existingText = captionNode.text?.toString().orEmpty().trim()
            val looksPlaceholder = existingText.isBlank() ||
                existingText.contains("comentario", ignoreCase = true) ||
                existingText.contains("caption", ignoreCase = true)
            if (looksPlaceholder) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        caption,
                    )
                }
                captionNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                AutoSendOrchestrator.lastActionTimestampMs = System.currentTimeMillis()
                return
            }
        }

        val sendButton = findSendOrNextButton(root, pkg, allowKeyboardAwareFabFallback = true)
        if (sendButton != null) {
            AutoSendOrchestrator.updateStatusText("Enviando imagen y diferencial...")
            if (tapNodeWithGestureOrClick(sendButton)) {
                AutoSendOrchestrator.onStep1ImageSent()
            }
        }
    }

    private fun handleDirectChatDifferentialSend(
        root: AccessibilityNodeInfo,
        pkg: String,
        diffMessage: String,
    ) {
        val entryNode = findChatEntryInput(root, pkg) ?: return
        val currentText = entryNode.text?.toString().orEmpty()

        if (!currentText.contains("DIFERENCIAL", ignoreCase = true) &&
            !AutoSendOrchestrator.step2TextTypedInChat
        ) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    diffMessage,
                )
            }
            if (entryNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                AutoSendOrchestrator.step2TextTypedInChat = true
                AutoSendOrchestrator.lastActionTimestampMs = System.currentTimeMillis()
            }
            return
        }

        val sendButton = findSendOrNextButton(root, pkg, allowKeyboardAwareFabFallback = false)
        if (sendButton != null && tapNodeWithGestureOrClick(sendButton)) {
            AutoSendOrchestrator.onStep2DifferentialSent()
        }
    }

    /**
     * Dispatches a genuine screen touch gesture at the center of [node] (preferred on Xiaomi
     * HyperOS + WhatsApp Business because RecyclerView touch handlers and FABs always respond to
     * real taps), falling back to virtual ACTION_CLICK if gesture dispatch isn't accepted.
     */
    private fun tapNodeWithGestureOrClick(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 4 && rect.height() > 4 && rect.centerX() > 0 && rect.centerY() > 0) {
            val tapped = performRealTap(rect.centerX().toFloat(), rect.centerY().toFloat())
            if (tapped) return true
        }
        return clickNodeOrParent(node)
    }

    private fun performRealTap(x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 75L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }

    private fun isMediaPreviewScreen(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (findCaptionInput(root, pkg) != null) return true
        val mediaToolbarIds = listOf(
            "$pkg:id/crop",
            "$pkg:id/doodle",
            "$pkg:id/text",
            "$pkg:id/hd_button",
            "$pkg:id/add_caption",
            "$pkg:id/send_media_btn",
            "$pkg:id/media_preview_caption",
        )
        for (id in mediaToolbarIds) {
            if (root.findAccessibilityNodeInfosByViewId(id)?.isNotEmpty() == true) {
                return true
            }
        }
        return findFirstByContentDescription(
            root,
            listOf("recortar", "crop", "dibujar", "añade un comentario", "add a caption", "hd"),
        ) != null
    }

    /**
     * Walks the ENTIRE WhatsApp window tree below [minTopY] to find the selectable row matching
     * [groupName] (works even with Spannable highlighted search text!). If search is active and
     * only 1 row appears below the search bar, automatically selects it.
     */
    private fun findSelectableGroupRow(
        root: AccessibilityNodeInfo,
        pkg: String,
        groupName: String,
        minTopY: Int,
        allowSingleResultFallback: Boolean,
    ): AccessibilityNodeInfo? {
        val normalizedTarget = normalizeText(groupName)
        val screen = Rect()
        root.getBoundsInScreen(screen)
        val maxRowBottomY = (screen.height() * 0.90f).toInt()

        val allNodes = collectAllNodes(root)

        // Filter non-editable visible text nodes strictly below the search bar
        val textCandidates = allNodes.filter { node ->
            if (isEditableNode(node)) return@filter false
            val txt = node.text?.toString()?.trim().orEmpty()
            if (txt.isEmpty()) return@filter false
            val resId = node.viewIdResourceName.orEmpty().lowercase()
            if (resId.contains("search") || resId.contains("selected") || resId.contains("chip")) {
                return@filter false
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.top >= minTopY &&
                rect.bottom <= maxRowBottomY &&
                rect.width() > 30 &&
                rect.height() > 12
        }

        // 1. Exact normalized match on contact/group row name
        textCandidates.firstOrNull { node ->
            normalizeText(node.text?.toString().orEmpty()) == normalizedTarget &&
                !isSectionHeader(node.text?.toString().orEmpty())
        }?.let { return findRowContainerOrSelf(it, minTopY) }

        // 2. Partial normalized match (handles emojis or prefixes like "📌 NOTAS DE TRABAJO")
        textCandidates.firstOrNull { node ->
            val raw = node.text?.toString().orEmpty()
            val norm = normalizeText(raw)
            !isSectionHeader(raw) &&
                norm.length >= 2 &&
                (norm.contains(normalizedTarget) || normalizedTarget.contains(norm))
        }?.let { return findRowContainerOrSelf(it, minTopY) }

        // 3. Known ContactPicker row view IDs (even if text didn't match exact spelling)
        if (allowSingleResultFallback) {
            val rowNameIds = setOf(
                "$pkg:id/contactpicker_row_name",
                "$pkg:id/conversations_row_contact_name",
                "$pkg:id/chat_name",
                "$pkg:id/contactpicker_row_content",
            )
            val idRows = allNodes.filter { node ->
                val resId = node.viewIdResourceName.orEmpty()
                if (resId !in rowNameIds) return@filter false
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.top >= minTopY && rect.bottom <= maxRowBottomY && rect.height() >= 30
            }
            if (idRows.size == 1) {
                return findRowContainerOrSelf(idRows.first(), minTopY)
            }

            // 4. Single clickable item inside RecyclerView/ListView below the search bar
            val singleListItem = findSingleClickableListRow(allNodes, minTopY, maxRowBottomY)
            if (singleListItem != null) {
                return singleListItem
            }
        }

        return null
    }

    private fun findRowContainerOrSelf(
        textNode: AccessibilityNodeInfo,
        minTopY: Int,
    ): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = textNode
        var depth = 0
        while (current != null && depth < 5) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (current.isClickable &&
                rect.top >= minTopY - 24 &&
                rect.height() in 60..320
            ) {
                return current
            }
            current = current.parent
            depth++
        }
        return textNode
    }

    private fun findSingleClickableListRow(
        allNodes: List<AccessibilityNodeInfo>,
        minTopY: Int,
        maxBottomY: Int,
    ): AccessibilityNodeInfo? {
        for (node in allNodes) {
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("RecyclerView") || cls.contains("ListView")) {
                val listRect = Rect()
                node.getBoundsInScreen(listRect)
                if (listRect.height() < 150) continue

                val rowChildren = mutableListOf<AccessibilityNodeInfo>()
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val r = Rect()
                    child.getBoundsInScreen(r)
                    val childText = collectSubtreeText(child)
                    if (r.top >= minTopY - 12 &&
                        r.bottom <= maxBottomY &&
                        r.height() in 75..320 &&
                        childText.isNotBlank() &&
                        !isSectionHeader(childText)
                    ) {
                        rowChildren.add(child)
                    }
                }
                if (rowChildren.size == 1) {
                    return rowChildren.first()
                }
            }
        }
        return null
    }

    private fun isSectionHeader(text: String): Boolean {
        val norm = normalizeText(text)
        return norm in setOf(
            "chats frecuentes",
            "chats recientes",
            "contactos frecuentes",
            "otros contactos",
            "contactos en whatsapp",
            "grupos",
            "chats",
            "frequently contacted",
            "recent chats",
            "other contacts",
            "groups",
            "enviar a...",
            "enviar a",
            "send to...",
            "send to",
            "reenviar a...",
        )
    }

    private fun collectSubtreeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(t)
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString()
    }

    private fun collectAllNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(96)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            result.add(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun findSearchButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf(
            "$pkg:id/menuitem_search",
            "$pkg:id/search_button",
            "$pkg:id/search_icon",
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return findFirstByContentDescription(root, listOf("buscar", "search"))
    }

    private fun findSearchEditText(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf(
            "$pkg:id/search_src_text",
            "$pkg:id/search_input",
            "$pkg:id/search_bar_text",
            "$pkg:id/search_et",
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return findFirstEditableNode(root, excludeChatEntry = true)
    }

    private fun findCaptionInput(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf(
            "$pkg:id/caption",
            "$pkg:id/caption_entry",
            "$pkg:id/mention_entry",
            "$pkg:id/add_caption",
            "$pkg:id/media_preview_caption",
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return null
    }

    private fun findChatEntryInput(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf("$pkg:id/entry")
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return null
    }

    private fun findSendOrNextButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        allowKeyboardAwareFabFallback: Boolean,
    ): AccessibilityNodeInfo? {
        val ids = listOf(
            "$pkg:id/send",
            "$pkg:id/next_btn",
            "$pkg:id/next_button",
            "$pkg:id/send_media_btn",
            "$pkg:id/fab",
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.firstOrNull { it.isEnabled && hasValidBounds(it) }
                ?.let { return it }
        }
        findFirstByContentDescription(
            root,
            listOf("enviar", "send", "siguiente", "next", "reenviar", "forward", "listo", "done"),
        )?.let { return it }

        if (allowKeyboardAwareFabFallback) {
            return findRightSideActionFab(root)
        }
        return null
    }

    /**
     * Locates WhatsApp's green circular Send / Next FAB on the right side of the screen,
     * including when pushed upward above an open soft keyboard (top >= 35% of screen height).
     */
    private fun findRightSideActionFab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screen = Rect()
        root.getBoundsInScreen(screen)
        if (screen.width() <= 0 || screen.height() <= 0) return null

        val minLeft = (screen.width() * 0.70f).toInt()
        val minTop = (screen.height() * 0.35f).toInt()
        val allNodes = collectAllNodes(root)
        var lowestRightFab: AccessibilityNodeInfo? = null
        var lowestTop = -1

        for (node in allNodes) {
            if (!node.isClickable || !node.isEnabled || isEditableNode(node)) continue
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val isFabSized = rect.width() in 85..260 && rect.height() in 85..260
            val aspectOk = rect.width() <= rect.height() * 1.4 && rect.height() <= rect.width() * 1.4
            if (isFabSized && aspectOk && rect.left >= minLeft && rect.top >= minTop) {
                if (rect.top > lowestTop) {
                    lowestTop = rect.top
                    lowestRightFab = node
                }
            }
        }
        return lowestRightFab
    }

    private fun hasValidBounds(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.width() > 10 && rect.height() > 10 && rect.left >= 0 && rect.top >= 0
    }

    private fun findFirstByContentDescription(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
    ): AccessibilityNodeInfo? {
        val allNodes = collectAllNodes(root)
        for (node in allNodes) {
            val desc = normalizeText(node.contentDescription?.toString().orEmpty())
            if (desc.isNotEmpty() && node.isEnabled && hasValidBounds(node)) {
                if (keywords.any { kw -> desc == kw || desc.startsWith("$kw ") || desc.contains(kw) }) {
                    return node
                }
            }
        }
        return null
    }

    private fun findFirstEditableNode(
        root: AccessibilityNodeInfo,
        excludeChatEntry: Boolean,
    ): AccessibilityNodeInfo? {
        val allNodes = collectAllNodes(root)
        for (node in allNodes) {
            if (isEditableNode(node) && hasValidBounds(node)) {
                val resId = node.viewIdResourceName.orEmpty()
                if (!excludeChatEntry || !resId.endsWith(":id/entry")) {
                    return node
                }
            }
        }
        return null
    }

    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable || node.className?.toString()?.contains("EditText") == true
    }

    private fun normalizeText(input: String): String {
        val decomposed = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 7) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    companion object {
        private const val MIN_ACTION_INTERVAL_MS = 520L
        private const val POLL_INTERVAL_MS = 600L

        @Volatile
        var instance: WhatsAppAutoSendService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponent = ComponentName(context, WhatsAppAutoSendService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val componentName = ComponentName.unflattenFromString(splitter.next())
                if (componentName == expectedComponent) return true
            }
            return false
        }
    }
}
