package com.ukaruka.slide

import android.graphics.Color
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

class AppearanceSettingsActivity : AppCompatActivity() {
    private lateinit var store: DisplayStyleStore
    private lateinit var playback: PlaybackPreferences
    private lateinit var previewTime: TextClock
    private lateinit var previewDate: TextClock
    private lateinit var previewClock: LinearLayout
    private val fontButtons = LinkedHashMap<Int, DisplayStyleStore.ClockFont>()
    private val positionButtons = LinkedHashMap<Int, DisplayStyleStore.ClockPosition>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DisplayStyleStore(this)
        playback = PlaybackPreferences(this)
        setContentView(buildContent())
        applyPreview()
    }

    private fun buildContent() = ScrollView(this).apply {
        isFillViewport = true
        fitsSystemWindows = true
        setBackgroundColor(getColor(R.color.background))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val gutter = resources.getDimensionPixelSize(R.dimen.page_gutter)
            setPadding(gutter, dp(24), gutter, dp(36))

            addView(MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "‹  돌아가기"
                isAllCaps = false
                strokeWidth = 0
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(-2, dp(48)))
            addView(title("화면과 재생"))
            addView(sectionTitle("폴더블 · 실험 기능"), top(24))
            addView(toggle("힌지 홀로그램 전환", playback.foldEffect) { playback.foldEffect = it })
            addView(body("접고 펼치는 각도에 따라 사진의 원근과 선명도가 바뀝니다. 센서를 지원하지 않으면 일반 재생합니다. 책처럼 좌우로 접는 화면용입니다."), top(6))
            addView(MaterialButton(context).apply {
                text = "힌지 · 양쪽 화면 테스트"
                isAllCaps = false
                setOnClickListener {
                    startActivity(android.content.Intent(this@AppearanceSettingsActivity, PreviewActivity::class.java)
                        .putExtra("dual_screen_test", true))
                }
            }, top(10))
            addView(body("테스트 화면에 힌지 각도와 양쪽 화면 지원 상태가 표시됩니다. 지원 기기에서는 시스템 확인창이 나타날 수 있습니다. 화면이 켜지는 시점은 기기에서 결정합니다."), top(6))
            addView(sectionTitle("사진"), top(24))
            addView(toggle("얼굴 중심으로 구도 맞추기", playback.faceFraming) { playback.faceFraming = it })
            addView(body("사진은 항상 화면을 꽉 채웁니다. 얼굴을 찾으면 얼굴 쪽으로 구도를 맞추며, 화면 비율에 따라 가장자리는 잘릴 수 있습니다."), top(6))
            addView(toggle("세로 사진 두 장 나란히", playback.portraitPairs) { playback.portraitPairs = it })
            addView(body("가로 화면에서 같은 추억 묶음의 세로 사진을 짝지어 보여줍니다."), top(6))
            addView(toggle("연사 후보 줄이기", playback.reduceBursts) { playback.reduceBursts = it })
            addView(body("같은 앨범에서 2초 안에 찍은 사진 중 한 장을 골라 재생합니다. 다음 회차에는 다른 사진이 선택될 수 있습니다."), top(6))
            addView(MaterialButton(context).apply {
                fun refresh() { text = "숨긴 사진 복원 (${playback.hidden.size}장)" }
                refresh()
                isAllCaps = false
                setOnClickListener {
                    playback.restoreHidden()
                    refresh()
                }
            }, top(12))
            addView(body("재생 화면: 터치하면 날짜·메뉴, 좌우 스와이프로 이전·다음, 길게 눌러 일시정지. 숨겨도 원본은 삭제되지 않습니다."), top(10))
            addView(buildPreview(), top(24, 230))

            addView(sectionTitle("시계"), top(30))
            addView(toggle("시계 표시", store.clockEnabled) {
                store.setClockEnabled(it)
                applyPreview()
            })
            addView(toggle("날짜 표시", store.showDate) {
                store.setShowDate(it)
                applyPreview()
            })
            addView(toggle("초 표시", store.showSeconds) {
                store.setShowSeconds(it)
                applyPreview()
            })
            val sizeLabel = settingLabel("시계·날짜 크기  ·  ${store.clockSizeSp}sp")
            addView(sizeLabel, top(18))
            addView(Slider(context).apply {
                valueFrom = 32f
                valueTo = 120f
                stepSize = 4f
                value = store.clockSizeSp.toFloat()
                addOnChangeListener { _, newValue, fromUser ->
                    if (!fromUser) return@addOnChangeListener
                    store.setClockSizeSp(newValue.roundToInt())
                    sizeLabel.text = "시계·날짜 크기  ·  ${newValue.roundToInt()}sp"
                    applyPreview()
                }
            }, top(4))

            addView(settingLabel("글꼴"), top(18))
            addView(buildFontGroup(), top(8))
            addView(settingLabel("기준 위치"), top(20))
            addView(buildPositionGroup(), top(8))
            addView(body("번인 완화를 위해 재생 중에는 이 위치 주변에서 조금씩 이동합니다."), top(10))

            addView(sectionTitle("야간"), top(30))
            addView(toggle("야간 모드 사용", playback.nightEnabled) { playback.nightEnabled = it })
            fun timeButton(start: Boolean) = MaterialButton(context).apply {
                fun minutes() = if (start) playback.nightStart else playback.nightEnd
                fun refresh() {
                    val value = minutes()
                    text = (if (start) "시작  " else "종료  ") +
                        String.format(java.util.Locale.getDefault(), "%02d:%02d", value / 60, value % 60)
                }
                refresh()
                isAllCaps = false
                setOnClickListener {
                    val value = minutes()
                    TimePickerDialog(this@AppearanceSettingsActivity, { _, hour, minute ->
                        if (start) playback.nightStart = hour * 60 + minute
                        else playback.nightEnd = hour * 60 + minute
                        refresh()
                    }, value / 60, value % 60, true).show()
                }
            }
            addView(timeButton(true), top(8))
            addView(timeButton(false), top(4))
            addView(body("기기의 현재 시각을 따릅니다. 시작과 종료가 같으면 야간 모드를 적용하지 않습니다."), top(6))
            val nightLabel = settingLabel("야간 화면 밝기  ·  ${playback.nightBrightness}%")
            addView(nightLabel, top(16))
            addView(Slider(context).apply {
                valueFrom = 5f
                valueTo = 50f
                stepSize = 5f
                value = playback.nightBrightness.toFloat()
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        playback.nightBrightness = value.roundToInt()
                        nightLabel.text = "야간 화면 밝기  ·  ${value.roundToInt()}%"
                    }
                }
            })
            addView(toggle("야간에는 완전히 검은 화면", playback.nightBlack) { playback.nightBlack = it })
            addView(body("검은 화면에서는 사진과 시계를 숨기고 재생을 쉽니다. 종료 시각에 다시 이어집니다. 앱을 종료하면 원래 밝기로 돌아갑니다."), top(8))

            addView(MaterialButton(context).apply {
                text = "완료"
                isAllCaps = false
                setOnClickListener { finish() }
            }, top(30, 56))
        })
    }

    private fun buildPreview() = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(getColor(R.color.surface))
            setStroke(dp(1), getColor(R.color.outline))
            cornerRadius = dp(4).toFloat()
        }
        clipToOutline = true
        previewClock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            previewTime = TextClock(context)
            previewDate = TextClock(context)
            addView(previewTime)
            addView(previewDate)
        }
        addView(previewClock, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(dp(20), dp(20), dp(20), dp(20))
        })
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(230)) }

    private fun buildFontGroup() = MaterialButtonToggleGroup(this).apply {
        isSingleSelection = true
        isSelectionRequired = true
        listOf(
            "산세리프" to DisplayStyleStore.ClockFont.SANS,
            "세리프" to DisplayStyleStore.ClockFont.SERIF,
            "디지털" to DisplayStyleStore.ClockFont.MONO
        ).forEach { (label, font) ->
            val button = choiceButton(label)
            fontButtons[button.id] = font
            addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (font == store.clockFont) check(button.id)
        }
        addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) fontButtons[checkedId]?.let {
                store.setClockFont(it)
                applyPreview()
            }
        }
    }

    private fun buildPositionGroup() = MaterialButtonToggleGroup(this).apply {
        isSingleSelection = true
        isSelectionRequired = true
        listOf(
            "↖" to DisplayStyleStore.ClockPosition.TOP_START,
            "↗" to DisplayStyleStore.ClockPosition.TOP_END,
            "↙" to DisplayStyleStore.ClockPosition.BOTTOM_START,
            "↘" to DisplayStyleStore.ClockPosition.BOTTOM_END
        ).forEach { (label, position) ->
            val button = choiceButton(label)
            positionButtons[button.id] = position
            button.textSize = 22f
            button.contentDescription = when (position) {
                DisplayStyleStore.ClockPosition.TOP_START -> "왼쪽 위"
                DisplayStyleStore.ClockPosition.TOP_END -> "오른쪽 위"
                DisplayStyleStore.ClockPosition.BOTTOM_START -> "왼쪽 아래"
                DisplayStyleStore.ClockPosition.BOTTOM_END -> "오른쪽 아래"
            }
            addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (position == store.clockPosition) check(button.id)
        }
        addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) positionButtons[checkedId]?.let {
                store.setClockPosition(it)
                applyPreview()
            }
        }
    }

    private fun applyPreview() {
        if (!::previewTime.isInitialized) return
        val alignment = when (store.clockPosition) {
            DisplayStyleStore.ClockPosition.TOP_START,
            DisplayStyleStore.ClockPosition.BOTTOM_START -> Gravity.START
            else -> Gravity.END
        }
        previewClock.gravity = alignment
        previewClock.layoutParams = (previewClock.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = alignment or when (store.clockPosition) {
                DisplayStyleStore.ClockPosition.TOP_START,
                DisplayStyleStore.ClockPosition.TOP_END -> Gravity.TOP
                else -> Gravity.BOTTOM
            }
        }
        val face = when (store.clockFont) {
            DisplayStyleStore.ClockFont.SANS -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            DisplayStyleStore.ClockFont.SERIF -> Typeface.create("serif", Typeface.NORMAL)
            DisplayStyleStore.ClockFont.MONO -> Typeface.create("monospace", Typeface.NORMAL)
        }
        previewTime.apply {
            format12Hour = if (store.showSeconds) "h:mm:ss" else "h:mm"
            format24Hour = if (store.showSeconds) "HH:mm:ss" else "HH:mm"
            textSize = (store.clockSizeSp * 0.62f).coerceIn(24f, 64f)
            typeface = face
            setTextColor(Color.WHITE)
            gravity = alignment
            visibility = if (store.clockEnabled) View.VISIBLE else View.INVISIBLE
        }
        previewDate.apply {
            format12Hour = "M월 d일 EEEE"
            format24Hour = "M월 d일 EEEE"
            textSize = (store.clockSizeSp * 0.20f).coerceIn(11f, 28f)
            typeface = face
            setTextColor(Color.argb(210, 255, 255, 255))
            gravity = alignment
            visibility = if (store.clockEnabled && store.showDate) View.VISIBLE else View.GONE
        }
    }

    private fun toggle(text: String, checked: Boolean, changed: (Boolean) -> Unit) =
        SwitchMaterial(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.ink))
            isChecked = checked
            setPadding(0, dp(5), 0, dp(5))
            setOnCheckedChangeListener { _, value -> changed(value) }
        }

    private fun choiceButton(text: String) = MaterialButton(
        this,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        id = View.generateViewId()
        this.text = text
        textSize = 11f
        isAllCaps = false
        minWidth = 0
        cornerRadius = dp(4)
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        letterSpacing = 0.18f
        setTextColor(getColor(R.color.mint))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(getColor(R.color.ink))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setLineSpacing(0f, 0.96f)
        setPadding(0, dp(10), 0, 0)
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(getColor(R.color.ink))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun settingLabel(text: String) = body(text).apply {
        setTextColor(getColor(R.color.ink))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(getColor(R.color.ink_muted))
    }

    private fun divider() = View(this).apply { setBackgroundColor(getColor(R.color.outline)) }

    private fun top(top: Int, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (height < 0) height else dp(height)
        ).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
