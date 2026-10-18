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
    private var animationIndex = 1
    private var textColorIndex = 0
    private var outlineColorIndex = 0
    private var boxColorIndex = 0
    private var isBold = true
    private var shadowEnabled = true
    private var boxEnabled = true
    private val fontNames = arrayOf("پیش‌فرض فارسی/انگلیسی", "Sans-serif", "Sans-serif light", "Sans-serif medium", "Sans-serif black", "Serif", "Monospace", "Serif monospace", "Sans-serif condensed", "Casual", "Cursive")
    private val fontFamilies = arrayOf("sans-serif", "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-black", "serif", "monospace", "serif-monospace", "sans-serif-condensed", "casual", "cursive")
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
    private lateinit var apiKeyInput: EditText
    private var selectedMediaUri: Uri? = null

    companion object {
        private const val REQUEST_PICK_MEDIA = 100
        private const val REQUEST_EXPORT_SRT = 101
        private const val REQUEST_IMPORT_SRT = 102
        private const val REQUEST_EXPORT_MP4 = 103
        private const val MAX_TRANSCRIPTION_UPLOAD_BYTES = 24L * 1024L * 1024L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(TextView(this).apply {
            text = "AutoSubtitle"
            textSize = 28f
            gravity = Gravity.CENTER_VERTICAL
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "نسخه 0.3.0 · زیرنویس خودکار، ویرایش زمان‌بندی و خروجی ویدئو"
            textSize = 16f
            setPadding(0, dp(6), 0, dp(16))
        }, matchWidth())

        content.addView(makeButton("انتخاب ویدئو یا فایل صوتی") { pickMedia() }, matchWidth())
        selectedFileLabel = TextView(this).apply {
            text = "هنوز فایلی انتخاب نشده است"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(14))
        }
        content.addView(selectedFileLabel, matchWidth())

        previewFrame = FrameLayout(this)
        videoPreview = VideoView(this).apply {
            visibility = android.view.View.GONE
            setBackgroundColor(0xFF111111.toInt())
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
            text = "تنظیمات ظاهر زیرنویس"
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
            text = "کلید API برای تبدیل خودکار (اختیاری؛ هزینهٔ سرویس جداست)"
            textSize = 14f
        }, matchWidth())
        apiKeyInput = EditText(this).apply {
            hint = "کلید API خودت را وارد کن؛ در برنامه ذخیره نمی‌شود"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        content.addView(apiKeyInput, matchWidth())
        content.addView(makeButton("تبدیل خودکار فایل صوتی به زیرنویس") { transcribeAudioFile() }, matchWidth())
        content.addView(TextView(this).apply {
            text = "تبدیل با سرویس آنلاین Whisper انجام می‌شود. فایل صوتی یا ویدئو انتخاب کن؛ در ویدئو، صدای آن استخراج و ارسال می‌شود. اینترنت، کلید API معتبر و رعایت محدودیت حجم سرویس لازم است."
            textSize = 12f
            setPadding(0, dp(2), 0, dp(12))
        }, matchWidth())

        content.addView(makeButton("حریم خصوصی و نحوهٔ پردازش فایل‌ها") { showPrivacyNotice() }, matchWidth())

        content.addView(TextView(this).apply {
            text = "متن زیرنویس"
            textSize = 16f
        }, matchWidth())
        subtitleInput = EditText(this).apply {
            hint = "متن فارسی یا انگلیسی را وارد کن"
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
            text = "زیرنویس‌های ثبت‌شده"
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
                "فایل‌ها فقط وقتی برای تبدیل خودکار دکمه را می‌زنی به سرویس OpenAI ارسال می‌شوند. " +
                "برای ویدئو، برنامه صدای آن را استخراج می‌کند و همان صدا را می‌فرستد. " +
                "این پردازش آنلاین است و ممکن است هزینهٔ API داشته باشد. " +
                "کلید API عمداً در تنظیمات یا فایل دائمی برنامه ذخیره نمی‌شود؛ پس از شروع درخواست، فیلد کلید پاک می‌شود. " +
                "فایل اصلی برای ساخت زیرنویس تغییر نمی‌کند. فایل‌های موقت استخراج صدا پس از پایان درخواست پاک می‌شوند. " +
                "از ارسال فایل‌های حساس یا متعلق به دیگران بدون اجازه خودداری کن. " +
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

    private fun runPreviewSubtitleAnimation() {
        if (animationIndex == 0) return
        val view = subtitleOverlay
        view.animate().cancel()
        view.alpha = if (animationIndex == 1) 0f else 1f
        view.translationY = if (animationIndex == 2) dp(18).toFloat() else 0f
        view.scaleX = if (animationIndex == 3) 0.88f else 1f
        view.scaleY = if (animationIndex == 3) 0.88f else 1f
        val animator = view.animate().setDuration(320L).alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
        animator.start()
    }

    private fun applySubtitleStyle() {
        if (!::subtitleOverlay.isInitialized) return
        subtitleOverlay.textSize = subtitleSizeSp.toFloat()
        subtitleOverlay.typeface = Typeface.create(fontFamilies[fontIndex.coerceIn(fontFamilies.indices)], if (isBold) Typeface.BOLD else Typeface.NORMAL)
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


    private fun transcribeAudioFile() {
        val uri = selectedMediaUri
        val apiKey = apiKeyInput.text.toString().trim()
        if (uri == null) {
            toast("ابتدا یک فایل صوتی انتخاب کن")
            return
        }
        if (apiKey.isEmpty()) {
            toast("برای تبدیل خودکار، کلید API معتبر وارد کن")
            return
        }
        val mime = contentResolver.getType(uri) ?: ""
        if (!mime.startsWith("audio/") && !mime.startsWith("video/")) {
            toast("این نوع فایل صوتی یا ویدئویی پشتیبانی نمی‌شود")
            return
        }

        toast("تبدیل شروع شد؛ استخراج صدا و ارسال ممکن است زمان ببرد")
        apiKeyInput.text?.clear()
        Thread {
            var connection: HttpURLConnection? = null
            var extractedAudio: File? = null
            try {
                val audioFile = if (mime.startsWith("video/")) {
                    extractedAudio = extractAudioTrack(uri)
                    extractedAudio!!
                } else null
                val uploadSize = audioFile?.length() ?: getFileSize(uri)
                if (uploadSize > MAX_TRANSCRIPTION_UPLOAD_BYTES) {
                    throw IllegalStateException("حجم فایل برای ارسال زیاد است؛ فایل صوتی باید کمتر از ۲۴ مگابایت باشد")
                }
                val boundary = "----AutoSubtitleBoundary" + System.currentTimeMillis()
                connection = (URL("https://api.openai.com/v1/audio/transcriptions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000
                    readTimeout = 180000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setChunkedStreamingMode(64 * 1024)
                }

                connection.outputStream.use { out ->
                    fun field(name: String, value: String) {
                        out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.write(value.toByteArray(Charsets.UTF_8))
                        out.write("\r\n".toByteArray(Charsets.UTF_8))
                    }
                    field("model", "whisper-1")
                    field("response_format", "verbose_json")
                    field("timestamp_granularities[]", "segment")
                    val filename = if (audioFile != null) "extracted_audio.m4a" else getDisplayName(uri).replace("\"", "_")
                    val uploadMime = if (audioFile != null) "audio/mp4" else mime
                    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Type: $uploadMime\r\n\r\n".toByteArray(Charsets.UTF_8))
                    if (audioFile != null) {
                        audioFile.inputStream().use { it.copyTo(out, 64 * 1024) }
                    } else {
                        contentResolver.openInputStream(uri)?.use { input ->
                            input.copyTo(out, 64 * 1024)
                        } ?: throw IllegalStateException("فایل قابل خواندن نیست")
                    }
                    out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val message = try { JSONObject(response).optJSONObject("error")?.optString("message") ?: response }
                    catch (_: Exception) { response }
                    throw IllegalStateException("خطای سرویس ($status): $message")
                }

                val json = JSONObject(response)
                val segments = json.optJSONArray("segments")
                val newCues = mutableListOf<Cue>()
                if (segments != null) {
                    for (i in 0 until segments.length()) {
                        val segment = segments.optJSONObject(i) ?: continue
                        val text = segment.optString("text").trim()
                        val start = (segment.optDouble("start", 0.0) * 1000).toLong().coerceAtLeast(0)
                        val end = (segment.optDouble("end", 0.0) * 1000).toLong()
                        if (text.isNotEmpty() && end > start) newCues.add(Cue(start, end, text))
                    }
                }
                if (newCues.isEmpty()) {
                    val text = json.optString("text").trim()
                    if (text.isNotEmpty()) newCues.add(Cue(0, 5000, text))
                }
                runOnUiThread {
                    if (newCues.isEmpty()) {
                        toast("متنی از فایل تشخیص داده نشد")
                    } else {
                        cues.clear()
                        cues.addAll(newCues)
                        refreshCueList()
                        toast("تبدیل تمام شد: " + newCues.size + " بخش زیرنویس")
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    toast(error.message?.take(220) ?: "تبدیل انجام نشد؛ اتصال و کلید API را بررسی کن")
                }
            } finally {
                connection?.disconnect()
                extractedAudio?.delete()
            }
        }.start()
    }

    private fun extractAudioTrack(uri: Uri): File {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val output = File(cacheDir, "autosub_audio_" + System.currentTimeMillis() + ".m4a")
        try {
            extractor.setDataSource(this, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (trackMime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) {
                throw IllegalStateException("در این ویدئو ترک صوتی پیدا نشد")
            }
            extractor.selectTrack(audioTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(audioFormat)
            muxer.start()
            val buffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                // Translate MediaExtractor flags to the MediaCodec flag set.
                info.flags = 0
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    info.flags = info.flags or android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                    info.flags = info.flags or android.media.MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                muxer.writeSampleData(outputTrack, buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop()
            return output
        } catch (e: Exception) {
            output.delete()
            throw IllegalStateException("استخراج صدا ناموفق بود: " + (e.message ?: "فرمت پشتیبانی‌نشده"))
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
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
                raw.removePrefix("\uFEFF").split(Regex("""\r?\n\s*\r?\n""")).forEach { block ->
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
                    typeface = Typeface.create(fontFamilies[exportFontIndex.coerceIn(fontFamilies.indices)], if (exportBold) Typeface.BOLD else Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                private val outlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = exportOutlineColor
                    typeface = Typeface.create(fontFamilies[exportFontIndex.coerceIn(fontFamilies.indices)], if (exportBold) Typeface.BOLD else Typeface.NORMAL)
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
        setOnClickListener { action() }
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
