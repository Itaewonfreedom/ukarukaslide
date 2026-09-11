package com.ukaruka.slide

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class SlideshowPlayerView(context: Context) : FrameLayout(context) {
    private val preferences = PlaybackPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<SlidePhoto>()
    private val history = mutableListOf<List<SlidePhoto>>()
    private var historyIndex = -1
    private var allPhotos = emptyList<SlidePhoto>()
    private var current = emptyList<SlidePhoto>()
    private var active: Layer? = null
    private var outgoing: Layer? = null
    private var running = false
    private var userPaused = false
    private var nightPaused = false
    private var lifecyclePaused = false
    private var loading = false
    private var pendingDisplay: (() -> Unit)? = null
    private var pendingFreshPhotos = emptyList<SlidePhoto>()
    private var generation = 0
    private var interval = 15_000L
    private var order = PhotoSourceStore.PlaybackOrder.RANDOM
    private var elapsed = 0L
    private var lastFrame = 0L
    private var nextAt = Long.MAX_VALUE
    private val paused get() = userPaused || nightPaused || lifecyclePaused
    data class LayerState(val photos: List<PreparedPhoto>, val born: Long, val alpha: Float)
    data class Snapshot(val all: List<SlidePhoto>, val queue: List<SlidePhoto>,
        val history: List<List<SlidePhoto>>, val index: Int, val interval: Long,
        val order: PhotoSourceStore.PlaybackOrder, val elapsed: Long, val nextAt: Long,
        val userPaused: Boolean, val active: LayerState?, val outgoing: LayerState?)
    private var inFlight = emptyList<SlidePhoto>()
    private var inFlightFresh = false
    fun snapshot(): Snapshot = Snapshot(allPhotos, (if (inFlightFresh) inFlight else emptyList()) + queue.toList(),
        history.toList(), historyIndex, interval, order, elapsed, nextAt, userPaused,
        active?.let { LayerState(it.photos, it.born, it.alpha) },
        outgoing?.let { LayerState(it.photos, it.born, it.alpha) })
    fun restore(s: Snapshot) {
        stop()
        allPhotos = s.all; queue.clear(); queue.addAll(s.queue)
        history.clear(); history.addAll(s.history); historyIndex = s.index
        interval = s.interval; order = s.order; elapsed = s.elapsed; nextAt = s.nextAt
        userPaused = s.userPaused
        current = s.active?.photos?.map { it.photo } ?: emptyList()
        listOfNotNull(active, outgoing).forEach(::removeView)
        fun layer(state: LayerState?): Layer? = state?.let {
            Layer(it.photos, it.born).apply { alpha = it.alpha; this@SlideshowPlayerView.addView(this, 0, LayoutParams(-1, -1)) }
        }
        outgoing = layer(s.outgoing); active = layer(s.active)
        active?.bringToFront(); empty.bringToFront(); controls.bringToFront()
        empty.visibility = if (active == null) View.VISIBLE else View.GONE
        running = true
        if (active == null) post { if (running) navigate(1) }
        resumeFrames()
    }
    fun suspendPlayback() { lifecyclePaused = true; removeCallbacks(frame); controls.visibility = View.GONE }
    fun resumePlayback() { lifecyclePaused = false; resumeFrames() }
    private val empty = TextView(context).apply {
        text = "앨범을 선택해 주세요."
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
    }
    private val controls = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(24))
        setBackgroundColor(0xDD171715.toInt())
        visibility = View.GONE
    }
    private val hideControls = Runnable { controls.visibility = View.GONE }
    private inner class Layer(val photos: List<PreparedPhoto>, val born: Long = elapsed) : LinearLayout(context) {
        val images = photos.map { FramedPhotoView(context, it, preferences.faceFraming) }
        init {
            orientation = HORIZONTAL
            setBackgroundColor(Color.BLACK)
            images.forEach { view ->
                val age = (elapsed - born).coerceAtLeast(0).toDouble()
                view.progress((age / (age + interval + FADE_MS)).toFloat())
                addView(view, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
    }
    private val frame = object : Runnable {
        override fun run() {
            if (!running || paused) return
            val now = SystemClock.uptimeMillis()
            elapsed += (now - lastFrame).coerceAtLeast(0)
            lastFrame = now
            listOfNotNull(active, outgoing).forEach { layer ->
                val age = (elapsed - layer.born).toDouble()
                val p = (age / (age + interval + FADE_MS)).toFloat()
                layer.images.forEach { it.progress(p) }
            }
            active?.let { layer ->
                layer.alpha = if (outgoing == null) 1f
                    else ((elapsed - layer.born).toFloat() / FADE_MS).coerceIn(0f, 1f)
                if (layer.alpha >= 1f) {
                    outgoing?.let(::removeView)
                    outgoing = null
                }
            }
            if (!loading && elapsed >= nextAt) navigate(1, manual = false)
            postOnAnimation(this)
        }
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            showControls()
            performClick()
            return true
        }
        override fun onLongPress(e: MotionEvent) { togglePause() }
        override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
            if (a == null) return false
            val dx = b.x - a.x
            if (abs(dx) < dp(60) || abs(dx) <= abs(b.y - a.y)) return false
            navigate(if (dx < 0) 1 else -1)
            return true
        }
    })

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        addView(empty, LayoutParams(-1, -1))
        addView(controls, LayoutParams(-1, -2, Gravity.BOTTOM))
        contentDescription = "사진 슬라이드. 좌우 스와이프로 이동, 길게 눌러 일시정지, 터치하여 메뉴"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)
    override fun performClick(): Boolean { super.performClick(); return true }

    fun start(photos: List<SlidePhoto>, slideIntervalMs: Long, playbackOrder: PhotoSourceStore.PlaybackOrder) {
        stop()
        generation++
        allPhotos = photos.distinctBy { it.uri }.filterNot { it.uri.toString() in preferences.hidden }
        queue.clear()
        history.clear()
        historyIndex = -1
        current = emptyList()
        listOfNotNull(active, outgoing).forEach(::removeView)
        active = null
        outgoing = null
        interval = slideIntervalMs.coerceIn(5_000, 60_000)
        order = playbackOrder
        elapsed = 0
        nextAt = Long.MAX_VALUE
        userPaused = false
        running = true
        empty.text = if (allPhotos.isEmpty()) "재생할 사진이 없습니다.\n앨범이나 숨긴 사진 설정을 확인하세요." else "사진을 불러오는 중…"
        empty.visibility = View.VISIBLE
        post { if (running) navigate(1) }
        resumeFrames()
    }

    fun stop() {
        inFlight = emptyList()
        inFlightFresh = false
        running = false
        generation++
        loading = false
        pendingDisplay = null
        pendingFreshPhotos = emptyList()
        removeCallbacks(frame)
        handler.removeCallbacksAndMessages(null)
        controls.visibility = View.GONE
    }

    fun release() {
        stop()
        decoder.shutdownNow()
        listOfNotNull(active, outgoing).forEach(::removeView)
        active = null
        outgoing = null
        history.clear()
        queue.clear()
        allPhotos = emptyList()
    }

    fun setNightPaused(value: Boolean) {
        if (nightPaused == value) return
        nightPaused = value
        if (paused) removeCallbacks(frame) else resumeFrames()
        if (value) controls.visibility = View.GONE
    }

    private fun resumeFrames() {
        removeCallbacks(frame)
        lastFrame = SystemClock.uptimeMillis()
        if (running && !paused) {
            val pending = pendingDisplay
            pendingDisplay = null
            pendingFreshPhotos = emptyList()
            pending?.invoke()
        }
        if (running && !paused) postOnAnimation(frame)
    }

    private fun togglePause() {
        userPaused = !userPaused
        if (paused) removeCallbacks(frame) else resumeFrames()
        showControls()
    }

    private fun refill() {
        val visible = allPhotos.filterNot { it.uri.toString() in preferences.hidden }
        val ordered = if (order == PhotoSourceStore.PlaybackOrder.RANDOM) {
            val candidates = if (preferences.reduceBursts) PlaybackRules.memories(
                visible, { it.capturedAtMs }, { if (it.dateIsCapture) it.albumId else it.uri.toString() },
                true, false
            ) else visible
            candidates.shuffled()
        } else PlaybackRules.memories(visible, { it.capturedAtMs },
            { if (it.dateIsCapture) it.albumId else it.uri.toString() },
            preferences.reduceBursts, order == PhotoSourceStore.PlaybackOrder.REVERSE_CHRONOLOGICAL)
        val cycle = ordered.toMutableList()
        if (cycle.size > 1 && cycle.first().uri in current.map { it.uri }) {
            val swap = cycle.indexOfFirst {
                it.uri !in current.map { p -> p.uri } &&
                    (order == PhotoSourceStore.PlaybackOrder.RANDOM ||
                        PlaybackRules.sameMoment(it.capturedAtMs, cycle.first().capturedAtMs))
            }
            if (swap > 0) java.util.Collections.swap(cycle, 0, swap)
        }
        cycle.forEach(queue::addLast)
    }

    private fun navigate(direction: Int, manual: Boolean = true) {
        if (manual && pendingDisplay != null) {
            pendingDisplay = null
            pendingFreshPhotos.asReversed().forEach(queue::addFirst)
            pendingFreshPhotos = emptyList()
            loading = false
        }
        if (!running || loading) return
        var targetIndex = historyIndex + direction
        val selected = if (targetIndex in history.indices) history[targetIndex] else {
            if (direction < 0) return
            if (queue.isEmpty()) refill()
            val first = queue.pollFirst() ?: run { nextAt = Long.MAX_VALUE; return }
            val pair = if (preferences.portraitPairs && width > height && first.portrait) {
                queue.firstOrNull { it.portrait && PlaybackRules.sameMoment(first.capturedAtMs, it.capturedAtMs) }
            } else null
            pair?.let { queue.remove(it) }
            targetIndex = history.size
            listOfNotNull(first, pair)
        }
        load(selected, targetIndex, manual)
    }

    private fun load(selected: List<SlidePhoto>, targetIndex: Int, manual: Boolean = true) {
        loading = true
        inFlight = selected
        inFlightFresh = targetIndex >= history.size
        val token = generation
        val protect = preferences.faceFraming
        decoder.execute {
            val prepared = selected.mapNotNull { photo ->
                try {
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo.uri)) { d, info, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val ratio = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                        d.setTargetSize((info.size.width * ratio).roundToInt().coerceAtLeast(1),
                            (info.size.height * ratio).roundToInt().coerceAtLeast(1))
                    }
                    val faces = if (protect) runCatching { FaceFraming.detect(bitmap) }.getOrNull() else null
                    PreparedPhoto(photo, bitmap, faces)
                } catch (_: Exception) { null }
            }
            val deliver = deliver@{
                if (!running || token != generation) return@deliver
                loading = false
                inFlight = emptyList()
                inFlightFresh = false
                val valid = prepared.filterNot { it.photo.uri.toString() in preferences.hidden }
                val bad = selected.filter { p -> prepared.none { it.photo.uri == p.uri } }.map { it.uri }.toSet()
                allPhotos = allPhotos.filterNot { it.uri in bad }
                queue.removeAll { it.uri in bad }
                // Remove failed or newly hidden items from back/forward history as well.
                history.indices.forEach { i ->
                    history[i] = history[i].filterNot { it.uri in bad || it.uri.toString() in preferences.hidden }
                }
                if (valid.isEmpty()) {
                    if (targetIndex in history.indices) historyIndex = targetIndex
                    if (allPhotos.isEmpty()) {
                        nextAt = Long.MAX_VALUE
                        empty.text = "읽을 수 있는 사진이 없습니다.\n사진 권한과 앨범을 확인하세요."
                        empty.visibility = View.VISIBLE
                    } else handler.post { navigate(1) }
                    return@deliver
                }
                current = valid.map { it.photo }
                if (targetIndex in history.indices) historyIndex = targetIndex else {
                    history.add(current)
                    if (history.size > 100) history.removeAt(0)
                    historyIndex = history.lastIndex
                }
                outgoing?.let(::removeView)
                outgoing = active
                // If a user moves during a crossfade, make the previous photo opaque.
                outgoing?.alpha = 1f
                active = Layer(valid).also { layer ->
                    addView(layer, 0, LayoutParams(-1, -1))
                    layer.bringToFront()
                    layer.alpha = if (outgoing == null || paused) 1f else 0f
                }
                if (paused) { outgoing?.let(::removeView); outgoing = null }
                empty.visibility = View.GONE
                empty.bringToFront()
                controls.bringToFront()
                nextAt = elapsed + interval
                if (controls.visibility == View.VISIBLE) showControls()
            }
            handler.post {
                if (!running || token != generation) return@post
                if (paused && !manual && active != null) {
                    pendingDisplay = deliver
                    pendingFreshPhotos = if (targetIndex >= history.size) selected else emptyList()
                } else deliver()
            }
        }
    }

    private fun showControls() {
        controls.removeAllViews()
        val row = LinearLayout(context)
        fun action(label: String, callback: () -> Unit) = Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34332E.toInt())
            setOnClickListener { callback() }
            minHeight = dp(48)
        }
        row.addView(action("이전") { navigate(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(action(if (userPaused) "재생" else "일시정지") { togglePause() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(action("다음") { navigate(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(row)
        val format = SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.getDefault())
        current.forEachIndexed { index, photo ->
            val prefix = if (current.size == 2) (if (index == 0) "왼쪽 · " else "오른쪽 · ") else ""
            val date = if (photo.capturedAtMs > 0) format.format(Date(photo.capturedAtMs)) else "날짜 없음"
            controls.addView(action(prefix + date + (if (photo.dateIsCapture) "" else " (저장일)") + " · 숨기기") {
                if (loading) return@action
                preferences.hide(photo.uri.toString())
                queue.removeAll { it.uri == photo.uri }
                val remaining = current.filterNot { it.uri == photo.uri }
                history.indices.forEach { i -> history[i] = history[i].filterNot { it.uri == photo.uri } }
                if (remaining.isNotEmpty()) load(remaining, historyIndex) else {
                    allPhotos = allPhotos.filterNot { it.uri == photo.uri }
                    listOfNotNull(active, outgoing).forEach(::removeView)
                    active = null
                    outgoing = null
                    current = emptyList()
                    empty.text = "다른 사진을 불러오는 중…"
                    empty.visibility = View.VISIBLE
                    if (allPhotos.isEmpty()) empty.text = "사진이 모두 숨겨졌습니다.\n설정에서 숨긴 사진을 복원할 수 있습니다."
                    else navigate(1)
                }
                showControls()
            })
        }
        controls.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 6_000)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    companion object { private const val FADE_MS = 2_000L }
}
