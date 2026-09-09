package com.ukaruka.slide

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var store: PhotoSourceStore
    private lateinit var sourceStatus: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            showAlbumPicker()
        } else {
            toast("사진 권한이 있어야 기기 앨범을 읽을 수 있습니다.")
        }
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        persistReadAccess(uris)
        store.savePickedMedia(uris)
        refreshStatus()
        toast("${uris.size}장을 저장했습니다.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhotoSourceStore(this)
        setContentView(buildContent())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::sourceStatus.isInitialized) refreshStatus()
    }

    private fun buildContent(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(36))
            setBackgroundColor(Color.rgb(247, 247, 247))
        }

        content.addView(TextView(this).apply {
            text = "Ukaruka Slide"
            textSize = 30f
            setTextColor(Color.rgb(20, 20, 20))
            gravity = Gravity.CENTER
        }, matchWrap(top = 0, bottom = 8))

        content.addView(TextView(this).apply {
            text = "휴대폰과 태블릿을 내 사진 액자로"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }, matchWrap(bottom = 28))

        sourceStatus = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(30, 30, 30))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(sourceStatus, matchWrap(bottom = 18))

        content.addView(actionButton("기기 사진 앨범 선택") { requestAlbumAccess() }, matchWrap(bottom = 10))
        content.addView(actionButton("Google Photos / 사진 선택") {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, matchWrap(bottom = 8))

        content.addView(TextView(this).apply {
            text = "시스템 사진 선택기에서 Google Photos의 앨범도 열 수 있습니다. 선택한 사진만 앱에 공유됩니다."
            textSize = 13f
            setTextColor(Color.GRAY)
        }, matchWrap(bottom = 24))

        content.addView(TextView(this).apply {
            text = "사진 전환 간격"
            textSize = 16f
            setTextColor(Color.rgb(30, 30, 30))
        }, matchWrap(bottom = 4))

        val intervalGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
            val choices = listOf(10 to "10초", 15 to "15초", 30 to "30초")
            choices.forEachIndexed { index, (seconds, label) ->
                addView(RadioButton(this@MainActivity).apply {
                    id = ViewId.next()
                    text = label
                    isChecked = store.slideIntervalMs == seconds * 1_000L
                    setOnClickListener { store.setSlideIntervalMs(seconds * 1_000L) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (index == 1 && store.slideIntervalMs !in choices.map { it.first * 1_000L }) {
                    (getChildAt(index) as RadioButton).isChecked = true
                }
            }
        }
        content.addView(intervalGroup, matchWrap(bottom = 20))

        content.addView(actionButton("전체 화면 미리보기") {
            startActivity(Intent(this, PreviewActivity::class.java))
        }, matchWrap(bottom = 10))

        content.addView(actionButton("시스템 화면 보호기 설정 열기") { openDreamSettings() }, matchWrap(bottom = 12))

        content.addView(TextView(this).apply {
            text = "설정에서 Ukaruka Slide를 선택하면 충전 중 또는 도킹 중 Android 화면 보호기로 자동 실행됩니다."
            textSize = 13f
            setTextColor(Color.GRAY)
        }, matchWrap(bottom = 20))

        return ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun requestAlbumAccess() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            showAlbumPicker()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun showAlbumPicker() {
        sourceStatus.text = "앨범을 불러오는 중…"
        thread(name = "album-loader") {
            val albums = runCatching { PhotoRepository(this).loadAlbums() }.getOrDefault(emptyList())
            runOnUiThread {
                refreshStatus()
                if (albums.isEmpty()) {
                    toast("읽을 수 있는 기기 앨범이 없습니다.")
                    return@runOnUiThread
                }
                val labels = albums.map { "${it.name}  ·  ${it.photoCount}장" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("랜덤 재생할 앨범")
                    .setItems(labels) { _, index ->
                        val selected = albums[index]
                        store.saveLocalAlbum(selected.id, selected.name)
                        refreshStatus()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private fun persistReadAccess(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun openDreamSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        val target = intents.firstOrNull { it.resolveActivity(packageManager) != null }
        if (target != null) startActivity(target) else toast("화면 보호기 설정 화면을 열 수 없습니다.")
    }

    private fun refreshStatus() {
        sourceStatus.text = "현재 소스\n${store.summary()}"
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private object ViewId {
        private var value = 10_000
        fun next(): Int = value++
    }
}
