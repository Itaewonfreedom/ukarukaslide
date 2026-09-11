package com.ukaruka.slide

import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Public APIs only. Dual-screen sessions require the explicit test entry point. */
@OptIn(androidx.window.core.ExperimentalWindowApi::class)
class FoldController(private val activity: ComponentActivity, private val surface: FoldSurface,
    private val scene: AmbientDisplayView, private val dualTest: Boolean) {
    private val enabled = PlaybackPreferences(activity).foldEffect || dualTest
    private var active = false
    private var generation = 0
    private var postureJob: Job? = null
    private var areaJob: Job? = null
    private var session: WindowAreaSessionPresenter? = null
    private var mirror: FoldSurface? = null
    private var requested = false
    private var horizontalFold = false
    private var latestAngle: Float? = null
    private var dualStatus = "양쪽 화면 지원 확인 중"
    private var lastStatusAt = 0L
    private val resizeListener = android.view.View.OnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
        if (r - l != oldR - oldL) {
            surface.inner = (r - l) / activity.resources.displayMetrics.density >= 600f
            surface.hingeX = null // old display coordinates are invalid after a handoff
            surface.angle = if (horizontalFold) null else latestAngle
            scene.setFoldReveal(if (horizontalFold) null else latestAngle?.let { if (surface.inner) FoldGeometry.reveal(it) else 1f })
        }
    }
    private val status = TextView(activity).apply {
        setTextColor(android.graphics.Color.WHITE); setBackgroundColor(0xAA000000.toInt())
        setPadding(16, 16, 16, 16); textSize = 11f
        maxLines = 26; movementMethod = android.text.method.ScrollingMovementMethod()
        setOnLongClickListener {
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("hinge sensors", "$text\n\n${monitor.sensorDump()}"))
            android.widget.Toast.makeText(activity, "센서 목록을 복사했습니다", android.widget.Toast.LENGTH_SHORT).show(); true
        }
    }
    private val probe = SensorProbe(activity.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager,
        listOf("hinge_angle", "folding_angle", "folding_state", "folding_state_lpm", "accelerometer_sub", "gyroscope_sub", "hallIC"))
    private val channelProbe = HingeChannelProbe(activity.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager)
    private val motion = MotionHingeProbe(activity.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager)
    private val imu = DualImuHinge(activity.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager)
    private val monitor = HingeMonitor(activity) { angle ->
        if (dualTest) { motion.anchor(monitor.rawAngle); monitor.hint(motion.hintDegrees) }
        latestAngle = angle
        surface.angle = if (horizontalFold) null else angle
        scene.setFoldReveal(if (angle == null || horizontalFold) null else if (surface.inner) FoldGeometry.reveal(angle) else 1f)
        mirror?.angle = if (horizontalFold) null else angle
        updateStatus()
    }
    init {
        if (dualTest) activity.addContentView(status, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        surface.inner = activity.resources.configuration.smallestScreenWidthDp >= 600
    }
    private fun updateStatus() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastStatusAt < 200) return
        lastStatusAt = now
        if (!dualTest) return
        if (monitor.rawAngle >= 179f) imu.calibrateFlat()
        channelProbe.poll()
        status.text = "${monitor.status} · 보간 ${latestAngle?.toInt() ?: "—"}° · $dualStatus\n${monitor.inventory}\n${channelProbe.metadata}\n${channelProbe.report}\n${imu.status}\n${motion.report}\n${probe.report}\n길게 누르면 전체 센서 목록 복사\n${monitor.sensorDump()}"
    }
    fun start() {
        if (!enabled || active) return
        active = true; monitor.start()
        if (dualTest) { imu.start(); probe.start(); motion.start(); channelProbe.start() }
        surface.addOnLayoutChangeListener(resizeListener)
        val token = ++generation
        postureJob = activity.lifecycleScope.launch {
            try {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { layout ->
                    val feature = layout.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    surface.inner = feature != null || activity.resources.configuration.smallestScreenWidthDp >= 600
                    horizontalFold = feature?.orientation == FoldingFeature.Orientation.HORIZONTAL
                    surface.hingeX = feature?.takeIf { !horizontalFold }?.bounds?.centerX()?.toFloat()
                    surface.angle = if (horizontalFold) null else latestAngle
                    scene.setFoldReveal(if (horizontalFold) null else latestAngle?.let { if (surface.inner) FoldGeometry.reveal(it) else 1f })
                    surface.invalidate()
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { surface.angle = null }
        }
        if (!dualTest) return
        requested = false
        areaJob = activity.lifecycleScope.launch {
            try {
                val controller = WindowAreaController.getOrCreate()
                controller.windowAreaInfos.collect { infos ->
                    val area = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                    val capability = area?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status
                    dualStatus = when (capability) {
                        WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> "양쪽 화면 사용 가능"
                        WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> "양쪽 화면 활성"
                        WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> "현재 자세에서는 양쪽 화면 사용 불가"
                        else -> "이 기기는 양쪽 화면 API 미지원"
                    }
                    updateStatus()
                    if (area != null && capability == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE && !requested) {
                        requested = true
                        controller.presentContentOnWindowArea(area.token, activity, activity.mainExecutor,
                            object : WindowAreaPresentationSessionCallback {
                                override fun onSessionStarted(s: WindowAreaSessionPresenter) {
                                    if (!active || token != generation) { s.close(); return }
                                    session = s
                                    mirror = FoldSurface(s.context, scene).apply { inner = false; angle = latestAngle }
                                    s.setContentView(mirror!!)
                                }
                                override fun onSessionEnded(t: Throwable?) {
                                    if (token != generation) return
                                    session = null; mirror = null
                                    dualStatus = if (t == null) "양쪽 화면 세션 종료" else "양쪽 화면 시작 실패 · 일반 재생"
                                    updateStatus()
                                }
                                override fun onContainerVisibilityChanged(isVisible: Boolean) {
                                    if (token != generation) return
                                    mirror?.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                                }
                            })
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { dualStatus = "양쪽 화면을 사용할 수 없음 · 일반 재생"; updateStatus() }
        }
    }
    fun stop() {
        generation++
        surface.removeOnLayoutChangeListener(resizeListener)
        active = false; postureJob?.cancel(); areaJob?.cancel(); monitor.stop(); imu.stop(); probe.stop(); motion.stop(); channelProbe.stop()
        session?.close(); session = null; mirror = null
        surface.angle = null
    }
}
