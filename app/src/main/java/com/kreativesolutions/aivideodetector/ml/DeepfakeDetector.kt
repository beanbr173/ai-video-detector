package com.kreativesolutions.aivideodetector.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
class DeepfakeDetector(context: Context) {
    private val interpreter: Interpreter
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputDataType: DataType
    private val outputIsProbability: Boolean

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_FILE)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        val input = interpreter.getInputTensor(0)
        val shape = input.shape()
        inputHeight = shape[1]
        inputWidth = shape[2]
        inputDataType = input.dataType()
        outputIsProbability = interpreter.getOutputTensor(0).shape().last() == 1
    }

    fun fakeProbability(bitmap: Bitmap): Float {
        val inputBuffer = preprocess(bitmap)
        val output = Array(1) { FloatArray(if (outputIsProbability) 1 else 2) }
        interpreter.run(inputBuffer, output)
        return if (outputIsProbability) {
            output[0][0].coerceIn(0f, 1f)
        } else {
            softmaxFakeProbability(output[0])
        }
    }

    private fun preprocess(source: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled !== source) {
            scaled.recycle()
        }

        val buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * INPUT_CHANNELS * FLOAT_BYTES)
        buffer.order(ByteOrder.nativeOrder())

        when (inputDataType) {
            DataType.FLOAT32 -> {
                for (pixel in pixels) {
                    val r = ((pixel shr 16) and 0xFF) / NORMALIZE_DIVISOR - NORMALIZE_OFFSET
                    val g = ((pixel shr 8) and 0xFF) / NORMALIZE_DIVISOR - NORMALIZE_OFFSET
                    val b = (pixel and 0xFF) / NORMALIZE_DIVISOR - NORMALIZE_OFFSET
                    buffer.putFloat(r)
                    buffer.putFloat(g)
                    buffer.putFloat(b)
                }
            }

            DataType.UINT8 -> {
                for (pixel in pixels) {
                    buffer.put(((pixel shr 16) and 0xFF).toByte())
                    buffer.put(((pixel shr 8) and 0xFF).toByte())
                    buffer.put((pixel and 0xFF).toByte())
                }
            }

            else -> error("Unsupported input type: $inputDataType")
        }

        buffer.rewind()
        return buffer
    }

    private fun softmaxFakeProbability(logits: FloatArray): Float {
        if (logits.size == 1) {
            return logits[0].coerceIn(0f, 1f)
        }
        val maxLogit = logits.max()
        val exp = logits.map { kotlin.math.exp((it - maxLogit).toDouble()).toFloat() }
        val sum = exp.sum()
        val fakeIndex = if (logits.size == 2) 1 else logits.indices.maxByOrNull { logits[it] } ?: 1
        return (exp[fakeIndex] / sum).coerceIn(0f, 1f)
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "deepfake_detector.tflite"
        private const val INPUT_CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val NORMALIZE_DIVISOR = 127.5f
        private const val NORMALIZE_OFFSET = 1f
    }
}
