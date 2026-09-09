package com.ukaruka.slide

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class AlbumPickerActivity : AppCompatActivity() {
    private lateinit var store: PhotoSourceStore
    private lateinit var albumList: LinearLayout
    private lateinit var applyButton: MaterialButton
    private lateinit var statusView: TextView
    private var albums: List<DeviceAlbum> = emptyList()
    private val selectedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhotoSourceStore(this)
        selectedIds += store.albumIds
        setContentView(buildContent())
        loadAlbums()
    }

    private fun buildContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        fitsSystemWindows = true
        setBackgroundColor(getColor(R.color.background))
        val gutter = resources.getDimensionPixelSize(R.dimen.page_gutter)
        setPadding(gutter, dp(24), gutter, dp(20))

        addView(TextView(context).apply {
            text = "기기에 저장된 사진"
            textSize = 11f
            setTextColor(getColor(R.color.mint))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        addView(TextView(context).apply {
            text = "앨범 선택"
            textSize = 28f
            setTextColor(getColor(R.color.ink))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }, top(8))
        addView(TextView(context).apply {
            text = "재생할 앨범을 선택하세요. 여러 개를 함께 사용할 수 있습니다."
            textSize = 14f
            setTextColor(getColor(R.color.ink_muted))
        }, top(8))

        statusView = TextView(context).apply {
            text = "기기 앨범을 불러오는 중…"
            textSize = 14f
            setTextColor(getColor(R.color.mint))
            setPadding(0, dp(18), 0, dp(10))
        }
        addView(statusView)

        albumList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(ScrollView(context).apply {
            clipToPadding = false
            addView(albumList, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(MaterialButton(context).apply {
                text = "취소"
                isAllCaps = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(0, dp(54), 0.34f).apply { marginEnd = dp(8) })
            applyButton = MaterialButton(context).apply {
                isAllCaps = false
                setOnClickListener { applySelection() }
            }
            addView(applyButton, LinearLayout.LayoutParams(0, dp(54), 0.66f))
        }, top(14))
        updateApplyButton()
    }

    private fun loadAlbums() {
        thread(name = "album-picker-loader") {
            val result = runCatching { PhotoRepository(this).loadAlbums() }
            runOnUiThread {
                result.onSuccess { loaded ->
                    albums = loaded
                    selectedIds.retainAll(loaded.mapTo(mutableSetOf()) { it.id })
                    showAlbums()
                }.onFailure {
                    statusView.text = "앨범을 읽지 못했습니다. 사진 권한을 다시 확인해 주세요."
                    statusView.setTextColor(getColor(R.color.violet))
                }
            }
        }
    }

    private fun showAlbums() {
        albumList.removeAllViews()
        if (albums.isEmpty()) {
            statusView.text = "기기에 표시할 수 있는 사진 앨범이 없습니다."
            updateApplyButton()
            return
        }
        statusView.text = "${albums.size}개 앨범 · 여러 개 선택 가능"
        albums.forEach { album -> albumList.addView(albumRow(album), top(8)) }
        updateApplyButton()
    }

    private fun albumRow(album: DeviceAlbum): MaterialCardView {
        val checkBox = MaterialCheckBox(this).apply {
            isChecked = album.id in selectedIds
            contentDescription = "${album.name} 선택"
        }
        val card = MaterialCardView(this).apply {
            radius = dp(4).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.surface))
            strokeWidth = dp(1)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(15), dp(12), dp(15))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = album.name
                        textSize = 16f
                        setTextColor(getColor(R.color.ink))
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    })
                    addView(TextView(context).apply {
                        text = "사진 ${album.photoCount}장"
                        textSize = 13f
                        setTextColor(getColor(R.color.ink_muted))
                    }, top(3))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(checkBox, LinearLayout.LayoutParams(dp(48), dp(48)))
            })
        }
        fun refreshCard(checked: Boolean) {
            card.strokeColor = getColor(if (checked) R.color.mint else R.color.outline)
        }
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds += album.id else selectedIds -= album.id
            refreshCard(checked)
            updateApplyButton()
        }
        card.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
        refreshCard(checkBox.isChecked)
        return card
    }

    private fun applySelection() {
        val selected = albums.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        store.saveLocalAlbums(selected)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun updateApplyButton() {
        if (!::applyButton.isInitialized) return
        applyButton.text = "${selectedIds.size}개 앨범 적용"
        applyButton.isEnabled = selectedIds.isNotEmpty()
    }

    private fun top(value: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(value)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
