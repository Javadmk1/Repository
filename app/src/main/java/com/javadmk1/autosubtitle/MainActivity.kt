package com.javadmk1.autosubtitle

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.database.Cursor
import android.provider.OpenableColumns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val cues = mutableListOf<Cue>()
    private lateinit var selectedFileLabel: TextView
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
        content.addView(makeButton("خروجی گرفتن فایل SRT") { exportSrt() }, matchWidth())

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
            text = "نکته: این نسخه زیرنویس SRT می‌سازد؛ چسباندن زیرنویس روی تصویر و خروجی MP4 هنوز اضافه نشده است."
            textSize = 13f
            setPadding(0, dp(16), 0, dp(8))
        }, matchWidth())

        setContentView(scroll)
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
                toast("فایل انتخاب شد")
            }
            REQUEST_EXPORT_SRT -> {
                data.data?.let { uri -> writeSrt(uri) }
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
