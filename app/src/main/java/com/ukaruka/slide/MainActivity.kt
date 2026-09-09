package com.ukaruka.slide

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var store: PhotoSourceStore
    private lateinit var sourceTitle: TextView
    private lateinit var sourceDetail: TextView
    private var returningFromPermissionSettings = false

    private val albumPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) refreshStatus()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && hasFullPhotoAccess()) showAlbumPicker()
        else showFullAlbumAccessDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhotoSourceStore(this)
        setContentView(R.layout.activity_main)

        sourceTitle = findViewById(R.id.sourceTitle)
        sourceDetail = findViewById(R.id.sourceDetail)

        findViewById<MaterialButton>(R.id.selectAlbumsButton).setOnClickListener {
            requestAlbumAccess()
        }
        findViewById<MaterialButton>(R.id.styleSettingsButton).setOnClickListener {
            startActivity(Intent(this, AppearanceSettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.previewButton).setOnClickListener {
            launchFullscreenPreview()
        }
        findViewById<MaterialButton>(R.id.dreamSettingsButton).setOnClickListener {
            openDreamSettings()
        }

        configureIntervalButtons()
        configurePlaybackOrderButtons()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::sourceTitle.isInitialized) refreshStatus()
        if (returningFromPermissionSettings) {
            returningFromPermissionSettings = false
            if (hasFullPhotoAccess()) showAlbumPicker()
            else toast("앨범 전체 재생에는 사진을 모두 허용해야 합니다.")
        }
    }

    private fun configureIntervalButtons() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.intervalGroup)
        group.check(
            when (store.slideIntervalMs) {
                10_000L -> R.id.interval10
                30_000L -> R.id.interval30
                else -> R.id.interval15
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            store.setSlideIntervalMs(
                when (checkedId) {
                    R.id.interval10 -> 10_000L
                    R.id.interval30 -> 30_000L
                    else -> 15_000L
                }
            )
        }
    }

    private fun configurePlaybackOrderButtons() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.playbackOrderGroup)
        group.check(
            when (store.playbackOrder) {
                PhotoSourceStore.PlaybackOrder.RANDOM -> R.id.orderRandom
                PhotoSourceStore.PlaybackOrder.CHRONOLOGICAL -> R.id.orderChronological
                PhotoSourceStore.PlaybackOrder.REVERSE_CHRONOLOGICAL -> R.id.orderReverse
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            store.setPlaybackOrder(
                when (checkedId) {
                    R.id.orderChronological -> PhotoSourceStore.PlaybackOrder.CHRONOLOGICAL
                    R.id.orderReverse -> PhotoSourceStore.PlaybackOrder.REVERSE_CHRONOLOGICAL
                    else -> PhotoSourceStore.PlaybackOrder.RANDOM
                }
            )
        }
    }

    private fun requestAlbumAccess() {
        if (hasFullPhotoAccess()) {
            showAlbumPicker()
        } else {
            permissionLauncher.launch(requiredPhotoPermission())
        }
    }

    private fun hasFullPhotoAccess(): Boolean = ContextCompat.checkSelfPermission(
        this,
        requiredPhotoPermission()
    ) == PackageManager.PERMISSION_GRANTED

    private fun requiredPhotoPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun showFullAlbumAccessDialog() {
        val selectedOnly = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        val explanation = if (selectedOnly) {
            "현재 ‘선택한 사진만 허용’ 상태입니다. 앨범·폴더 전체를 자동 재생하려면 사진 권한을 ‘모두 허용’으로 바꿔 주세요."
        } else {
            "기기 앨범·폴더 전체를 읽으려면 사진 권한이 필요합니다. 설정에서 사진을 ‘모두 허용’해 주세요."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("앨범 전체 권한이 필요해요")
            .setMessage(explanation)
            .setNegativeButton("취소", null)
            .setPositiveButton("설정 열기") { _, _ ->
                returningFromPermissionSettings = true
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .show()
    }

    private fun showAlbumPicker() {
        albumPickerLauncher.launch(Intent(this, AlbumPickerActivity::class.java))
    }

    private fun launchFullscreenPreview() {
        val intent = Intent(this, PreviewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    private fun openDreamSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        val target = intents.firstOrNull { it.resolveActivity(packageManager) != null }
        if (target != null) startActivity(target)
        else toast("화면 보호기 설정 화면을 열 수 없습니다.")
    }

    private fun refreshStatus() {
        when (store.sourceType) {
            PhotoSourceStore.SourceType.NONE -> {
                sourceTitle.text = "앨범을 선택하세요"
                sourceDetail.text = "기기의 앨범·폴더를 선택해 주세요."
            }
            PhotoSourceStore.SourceType.LOCAL_ALBUM -> {
                sourceTitle.text = "${store.albumIds.size}개의 앨범"
                sourceDetail.text = store.albumNames.joinToString(" · ")
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
