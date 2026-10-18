package com.javadmk1.autosubtitle

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import android.widget.MediaController
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.database.Cursor
import android.provider.OpenableColumns
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import android.text.Layout
import android.graphics.Typeface
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Effects
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import org.json.JSONObject
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private val cues = mutableListOf<Cue>()
    private lateinit var selectedFileLabel: TextView
    private lateinit var videoPreview: VideoView
    private lateinit var previewFrame: FrameLayout
    private lateinit var subtitleOverlay: TextView
    private lateinit var fontSpinner: Spinner
    private lateinit var animationSpinner: Spinner
    private lateinit var fontSamplePreview: TextView
    private lateinit var textColorSpinner: Spinner
    private lateinit var outlineColorSpinner: Spinner
    private lateinit var boxColorSpinner: Spinner
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var positionSeekBar: SeekBar
    private lateinit var outlineSeekBar: SeekBar
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var boldCheckBox: CheckBox
    private lateinit var shadowCheckBox: CheckBox
    private lateinit var boxCheckBox: CheckBox
    private var subtitleSizeSp = 19
    private var subtitlePosition = 88
    private var outlineWidth = 2
    private var backgroundOpacity = 80
    private var fontIndex = 0
    private var bundledVazirmatnTypeface: Typeface? = null
    private var bundledVazirmatnLoaded = false
    private var animationIndex = 1
    private var textColorIndex = 0
    private var outlineColorIndex = 0
    private var boxColorIndex = 0
    private var isBold = true
    private var shadowEnabled = true
    private var boxEnabled = true
    private val fontNames = arrayOf("پیش‌فرض فارسی/انگلیسی", "Sans-serif", "Sans-serif light", "Sans-serif medium", "Sans-serif black", "Serif", "Monospace", "Serif monospace", "Sans-serif condensed", "Casual", "Cursive", "Vazirmatn (فونت فارسی اختصاصی)")
    private val fontFamilies = arrayOf("sans-serif", "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-black", "serif", "monospace", "serif-monospace", "sans-serif-condensed", "casual", "cursive", "sans-serif")
    private val animationNames = arrayOf("بدون انیمیشن", "محو شدن (Fade)", "ورود از پایین (Slide)", "ظاهر شدن نرم (Pop)")
    private val textColorNames = arrayOf("سفید", "زرد", "فیروزه‌ای", "قرمز", "سبز")
    private val outlineColorNames = arrayOf("مشکی", "سفید", "سرمه‌ای", "قرمز")
    private val boxColorNames = arrayOf("مشکی", "سرمه‌ای", "زرشکی", "بدون رنگ")
    private val textColors = intArrayOf(Color.WHITE, Color.YELLOW, 0xFF40E0D0.toInt(), Color.RED, Color.GREEN)
    private val outlineColors = intArrayOf(Color.BLACK, Color.WHITE, 0xFF101B35.toInt(), 0xFFB00020.toInt())
    private val boxColors = intArrayOf(Color.BLACK, 0xFF101B35.toInt(), 0xFF6D1020.toInt(), Color.TRANSPARENT)
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewTicker = object : Runnable {
        override fun run() {
            if (::videoPreview.isInitialized && ::subtitleOverlay.isInitialized &&
                videoPreview.visibility == android.view.View.VISIBLE && selectedMediaUri != null
            ) {
                val cueIndex = findActiveCueIndex(cues, videoPreview.currentPosition.toLong())
                if (cueIndex != lastPreviewCueIndex) {
                    lastPreviewCueIndex = cueIndex
                    subtitleOverlay.animate().cancel()
                    subtitleOverlay.alpha = 1f
                    subtitleOverlay.translationY = 0f
                    subtitleOverlay.scaleX = 1f
                    subtitleOverlay.scaleY = 1f
                    subtitleOverlay.text = if (cueIndex >= 0) cues[cueIndex].text else ""
                    subtitleOverlay.visibility = if (cueIndex >= 0) android.view.View.VISIBLE else android.view.View.GONE
                    if (cueIndex >= 0) runPreviewSubtitleAnimation()
                }
            } else if (::subtitleOverlay.isInitialized && subtitleOverlay.visibility != android.view.View.GONE) {
                lastPreviewCueIndex = -1
                subtitleOverlay.visibility = android.view.View.GONE
            }
            previewHandler.postDelayed(this, 200)
        }
    }
    private lateinit var subtitleInput: EditText
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var cueList: LinearLayout
    private lateinit var addCueButton: Button
    private var editingCueIndex: Int? = null
    private var lastPreviewCueIndex: Int = Int.MIN_VALUE
    private var selectedMediaUri: Uri? = null
    private var autoLanguage = "fa"
    private var transcriptionJob: Job? = null
    private lateinit var languageSpinner: Spinner
    private lateinit var modelQualitySpinner: Spinner

    companion object {
        private const val REQUEST_PICK_MEDIA = 100
        private const val REQUEST_EXPORT_SRT = 101
        private const val REQUEST_IMPORT_SRT = 102
        private const val REQUEST_EXPORT_MP4 = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = 0xFF0B1020.toInt()
        window.navigationBarColor = 0xFF0B1020.toInt()
        window.decorView.systemUiVisibility = 0

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(0xFF0B1020.toInt())
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0B1020.toInt())
            isFillViewport = true
            addView(content)
        }

        content.addView(TextView(this).apply {
            text = "زیرنویس‌یار"
            textSize = 30f
            setTextColor(0xFFF4F7FF.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3))
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "ویرایش و خروجی زیرنویس · کاملاً روی دستگاه"
            textSize = 14f
            setTextColor(0xFF9EABC7.toInt())
            setPadding(0, 0, 0, dp(18))
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "●  بدون API  ·  فایل‌ها روی گوشی می‌مانند"
            textSize = 12f
            setTextColor(0xFF9DE8D3.toInt())
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(0xFF132A30.toInt(), 0xFF28514F.toInt(), 14)
        }, matchWidth())

        content.addView(makeButton("انتخاب ویدئو یا فایل صوتی") { pickMedia() }, matchWidth())
        selectedFileLabel = TextView(this).apply {
            text = "هنوز فایلی انتخاب نشده است"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(14))
        }
        content.addView(selectedFileLabel, matchWidth())

        previewFrame = FrameLayout(this).apply {
            background = roundedBackground(0xFF121A2B.toInt(), 0xFF283752.toInt(), 20)
            clipToOutline = true
        }
        videoPreview = VideoView(this).apply {
            visibility = android.view.View.GONE
            setBackgroundColor(0xFF080B12.toInt())
            val controller = MediaController(this@MainActivity)
            setMediaController(controller)
            controller.setAnchorView(this)
        }
        previewFrame.addView(videoPreview, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
        ))
        subtitleOverlay = SubtitlePreviewView(this).apply {
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dp(10), dp(5), dp(10), dp(5))
            maxWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt()
            visibility = android.view.View.GONE
        }
        previewFrame.addView(subtitleOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        content.addView(previewFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
        ).apply { bottomMargin = dp(12) })
        content.addView(TextView(this).apply {
            text = "ظاهر و استایل زیرنویس"
            textSize = 19f
            setPadding(0, dp(8), 0, dp(6))
        }, matchWidth())
        fun addSettingLabel(label: String) {
            content.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(0, dp(5), 0, dp(2))
            }, matchWidth())
        }
        fun makeSpinner(items: Array<String>): Spinner {
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            }
        }
        addSettingLabel("نوع فونت")
        fontSpinner = makeSpinner(fontNames)
        content.addView(fontSpinner, matchWidth())
        addSettingLabel("انیمیشن ورود زیرنویس")
        animationSpinner = makeSpinner(animationNames)
        content.addView(animationSpinner, matchWidth())
        fontSamplePreview = TextView(this).apply {
            text = "سلام دنیا · Hello World 123"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF172033.toInt())
            setPadding(dp(14), dp(18), dp(14), dp(18))
            contentDescription = "پیش‌نمایش فونت فارسی و انگلیسی"
        }
        val fontSampleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fontSampleParams.topMargin = dp(8)
        content.addView(fontSamplePreview, fontSampleParams)
        content.addView(makeButton("آزمایش انیمیشن انتخاب‌شده") {
            fontSamplePreview.animate().cancel()
            fontSamplePreview.alpha = 1f
            fontSamplePreview.translationY = 0f
            fontSamplePreview.scaleX = 1f
            fontSamplePreview.scaleY = 1f
            runPreviewSubtitleAnimation(fontSamplePreview)
        }, matchWidth())
        addSettingLabel("رنگ متن")
        textColorSpinner = makeSpinner(textColorNames)
        content.addView(textColorSpinner, matchWidth())
        addSettingLabel("رنگ حاشیه")
        outlineColorSpinner = makeSpinner(outlineColorNames)
        content.addView(outlineColorSpinner, matchWidth())
        addSettingLabel("رنگ پس‌زمینه")
        boxColorSpinner = makeSpinner(boxColorNames)
        content.addView(boxColorSpinner, matchWidth())
        fun makeSlider(label: String, maxValue: Int, initial: Int): SeekBar {
            addSettingLabel(label)
            return SeekBar(this).apply {
                max = maxValue
                progress = initial
            }.also { content.addView(it, matchWidth()) }
        }
        sizeSeekBar = makeSlider("اندازهٔ فونت (۱۴ تا ۵۶)", 42, 5)
        positionSeekBar = makeSlider("جای زیرنویس (بالا تا پایین)", 100, 88)
        outlineSeekBar = makeSlider("ضخامت حاشیه", 10, 2)
        opacitySeekBar = makeSlider("شفافیت پس‌زمینه", 100, 80)
        boldCheckBox = CheckBox(this).apply { text = "فونت ضخیم"; isChecked = true }
        shadowCheckBox = CheckBox(this).apply { text = "سایهٔ متن"; isChecked = true }
        boxCheckBox = CheckBox(this).apply { text = "نمایش کادر پس‌زمینه"; isChecked = true }
        content.addView(boldCheckBox, matchWidth())
        content.addView(shadowCheckBox, matchWidth())
        content.addView(boxCheckBox, matchWidth())

        content.addView(TextView(this).apply {
            text = "حریم خصوصی و حالت آفلاین"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(4))
        }, matchWidth())
        content.addView(TextView(this).apply {
            text = "ویرایش، پیش‌نمایش و خروجی روی گوشی انجام می‌شود و هیچ فایلی برای تبدیل به سرویس آنلاین فرستاده نمی‌شود. تشخیص خودکار گفتار با مدل آفلاین هنوز در دست توسعه است."
            textSize = 12f
            setPadding(0, dp(2), 0, dp(12))
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "زیرنویس خودکار آفلاین"
            textSize = 19f
            setTextColor(0xFFF4F7FF.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        }, matchWidth())
        content.addView(TextView(this).apply {
            text = "برای سرعت مدل پایه را انتخاب کن؛ برای دقت بهتر مدل کوچک را امتحان کن. مدل انتخابی فقط یک‌بار دانلود می‌شود و پس از آن گفتار روی گوشی و بدون API پردازش می‌شود."
            textSize = 12f
            setTextColor(0xFF9EABC7.toInt())
            setPadding(0, 0, 0, dp(8))
        }, matchWidth())
        languageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("فارسی", "English", "تشخیص خودکار زبان"))
            setSelection(0)
        }
        content.addView(languageSpinner, matchWidth())
        addSettingLabel("کیفیت موتور تشخیص گفتار")
        modelQualitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("پایه · سریع‌تر · حدود 142 مگابایت", "کوچک · دقت بالاتر · حدود 466 مگابایت"))
            setSelection(0)
        }
        content.addView(modelQualitySpinner, matchWidth())
        content.addView(makeButton("ساخت زیرنویس خودکار از فایل انتخاب‌شده") {
            startLocalTranscription()
        }, matchWidth())

        content.addView(makeButton("حریم خصوصی و نحوهٔ پردازش فایل‌ها") { showPrivacyNotice() }, matchWidth())

        content.addView(TextView(this).apply {
            text = "ویرایش یک زیرنویس"
            textSize = 16f
        }, matchWidth())
        subtitleInput = EditText(this).apply {
            hint = "متن فارسی یا انگلیسی را وارد کن"
            setTextColor(0xFFF4F7FF.toInt())
            setHintTextColor(0xFF7887A5.toInt())
            background = roundedBackground(0xFF121A2B.toInt(), 0xFF33425F.toInt(), 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minLines = 2
            gravity = Gravity.TOP
            setSingleLine(false)
        }
        content.addView(subtitleInput, matchWidth())

        content.addView(TextView(this).apply {
            text = "زمان شروع (ثانیه)"
            textSize = 14f
        }, matchWidth())
        startInput = EditText(this).apply {
            hint = "مثلاً 0 یا 2.5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
        }
        content.addView(startInput, matchWidth())

        content.addView(TextView(this).apply {
            text = "زمان پایان (ثانیه)"
            textSize = 14f
        }, matchWidth())
        endInput = EditText(this).apply {
            hint = "مثلاً 3.5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("3")
        }
        content.addView(endInput, matchWidth())

        val timeAssistRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        timeAssistRow.addView(makeButton("شروع از زمان ویدئو") { setCueTimeFromVideo(true) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        timeAssistRow.addView(makeButton("پایان از زمان ویدئو") { setCueTimeFromVideo(false) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        content.addView(timeAssistRow, matchWidth())
        content.addView(TextView(this).apply {
            text = "برای ثبت دقیق‌تر زمان، ویدئو را پخش یا روی نقطهٔ دلخواه مکث کن و دکمهٔ شروع یا پایان را بزن."
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }, matchWidth())

        addCueButton = makeButton("افزودن زیرنویس") { addCue() }
        content.addView(addCueButton, matchWidth())
        content.addView(makeButton("وارد کردن فایل زیرنویس SRT") { importSrtFile() }, matchWidth())
        content.addView(makeButton("خروجی گرفتن فایل SRT") { exportSrt() }, matchWidth())
        content.addView(makeButton("ساخت ویدئوی MP4 با زیرنویس چسبیده") { requestBurnInExport() }, matchWidth())

        content.addView(TextView(this).apply {
            text = "خطوط زیرنویس در تایم‌لاین"
            textSize = 18f
            setPadding(0, dp(18), 0, dp(8))
        }, matchWidth())
        cueList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        content.addView(cueList, matchWidth())

        content.addView(TextView(this).apply {
            text = "خروجی MP4 با زیرنویس چسبیده، ویدئو را دوباره پردازش می‌کند و ممکن است بسته به طول و کیفیت ویدئو زمان‌بر باشد. فایل اصلی تغییر نمی‌کند."
            textSize = 13f
            setPadding(0, dp(16), 0, dp(8))
        }, matchWidth())

        val prefs = getSharedPreferences("subtitle_style", MODE_PRIVATE)
        subtitleSizeSp = prefs.getInt("size", 19).coerceIn(14, 56)
        subtitlePosition = prefs.getInt("position", 88).coerceIn(0, 100)
        outlineWidth = prefs.getInt("outline", 2).coerceIn(0, 10)
        backgroundOpacity = prefs.getInt("opacity", 80).coerceIn(0, 100)
        fontIndex = prefs.getInt("font", 0).coerceIn(fontNames.indices)
        animationIndex = prefs.getInt("animation", 1).coerceIn(animationNames.indices)
        textColorIndex = prefs.getInt("textColor", 0).coerceIn(textColors.indices)
        outlineColorIndex = prefs.getInt("outlineColor", 0).coerceIn(outlineColors.indices)
        boxColorIndex = prefs.getInt("boxColor", 0).coerceIn(boxColors.indices)
        isBold = prefs.getBoolean("bold", true)
        shadowEnabled = prefs.getBoolean("shadow", true)
        boxEnabled = prefs.getBoolean("box", true)
        fontSpinner.setSelection(fontIndex)
        animationSpinner.setSelection(animationIndex)
        textColorSpinner.setSelection(textColorIndex)
        outlineColorSpinner.setSelection(outlineColorIndex)
        boxColorSpinner.setSelection(boxColorIndex)
        sizeSeekBar.progress = subtitleSizeSp - 14
        positionSeekBar.progress = subtitlePosition
        outlineSeekBar.progress = outlineWidth
        opacitySeekBar.progress = backgroundOpacity
        boldCheckBox.isChecked = isBold
        shadowCheckBox.isChecked = shadowEnabled
        boxCheckBox.isChecked = boxEnabled
        val styleChanged = {
            subtitleSizeSp = sizeSeekBar.progress + 14
            subtitlePosition = positionSeekBar.progress
            outlineWidth = outlineSeekBar.progress
            backgroundOpacity = opacitySeekBar.progress
            fontIndex = fontSpinner.selectedItemPosition.coerceAtLeast(0)
            animationIndex = animationSpinner.selectedItemPosition.coerceIn(animationNames.indices)
            textColorIndex = textColorSpinner.selectedItemPosition.coerceAtLeast(0)
            outlineColorIndex = outlineColorSpinner.selectedItemPosition.coerceAtLeast(0)
            boxColorIndex = boxColorSpinner.selectedItemPosition.coerceAtLeast(0)
            isBold = boldCheckBox.isChecked
            shadowEnabled = shadowCheckBox.isChecked
            boxEnabled = boxCheckBox.isChecked
            prefs.edit().putInt("size", subtitleSizeSp).putInt("position", subtitlePosition)
                .putInt("outline", outlineWidth).putInt("opacity", backgroundOpacity)
                .putInt("font", fontIndex).putInt("animation", animationIndex).putInt("textColor", textColorIndex)
                .putInt("outlineColor", outlineColorIndex).putInt("boxColor", boxColorIndex)
                .putBoolean("bold", isBold).putBoolean("shadow", shadowEnabled)
                .putBoolean("box", boxEnabled).apply()
            applySubtitleStyle()
        }
        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { styleChanged() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        sizeSeekBar.setOnSeekBarChangeListener(sliderListener)
        positionSeekBar.setOnSeekBarChangeListener(sliderListener)
        outlineSeekBar.setOnSeekBarChangeListener(sliderListener)
        opacitySeekBar.setOnSeekBarChangeListener(sliderListener)
        val spinnerListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { styleChanged() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        fontSpinner.onItemSelectedListener = spinnerListener
        animationSpinner.onItemSelectedListener = spinnerListener
        textColorSpinner.onItemSelectedListener = spinnerListener
        outlineColorSpinner.onItemSelectedListener = spinnerListener
        boxColorSpinner.onItemSelectedListener = spinnerListener
        val checkListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> styleChanged() }
        boldCheckBox.setOnCheckedChangeListener(checkListener)
        shadowCheckBox.setOnCheckedChangeListener(checkListener)
        boxCheckBox.setOnCheckedChangeListener(checkListener)
        setContentView(scroll)
        applySubtitleStyle()
        previewHandler.post(previewTicker)
    }

    private fun showPrivacyNotice() {
        android.app.AlertDialog.Builder(this)
            .setTitle("حریم خصوصی و داده‌ها")
            .setMessage(
                "این نسخه برای ساخت و ویرایش زیرنویس، پیش‌نمایش و خروجی‌گرفتن به API یا سرور نیاز ندارد. " +
                "فایل‌های انتخاب‌شده برای پردازش زیرنویس به سرویس آنلاین ارسال نمی‌شوند. " +
                "تشخیص خودکار گفتار با مدل محلی هنوز در این نسخه فعال نیست. " +
                "فایل اصلی تا زمانی که خودت خروجی نگیری تغییر نمی‌کند. " +
                "این نسخه هنوز به سیاست حریم خصوصی عمومیِ میزبانی‌شده و اطلاعات تماس توسعه‌دهنده برای انتشار نهایی نیاز دارد."
            )
            .setNeutralButton("سیاست حریم خصوصی کامل") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Javadmk1/Repository/blob/main/PRIVACY_POLICY.md")))
                } catch (_: Exception) {
                    toast("برای مشاهدهٔ سیاست حریم خصوصی، مخزن عمومی پروژه را باز کن")
                }
            }
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    private inner class SubtitlePreviewView(context: android.content.Context) : TextView(context) {
        override fun onDraw(canvas: Canvas) {
            background?.draw(canvas)
            val textLayout = this.layout ?: return
            canvas.save()
            canvas.translate(totalPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
            val p = paint
            val oldStyle = p.style
            val oldColor = p.color
            val oldStroke = p.strokeWidth
            p.clearShadowLayer()
            if (outlineWidth > 0) {
                p.style = Paint.Style.STROKE
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = dp(outlineWidth).toFloat()
                p.color = outlineColors[outlineColorIndex.coerceIn(outlineColors.indices)]
                textLayout.draw(canvas)
            }
            p.style = Paint.Style.FILL
            p.color = currentTextColor
            if (shadowEnabled) p.setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
            textLayout.draw(canvas)
            p.clearShadowLayer()
            p.style = oldStyle
            p.color = oldColor
            p.strokeWidth = oldStroke
            canvas.restore()
        }
    }

    private fun findActiveCueIndex(items: List<Cue>, timeMs: Long): Int =
        SubtitleUtils.findActiveCueIndex(items, timeMs)

    private fun runPreviewSubtitleAnimation(view: android.view.View = subtitleOverlay) {
        if (animationIndex == 0) return
        view.animate().cancel()
        view.alpha = if (animationIndex == 1) 0f else 1f
        view.translationY = if (animationIndex == 2) dp(18).toFloat() else 0f
        view.scaleX = if (animationIndex == 3) 0.88f else 1f
        view.scaleY = if (animationIndex == 3) 0.88f else 1f
        val animator = view.animate().setDuration(320L).alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
        animator.start()
    }

    private fun subtitleTypeface(index: Int, bold: Boolean): Typeface {
        val safeIndex = index.coerceIn(fontNames.indices)
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        if (safeIndex == fontNames.lastIndex) {
            if (!bundledVazirmatnLoaded) {
                bundledVazirmatnLoaded = true
                bundledVazirmatnTypeface = runCatching {
                    Typeface.createFromAsset(assets, "fonts/Vazirmatn-Regular.ttf")
                }.getOrNull()
            }
            val base = bundledVazirmatnTypeface ?: Typeface.create("sans-serif", Typeface.NORMAL)
            return if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(base, style) else if (bold) Typeface.create("sans-serif", Typeface.BOLD) else base
        }
        return Typeface.create(fontFamilies[safeIndex], style)
    }

    private fun applySubtitleStyle() {
        if (!::subtitleOverlay.isInitialized) return
        subtitleOverlay.textSize = subtitleSizeSp.toFloat()
        subtitleOverlay.typeface = subtitleTypeface(fontIndex, isBold)
        if (::fontSamplePreview.isInitialized) {
            fontSamplePreview.typeface = subtitleTypeface(fontIndex, isBold)
            fontSamplePreview.setTextColor(textColors[textColorIndex.coerceIn(textColors.indices)])
        }
        subtitleOverlay.setTextColor(textColors[textColorIndex.coerceIn(textColors.indices)])
        val alpha = (backgroundOpacity * 255 / 100).coerceIn(0, 255)
        val baseBox = boxColors[boxColorIndex.coerceIn(boxColors.indices)]
        subtitleOverlay.setBackgroundColor(if (boxEnabled && baseBox != Color.TRANSPARENT) Color.argb(alpha, Color.red(baseBox), Color.green(baseBox), Color.blue(baseBox)) else Color.TRANSPARENT)
        subtitleOverlay.setShadowLayer(
            if (shadowEnabled) dp(3).toFloat() else outlineWidth.toFloat(),
            0f, if (shadowEnabled) dp(2).toFloat() else 0f,
            if (shadowEnabled) Color.BLACK else outlineColors[outlineColorIndex.coerceIn(outlineColors.indices)]
        )
        subtitleOverlay.setPadding(dp(10), dp(5), dp(10), dp(5))
        if (::previewFrame.isInitialized && subtitleOverlay.layoutParams is FrameLayout.LayoutParams) {
            subtitleOverlay.post {
                val params = subtitleOverlay.layoutParams as FrameLayout.LayoutParams
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = ((previewFrame.height - subtitleOverlay.height).coerceAtLeast(0) * subtitlePosition / 100)
                previewFrame.updateViewLayout(subtitleOverlay, params)
            }
        }
    }

    override fun onDestroy() {
        previewHandler.removeCallbacks(previewTicker)
        if (::videoPreview.isInitialized) videoPreview.stopPlayback()
        super.onDestroy()
    }

    private fun pickMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
        }
        startActivityForResult(intent, REQUEST_PICK_MEDIA)
    }

    @Deprecated("Uses the compatible activity result callback for a dependency-free starter app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        when (requestCode) {
            REQUEST_PICK_MEDIA -> {
                selectedMediaUri = data.data
                lastPreviewCueIndex = Int.MIN_VALUE
                try {
                    contentResolver.takePersistableUriPermission(
                        data.data!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some document providers do not offer persistable URI permissions.
                }
                selectedFileLabel.text = "فایل انتخاب‌شده: ${data.data!!.lastPathSegment ?: "فایل محلی"}"
                val selectedUri = data.data!!
                val selectedMime = contentResolver.getType(selectedUri) ?: ""
                if (selectedMime.startsWith("video/")) {
                    videoPreview.visibility = android.view.View.VISIBLE
                    subtitleOverlay.visibility = android.view.View.GONE
                    videoPreview.setVideoURI(selectedUri)
                } else {
                    videoPreview.stopPlayback()
                    videoPreview.visibility = android.view.View.GONE
                    subtitleOverlay.visibility = android.view.View.GONE
                }
                toast(if (selectedMime.startsWith("video/")) "ویدئو انتخاب شد؛ برای پخش از کنترل‌های ویدئو استفاده کن" else "فایل صوتی انتخاب شد")
            }
            REQUEST_EXPORT_SRT -> {
                data.data?.let { uri -> writeSrt(uri) }
            }
            REQUEST_IMPORT_SRT -> {
                data.data?.let { uri -> readSrt(uri) }
            }
            REQUEST_EXPORT_MP4 -> {
                data.data?.let { uri -> exportVideoWithBurnedSubtitles(uri) }
            }
        }
    }




    private fun startLocalTranscription() {
        val uri = selectedMediaUri
        if (uri == null) {
            toast("ابتدا فایل صوتی یا ویدئو را انتخاب کن")
            return
        }
        if (transcriptionJob?.isActive == true) {
            toast("تبدیل در حال انجام است")
            return
        }
        autoLanguage = when (languageSpinner.selectedItemPosition) {
            1 -> "en"
            2 -> "auto"
            else -> "fa"
        }
        val modelName = if (modelQualitySpinner.selectedItemPosition == 1) "ggml-small.bin" else "ggml-base.bin"
        val progress = android.app.ProgressDialog(this).apply {
            setTitle("زیرنویس خودکار")
            setMessage("در حال آماده‌سازی موتور آفلاین…")
            setCancelable(false)
            show()
        }
        transcriptionJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                val resultCues = withContext(Dispatchers.IO) {
                    val minimumModelBytes = if (modelName == "ggml-small.bin") 300_000_000L else 100_000_000L
                    val modelFile = File(filesDir, "models/$modelName")
                    if (!modelFile.exists() || modelFile.length() < minimumModelBytes) {
                        modelFile.parentFile?.mkdirs()
                        val partial = File(modelFile.parentFile, "$modelName.download")
                        val connection = (URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$modelName").openConnection() as HttpURLConnection).apply {
                            connectTimeout = 20000
                            readTimeout = 60000
                            instanceFollowRedirects = true
                        }
                        try {
                            connection.connect()
                            if (connection.responseCode !in 200..299) throw IllegalStateException("دانلود مدل ناموفق بود: HTTP ${connection.responseCode}")
                            BufferedInputStream(connection.inputStream).use { input ->
                                FileOutputStream(partial).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            if (partial.length() < minimumModelBytes) throw IllegalStateException("فایل مدل ناقص دانلود شد؛ فضای ذخیره‌سازی و اینترنت را بررسی کن")
                            if (!partial.renameTo(modelFile)) throw IllegalStateException("ذخیره مدل ناموفق بود")
                        } finally {
                            connection.disconnect()
                            partial.delete()
                        }
                    }
                    val mime = contentResolver.getType(uri) ?: ""
                    val audioFile = if (mime.startsWith("video/")) {
                        extractAudioToWav(uri)
                    } else {
                        copySelectedAudioToCache(uri)
                    }
                    try {
                        val model = Whisper.loadModel(this@MainActivity, modelFile.absolutePath)
                        try {
                            val cfg = WhisperConfig(language = autoLanguage)
                            val result = Whisper.transcribe(model, audioFile.absolutePath, cfg)
                            result.segments.mapNotNull { segment ->
                                val text = segment.text.trim()
                                if (text.isBlank() || segment.endMs <= segment.startMs) null
                                else Cue(segment.startMs, segment.endMs, text)
                            }
                        } finally {
                            Whisper.releaseModel(model)
                        }
                    } finally {
                        audioFile.delete()
                    }
                }
                if (resultCues.isEmpty()) {
                    toast("گفتار قابل تشخیصی پیدا نشد؛ زبان یا کیفیت صدا را بررسی کن")
                } else {
                    cues.clear()
                    cues.addAll(resultCues)
                    editingCueIndex = null
                    refreshCueList()
                    toast("${resultCues.size} خط زیرنویس ساخته شد؛ متن و زمان‌ها را بازبینی کن")
                }
            } catch (error: Exception) {
                toast("تبدیل انجام نشد: ${error.message ?: "خطای ناشناخته"}")
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun copySelectedAudioToCache(uri: Uri): File {
        val name = getDisplayName(uri).lowercase(java.util.Locale.ROOT)
        val ext = when {
            name.endsWith(".wav") -> ".wav"
            name.endsWith(".mp3") -> ".mp3"
            name.endsWith(".flac") -> ".flac"
            else -> throw IllegalStateException("برای تشخیص آفلاین، فعلاً فایل صوتی WAV، MP3 یا FLAC انتخاب کن")
        }
        val target = File(cacheDir, "asr_input_${System.currentTimeMillis()}$ext")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("خواندن فایل انتخاب‌شده ممکن نشد")
        return target
    }

    private fun extractAudioToWav(uri: Uri): File {
        val extractor = MediaExtractor()
        val raw = File(cacheDir, "asr_pcm_" + System.currentTimeMillis() + ".raw")
        val wav = File(cacheDir, "asr_audio_" + System.currentTimeMillis() + ".wav")
        var decoder: android.media.MediaCodec? = null
        var sampleRate = 16000
        var channels = 1
        var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        try {
            extractor.setDataSource(this, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) throw IllegalStateException("در این ویدئو صدایی پیدا نشد")
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("کدک صوتی ناشناخته است")
            extractor.selectTrack(audioTrack)
            decoder = android.media.MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            var inputDone = false
            var outputDone = false
            val info = android.media.MediaCodec.BufferInfo()
            FileOutputStream(raw).use { rawOut ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                            inputBuffer.clear()
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = decoder.dequeueOutputBuffer(info, 10000)) {
                        android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = decoder.outputFormat
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        android.media.MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && info.size > 0) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                outputBuffer.get(bytes)
                                when (pcmEncoding) {
                                    android.media.AudioFormat.ENCODING_PCM_16BIT -> rawOut.write(bytes)
                                    android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                                        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder())
                                        while (bb.remaining() >= 4) {
                                            val sample = bb.float.coerceIn(-1f, 1f)
                                            val pcm = (sample * 32767f).toInt().toShort()
                                            rawOut.write(pcm.toInt() and 0xFF)
                                            rawOut.write((pcm.toInt() shr 8) and 0xFF)
                                        }
                                    }
                                    else -> throw IllegalStateException("فرمت صدای PCM پشتیبانی نمی‌شود")
                                }
                            }
                            outputDone = (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            val pcmBytes = raw.length()
            if (pcmBytes <= 0L) throw IllegalStateException("صدای قابل پردازشی استخراج نشد")
            FileOutputStream(wav).use { out ->
                val byteRate = sampleRate * channels * 2
                out.write("RIFF".toByteArray(Charsets.US_ASCII))
                writeLeInt(out, (36L + pcmBytes).toInt())
                out.write("WAVE".toByteArray(Charsets.US_ASCII))
                out.write("fmt ".toByteArray(Charsets.US_ASCII))
                writeLeInt(out, 16)
                writeLeShort(out, 1)
                writeLeShort(out, channels)
                writeLeInt(out, sampleRate)
                writeLeInt(out, byteRate)
                writeLeShort(out, channels * 2)
                writeLeShort(out, 16)
                out.write("data".toByteArray(Charsets.US_ASCII))
                writeLeInt(out, pcmBytes.toInt())
                raw.inputStream().use { it.copyTo(out) }
            }
            return wav
        } catch (e: Exception) {
            wav.delete()
            throw IllegalStateException("آماده‌سازی صدای ویدئو ناموفق بود: " + (e.message ?: "فرمت پشتیبانی‌نشده"))
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor.release()
            raw.delete()
        }
    }

    private fun writeLeInt(out: java.io.OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeLeShort(out: java.io.OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && it.moveToFirst() && !it.isNull(index)) return it.getLong(index)
        }
        return -1L
    }

    private fun getDisplayName(uri: Uri): String {
        var name = "audio_file"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) name = it.getString(index) ?: name
        }
        return name
    }

    private fun setCueTimeFromVideo(isStart: Boolean) {
        if (selectedMediaUri == null || !::videoPreview.isInitialized ||
            videoPreview.visibility != android.view.View.VISIBLE
        ) {
            toast("ابتدا یک ویدئو انتخاب کن")
            return
        }
        val seconds = videoPreview.currentPosition.coerceAtLeast(0) / 1000.0
        val formatted = String.format(java.util.Locale.US, "%.3f", seconds)
        if (isStart) {
            startInput.setText(formatted)
            val end = endInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
            if (end == null || end <= seconds) {
                endInput.setText(String.format(java.util.Locale.US, "%.3f", seconds + 2.0))
            }
            toast("زمان شروع از موقعیت فعلی ویدئو تنظیم شد")
        } else {
            endInput.setText(formatted)
            val start = startInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
            if (start == null || start >= seconds) {
                startInput.setText(String.format(java.util.Locale.US, "%.3f", (seconds - 2.0).coerceAtLeast(0.0)))
            }
            toast("زمان پایان از موقعیت فعلی ویدئو تنظیم شد")
        }
    }

    private fun addCue() {
        val text = subtitleInput.text.toString().trim()
        val startSeconds = startInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val endSeconds = endInput.text.toString().trim().replace(',', '.').toDoubleOrNull()

        if (text.isEmpty()) {
            toast("ابتدا متن زیرنویس را وارد کن")
            return
        }
        if (startSeconds == null || endSeconds == null || startSeconds < 0 || endSeconds <= startSeconds) {
            toast("زمان شروع و پایان را بررسی کن")
            return
        }

        val cue = Cue((startSeconds * 1000).toLong(), (endSeconds * 1000).toLong(), text)
        val editIndex = editingCueIndex
        if (editIndex != null && editIndex in cues.indices) {
            cues[editIndex] = cue
            editingCueIndex = null
            addCueButton.text = "افزودن زیرنویس"
            toast("تغییرات زیرنویس ذخیره شد")
        } else {
            cues.add(cue)
            toast("زیرنویس اضافه شد")
        }
        cues.sortBy { it.startMs }
        subtitleInput.text.clear()
        refreshCueList()
    }

    private fun refreshCueList() {
        lastPreviewCueIndex = Int.MIN_VALUE
        cueList.removeAllViews()
        if (cues.isEmpty()) {
            cueList.addView(TextView(this).apply {
                text = "هنوز زیرنویسی اضافه نشده است."
                textSize = 14f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, matchWidth())
            return
        }

        cues.forEachIndexed { index, cue ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(0xFFF1F3F5.toInt())
            }
            item.addView(TextView(this).apply {
                text = (index + 1).toString() + ". " + formatTime(cue.startMs) + " --> " + formatTime(cue.endMs) + "\n" + cue.text
                textSize = 14f
            }, matchWidth())
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            actions.addView(Button(this).apply {
                text = "پخش از اینجا"
                setOnClickListener {
                    if (selectedMediaUri == null || !::videoPreview.isInitialized ||
                        videoPreview.visibility != android.view.View.VISIBLE
                    ) {
                        toast("ابتدا یک ویدئو انتخاب کن")
                    } else {
                        videoPreview.seekTo(cue.startMs.toInt())
                        videoPreview.start()
                    }
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(Button(this).apply {
                text = "ویرایش"
                setOnClickListener {
                    editingCueIndex = index
                    subtitleInput.setText(cue.text)
                    startInput.setText((cue.startMs / 1000.0).toString())
                    endInput.setText((cue.endMs / 1000.0).toString())
                    addCueButton.text = "ذخیره تغییرات"
                    toast("متن یا زمان را تغییر بده و ذخیره تغییرات را بزن")
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    cues.removeAt(index)
                    if (editingCueIndex == index) {
                        editingCueIndex = null
                        addCueButton.text = "افزودن زیرنویس"
                    } else if (editingCueIndex != null && editingCueIndex!! > index) {
                        editingCueIndex = editingCueIndex!! - 1
                    }
                    refreshCueList()
                    toast("زیرنویس حذف شد")
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            item.addView(actions, matchWidth())
            cueList.addView(item, matchWidth())
            cueList.addView(android.view.View(this).apply {
                setBackgroundColor(0xFFD5D9DE.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }
    private fun importSrtFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-subrip", "text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_SRT)
    }

    private fun readSrt(uri: Uri) {
        Thread {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("فایل زیرنویس قابل خواندن نیست")
                val timestamp = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
                fun millis(match: MatchResult, offset: Int): Long {
                    val h = match.groupValues[offset].toLong()
                    val m = match.groupValues[offset + 1].toLong()
                    val sec = match.groupValues[offset + 2].toLong()
                    val fraction = match.groupValues[offset + 3].padEnd(3, '0').take(3).toLong()
                    return ((h * 3600 + m * 60 + sec) * 1000) + fraction
                }
                val imported = mutableListOf<Cue>()
                raw.removePrefix("\uFEFF").split(Regex("""\r?
\s*\r?
""")).forEach { block ->
                    val lines = block.lines()
                    val timeIndex = lines.indexOfFirst { timestamp.containsMatchIn(it) }
                    if (timeIndex >= 0) {
                        val match = timestamp.find(lines[timeIndex])!!
                        val start = millis(match, 1)
                        val end = millis(match, 5)
                        val text = lines.drop(timeIndex + 1).joinToString("\n").trim()
                        if (text.isNotEmpty() && end > start) imported.add(Cue(start, end, text))
                    }
                }
                runOnUiThread {
                    if (imported.isEmpty()) {
                        toast("هیچ زیرنویس معتبر SRT در فایل پیدا نشد")
                    } else {
                        cues.clear()
                        cues.addAll(imported.sortedBy { it.startMs })
                        editingCueIndex = null
                        addCueButton.text = "افزودن زیرنویس"
                        refreshCueList()
                        toast("تعداد " + imported.size + " بخش زیرنویس وارد شد")
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { toast("خواندن SRT ناموفق بود: " + (error.message ?: "خطای ناشناخته")) }
            }
        }.start()
    }
    private fun exportSrt() {
        if (cues.isEmpty()) {
            toast("برای خروجی گرفتن، ابتدا یک زیرنویس اضافه کن")
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-subrip"
            putExtra(Intent.EXTRA_TITLE, "subtitles.srt")
        }
        startActivityForResult(intent, REQUEST_EXPORT_SRT)
    }

    private fun requestBurnInExport() {
        val uri = selectedMediaUri
        if (uri == null || !(contentResolver.getType(uri) ?: "").startsWith("video/")) {
            toast("ابتدا یک ویدئو انتخاب کن")
            return
        }
        if (cues.isEmpty()) {
            toast("ابتدا زیرنویس بساز یا فایل SRT وارد کن")
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            putExtra(Intent.EXTRA_TITLE, "autosubtitle.mp4")
        }
        startActivityForResult(intent, REQUEST_EXPORT_MP4)
    }

    @OptIn(UnstableApi::class)
    private fun exportVideoWithBurnedSubtitles(destinationUri: Uri) {
        val sourceUri = selectedMediaUri
        if (sourceUri == null || cues.isEmpty()) {
            toast("ویدئو یا زیرنویس در دسترس نیست")
            return
        }
        val exportCues = cues.toList()
        val outputFile = File(cacheDir, "autosubtitle_export_" + System.currentTimeMillis() + ".mp4")
        val exportSizeSp = subtitleSizeSp
        val exportPosition = subtitlePosition
        val exportOutlineWidth = outlineWidth
        val exportOpacity = backgroundOpacity
        val exportFontIndex = fontIndex
        val exportAnimationIndex = animationIndex
        val exportTextColor = textColors[textColorIndex.coerceIn(textColors.indices)]
        val exportOutlineColor = outlineColors[outlineColorIndex.coerceIn(outlineColors.indices)]
        val exportBoxColor = boxColors[boxColorIndex.coerceIn(boxColors.indices)]
        val exportBold = isBold
        val exportShadow = shadowEnabled
        val exportBox = boxEnabled
        toast("پردازش ویدئو شروع شد؛ برنامه را باز نگه دار")
        try {
            val overlay = object : CanvasOverlay(true) {
                private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = exportTextColor
                    typeface = subtitleTypeface(exportFontIndex, exportBold)
                    textAlign = Paint.Align.CENTER
                }
                private val outlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = exportOutlineColor
                    typeface = subtitleTypeface(exportFontIndex, exportBold)
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = exportOutlineWidth * 1.5f
                }
                private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val alpha = (exportOpacity * 255 / 100).coerceIn(0, 255)
                    color = if (exportBox && exportBoxColor != Color.TRANSPARENT) Color.argb(alpha, Color.red(exportBoxColor), Color.green(exportBoxColor), Color.blue(exportBoxColor)) else Color.TRANSPARENT
                    style = Paint.Style.FILL
                }

                private var cachedCueIndex = Int.MIN_VALUE
                private var cachedLayout: StaticLayout? = null
                private var cachedOutlineLayout: StaticLayout? = null
                private var cachedBlockHeight = 0f
                private var cachedHorizontalPadding = 0f
                private var cachedVerticalPadding = 0f
                private var cachedTextWidth = 0

                override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
                    val cueIndex = findActiveCueIndex(exportCues, presentationTimeUs / 1000)
                    if (cueIndex < 0 || exportCues[cueIndex].text.isBlank()) return
                    val active = exportCues[cueIndex]
                    val width = canvas.width.toFloat()
                    val height = canvas.height.toFloat()
                    val maxTextWidth = (width * 0.88f).toInt().coerceAtLeast(1)
                    if (cueIndex != cachedCueIndex || cachedTextWidth != maxTextWidth) {
                        textPaint.textSize = (height * 0.045f * exportSizeSp / 19f).coerceIn(18f, 100f)
                        outlinePaint.textSize = textPaint.textSize
                        if (exportShadow) textPaint.setShadowLayer(height * 0.003f, 0f, height * 0.002f, Color.BLACK)
                        else textPaint.clearShadowLayer()
                        cachedLayout = StaticLayout.Builder.obtain(
                            active.text, 0, active.text.length, textPaint, maxTextWidth
                        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                            .setIncludePad(false)
                            .build()
                        cachedOutlineLayout = StaticLayout.Builder.obtain(
                            active.text, 0, active.text.length, outlinePaint, maxTextWidth
                        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                            .setIncludePad(false)
                            .build()
                        cachedHorizontalPadding = width * 0.025f
                        cachedVerticalPadding = height * 0.012f
                        cachedBlockHeight = (cachedLayout?.height ?: 0) + cachedVerticalPadding * 2
                        cachedTextWidth = maxTextWidth
                        cachedCueIndex = cueIndex
                    }
                    val layout = cachedLayout ?: return
                    val outlineLayout = cachedOutlineLayout ?: return
                    val top = (height - cachedBlockHeight) * exportPosition / 100f
                    val left = (width - layout.width) / 2f - cachedHorizontalPadding
                    val right = (width + layout.width) / 2f + cachedHorizontalPadding
                    val elapsedMs = (presentationTimeUs / 1000L - active.startMs).coerceAtLeast(0L)
                    val progress = (elapsedMs / 320f).coerceIn(0f, 1f)
                    val animationAlpha = if (exportAnimationIndex == 1) (progress * 255f).toInt() else 255
                    textPaint.alpha = animationAlpha
                    outlinePaint.alpha = animationAlpha
                    backgroundPaint.alpha = if (exportBox && exportBoxColor != Color.TRANSPARENT) ((exportOpacity * 255 / 100) * animationAlpha / 255) else 0
                    val slideOffset = if (exportAnimationIndex == 2) height * 0.035f * (1f - progress) else 0f
                    val popScale = if (exportAnimationIndex == 3) 0.88f + 0.12f * progress else 1f
                    canvas.save()
                    canvas.translate(width / 2f, top + cachedBlockHeight / 2f + slideOffset)
                    canvas.scale(popScale, popScale)
                    canvas.translate(-width / 2f, -(top + cachedBlockHeight / 2f))
                    if (exportBox && exportBoxColor != Color.TRANSPARENT) {
                        canvas.drawRoundRect(
                            RectF(left, top, right, top + cachedBlockHeight),
                            height * 0.012f, height * 0.012f, backgroundPaint
                        )
                    }
                    canvas.save()
                    canvas.translate((width - layout.width) / 2f, top + cachedVerticalPadding)
                    if (exportOutlineWidth > 0) outlineLayout.draw(canvas)
                    layout.draw(canvas)
                    canvas.restore()
                    canvas.restore()
                    textPaint.alpha = 255
                    outlinePaint.alpha = 255
                    backgroundPaint.alpha = if (exportBox && exportBoxColor != Color.TRANSPARENT) (exportOpacity * 255 / 100).coerceIn(0, 255) else 0
                }
            }
            val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                .build()
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Thread {
                            try {
                                contentResolver.openOutputStream(destinationUri)?.use { output ->
                                    outputFile.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                                } ?: throw IllegalStateException("محل ذخیره‌سازی قابل نوشتن نیست")
                                runOnUiThread { toast("ویدئوی MP4 با زیرنویس چسبیده ذخیره شد") }
                            } catch (error: Exception) {
                                runOnUiThread { toast("ذخیره خروجی ناموفق بود: " + (error.message ?: "خطای ناشناخته")) }
                            } finally {
                                outputFile.delete()
                            }
                        }.start()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        outputFile.delete()
                        toast("پردازش ویدئو ناموفق بود: " + (exportException.message ?: "فرمت پشتیبانی‌نشده"))
                    }
                })
                .build()
            transformer.start(editedItem, outputFile.absolutePath)
        } catch (error: Exception) {
            outputFile.delete()
            toast("شروع خروجی ناموفق بود: " + (error.message ?: "خطای ناشناخته"))
        }
    }

    private fun writeSrt(uri: Uri) {
        val srt = cues.mapIndexed { index, cue ->
            "${index + 1}\n${formatTime(cue.startMs)} --> ${formatTime(cue.endMs)}\n${cue.text}\n"
        }.joinToString("\n")
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(srt) }
                ?: throw IllegalStateException("Could not open output file")
            toast("فایل SRT ذخیره شد")
        } catch (error: Exception) {
            toast("ذخیره فایل انجام نشد: ${error.localizedMessage ?: "خطای ناشناخته"}")
        }
    }

    private fun formatTime(milliseconds: Long): String = SubtitleUtils.formatTime(milliseconds)

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(0xFFF7FAFF.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(48)
        background = roundedBackground(
            if (label.contains("خروجی") || label.contains("افزودن") || label.contains("انتخاب")) 0xFF6558E8.toInt() else 0xFF1A2740.toInt(),
            if (label.contains("خروجی") || label.contains("افزودن") || label.contains("انتخاب")) 0xFF8B83FF.toInt() else 0xFF344563.toInt(),
            14
        )
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { action() }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
