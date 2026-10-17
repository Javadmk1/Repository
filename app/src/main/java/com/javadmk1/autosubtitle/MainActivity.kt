package com.javadmk1.autosubtitle

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
    private lateinit var cueList: TextView
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
            text = "تبدیل خودکار در این نسخه با سرویس آنلاین Whisper انجام می‌شود؛ اینترنت و کلید API معتبر لازم است. فایل صوتی باید در محدودهٔ مجاز سرویس باشد. فایل ویدئو مستقیماً برای این قابلیت پشتیبانی نمی‌شود."
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

        content.addView(makeButton("افزودن زیرنویس") { addCue() }, matchWidth())
        content.addView(makeButton("خروجی گرفتن فایل SRT") { exportSrt() }, matchWidth())

        content.addView(TextView(this).apply {
            text = "زیرنویس‌های ثبت‌شده"
            textSize = 18f
            setPadding(0, dp(18), 0, dp(8))
        }, matchWidth())
        cueList = TextView(this).apply {
            text = "هنوز زیرنویسی اضافه نشده است."
            textSize = 14f
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(cueList, matchWidth())

        content.addView(TextView(this).apply {
            text = "توجه: تشخیص خودکار گفتار و تبدیل نهایی ویدئو به MP4 در این نسخه پیاده‌سازی نشده است."
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
        if (!mime.startsWith("audio/")) {
            toast("این قابلیت فعلاً فایل صوتی را می‌پذیرد، نه ویدئو")
            return
        }

        toast("تبدیل شروع شد؛ برای فایل‌های طولانی صبر کن")
        Thread {
            var connection: HttpURLConnection? = null
            try {
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
                    val filename = getDisplayName(uri).replace("\"", "_")
                    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n".toByteArray(Charsets.UTF_8))
                    val contentType = if (mime.isNotBlank()) mime else "application/octet-stream"
                    out.write("Content-Type: $contentType\r\n\r\n".toByteArray(Charsets.UTF_8))
                    contentResolver.openInputStream(uri)?.use { input ->
                        input.copyTo(out, 64 * 1024)
                    } ?: throw IllegalStateException("فایل قابل خواندن نیست")
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
            }
        }.start()
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

        cues.add(Cue((startSeconds * 1000).toLong(), (endSeconds * 1000).toLong(), text))
        cues.sortBy { it.startMs }
        subtitleInput.text.clear()
        refreshCueList()
        toast("زیرنویس اضافه شد")
    }

    private fun refreshCueList() {
        cueList.text = if (cues.isEmpty()) {
            "هنوز زیرنویسی اضافه نشده است."
        } else {
            cues.mapIndexed { index, cue ->
                "${index + 1}. ${formatTime(cue.startMs)} --> ${formatTime(cue.endMs)}\n${cue.text}"
            }.joinToString("\n\n")
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
