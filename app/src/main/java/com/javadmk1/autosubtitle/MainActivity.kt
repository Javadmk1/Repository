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
import androidx.media3.common.Effects
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
    private data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val cues = mutableListOf<Cue>()
    private lateinit var selectedFileLabel: TextView
    private lateinit var videoPreview: VideoView
    private lateinit var subtitleOverlay: TextView
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewTicker = object : Runnable {
        override fun run() {
            if (::videoPreview.isInitialized && ::subtitleOverlay.isInitialized &&
                videoPreview.visibility == android.view.View.VISIBLE && videoPreview.isPlaying
            ) {
                val position = videoPreview.currentPosition.toLong()
                val activeCue = cues.firstOrNull { position >= it.startMs && position < it.endMs }
                subtitleOverlay.text = activeCue?.text.orEmpty()
                subtitleOverlay.visibility = if (activeCue == null) android.view.View.GONE else android.view.View.VISIBLE
            } else if (::subtitleOverlay.isInitialized) {
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
    private lateinit var apiKeyInput: EditText
    private var selectedMediaUri: Uri? = null

    companion object {
        private const val REQUEST_PICK_MEDIA = 100
        private const val REQUEST_EXPORT_SRT = 101
        private const val REQUEST_IMPORT_SRT = 102
        private const val REQUEST_EXPORT_MP4 = 103
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
            text = "نسخهٔ اولیه: انتخاب فایل و ساخت زیرنویس SRT"
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

        val previewFrame = FrameLayout(this)
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
        subtitleOverlay = TextView(this).apply {
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = android.view.View.GONE
        }
        previewFrame.addView(subtitleOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(dp(10), 0, dp(10), dp(18)) })
        content.addView(previewFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
        ).apply { bottomMargin = dp(12) })

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

        setContentView(scroll)
        previewHandler.post(previewTicker)
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
        Thread {
            var connection: HttpURLConnection? = null
            var extractedAudio: File? = null
            try {
                val audioFile = if (mime.startsWith("video/")) {
                    extractedAudio = extractAudioTrack(uri)
                    extractedAudio!!
                } else null
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
                info.flags = extractor.sampleFlags
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

    private fun getDisplayName(uri: Uri): String {
        var name = "audio_file"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) name = it.getString(index) ?: name
        }
        return name
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
        toast("پردازش ویدئو شروع شد؛ برنامه را باز نگه دار")
        try {
            val overlay = object : CanvasOverlay(true) {
                private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCC000000.toInt()
                    style = Paint.Style.FILL
                }

                override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
                    val active = exportCues.firstOrNull {
                        presentationTimeUs / 1000 >= it.startMs && presentationTimeUs / 1000 < it.endMs
                    }
                    if (active == null || active.text.isBlank()) return
                    val width = canvas.width.toFloat()
                    val height = canvas.height.toFloat()
                    val maxTextWidth = (width * 0.88f).toInt().coerceAtLeast(1)
                    textPaint.textSize = (height * 0.045f).coerceIn(24f, 64f)
                    val layout = StaticLayout.Builder.obtain(
                        active.text, 0, active.text.length, textPaint, maxTextWidth
                    ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                        .setIncludePad(false)
                        .build()
                    val horizontalPadding = width * 0.025f
                    val verticalPadding = height * 0.012f
                    val blockHeight = layout.height + verticalPadding * 2
                    val top = height * 0.88f - blockHeight
                    val left = (width - layout.width) / 2f - horizontalPadding
                    val right = (width + layout.width) / 2f + horizontalPadding
                    canvas.drawRoundRect(
                        RectF(left, top, right, top + blockHeight),
                        height * 0.012f, height * 0.012f, backgroundPaint
                    )
                    canvas.save()
                    canvas.translate((width - layout.width) / 2f, top + verticalPadding)
                    layout.draw(canvas)
                    canvas.restore()
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

    private fun formatTime(milliseconds: Long): String {
        val hours = milliseconds / 3_600_000
        val minutes = (milliseconds % 3_600_000) / 60_000
        val seconds = (milliseconds % 60_000) / 1_000
        val millis = milliseconds % 1_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

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
