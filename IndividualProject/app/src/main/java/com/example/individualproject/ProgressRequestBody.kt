package com.example.individualproject

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val listener: UploadCallbacks
) : RequestBody() {

    interface UploadCallbacks {
        fun onProgressUpdate(percentage: Int)
        fun onError()
        fun onFinish()
    }

    override fun contentType(): MediaType? {
        return contentType.toMediaTypeOrNull()
    }

    override fun contentLength(): Long {
        return file.length()
    }

    override fun writeTo(sink: BufferedSink) {
        val fileLength = file.length()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val fis = FileInputStream(file)
        var uploaded: Long = 0
        val handler = Handler(Looper.getMainLooper())

        try {
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                uploaded += read.toLong()
                sink.write(buffer, 0, read)
                val progress = ((uploaded.toDouble() / fileLength.toDouble()) * 100).toInt()

                handler.post { listener.onProgressUpdate(progress) }
            }
            handler.post { listener.onFinish() }
        } catch (e: Exception) {
            e.printStackTrace()
            handler.post { listener.onError() }

            throw e
        } finally {
            fis.close()
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}