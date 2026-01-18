package com.example.classi_backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class ImageAnalyzer(private val context: Context) {
    private var tflite: Interpreter? = null
    private val IMG_SIZE = 224

    init {
        try {
            tflite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd("final.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun analyzeImage(uri: Uri): Boolean {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return false

            val processedBitmap = preprocess(bitmap)
            val inputBuffer = convertBitmapToByteBuffer(processedBitmap)
            val output = Array(1) { FloatArray(1) }

            tflite?.run(inputBuffer, output)

            val score = output[0][0]

            score <= 0.5f
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun preprocess(im: Bitmap): Bitmap {
        var w = im.width
        var h = im.height
        val scale = IMG_SIZE.toFloat() / minOf(w, h)
        w = (w * scale).toInt()
        h = (h * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(im, w, h, true)
        val l = (w - IMG_SIZE) / 2
        val t = (h - IMG_SIZE) / 2

        return Bitmap.createBitmap(scaled, l, t, IMG_SIZE, IMG_SIZE)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until IMG_SIZE) {
            for (j in 0 until IMG_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((value and 0xFF) / 255.0f))
            }
        }
        return byteBuffer
    }

    fun close() {
        tflite?.close()
    }
}
