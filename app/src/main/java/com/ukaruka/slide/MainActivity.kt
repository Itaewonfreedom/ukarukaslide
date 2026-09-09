package com.ukaruka.slide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var store: PhotoSourceStore
    private lateinit var sourceTitle: TextView
    private lateinit var sourceDetail: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) showAlbumPicker()
        else toast("사진 권한이 있어야 기기 앨범을 읽을 수 있습니다.")
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        persistReadAccess(uris)
        store.savePickedMedia(uris)
        refreshStatus()
        toast("사진 ${uris.size}장을 연결했습니다.")
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
        findViewById<MaterialButton>(R.id.pickPhotosButton).setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        findViewById<MaterialButton>(R.id.previewButton).setOnClickListener {
            launchFullscreenPreview()
        }
        findViewById<MaterialButton>(R.id.dreamSettingsButton).setOnClickListener {
            openDreamSettings()
        }

        configureIntervalButtons()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::sourceTitle.isInitialized) refreshStatus()
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
        sourceTitle.text = "앨범을 불러오는 중"
        sourceDetail.text = "기기의 사진 폴더를 확인하고 있습니다."

        thread(name = "album-loader") {
            val albums = runCatching { PhotoRepository(this).loadAlbums() }.getOrDefault(emptyList())
            runOnUiThread {
                refreshStatus()
                if (albums.isEmpty()) {
                    toast("읽을 수 있는 기기 앨범이 없습니다.")
                    return@runOnUiThread
                }

                val previousIds = store.albumIds.toSet()
                val checked = BooleanArray(albums.size) { albums[it].id in previousIds }
                val labels = albums.map { "${it.name}  ·  ${it.photoCount}장" }.toTypedArray()

                MaterialAlertDialogBuilder(this)
                    .setTitle("앨범·폴더 선택")
                    .setMessage("여러 개를 골라도 됩니다. 폴더에 새로 추가되는 사진도 자동으로 포함됩니다.")
                    .setMultiChoiceItems(labels, checked) { _, index, isChecked ->
                        checked[index] = isChecked
                    }
                    .setNegativeButton("취소", null)
                    .setPositiveButton("적용") { _, _ ->
                        val selected = albums.filterIndexed { index, _ -> checked[index] }
                        if (selected.isEmpty()) {
                            toast("앨범을 하나 이상 선택해 주세요.")
                        } else {
                            store.saveLocalAlbums(selected)
                            refreshStatus()
                            toast("앨범 ${selected.size}개를 연결했습니다.")
                        }
                    }
                    .show()
            }
        }
    }

    private fun persistReadAccess(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
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
                sourceTitle.text = "사진 소스를 연결해 주세요"
                sourceDetail.text = "기기 앨범 또는 Google Photos에서 시작할 수 있습니다."
            }
            PhotoSourceStore.SourceType.LOCAL_ALBUM -> {
                sourceTitle.text = "기기 앨범 ${store.albumIds.size}개"
                sourceDetail.text = store.albumNames.joinToString(" · ")
            }
            PhotoSourceStore.SourceType.PICKED_MEDIA -> {
                sourceTitle.text = "Google Photos · 선택 사진"
                sourceDetail.text = "${store.pickedMedia().size}장 연결됨"
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
