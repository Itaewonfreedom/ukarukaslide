package com.ukaruka.slide

import android.graphics.Color
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
    private lateinit var previewTime: TextClock
    private lateinit var previewDate: TextClock
    private lateinit var previewClock: LinearLayout
    private val fontButtons = LinkedHashMap<Int, DisplayStyleStore.ClockFont>()
    private val positionButtons = LinkedHashMap<Int, DisplayStyleStore.ClockPosition>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DisplayStyleStore(this)
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
            addView(title("시계와 날짜"))
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
