package com.nobodybuilds.listens.data.smart

import android.content.Context
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * Thin wrapper around a single TFLite [Interpreter] running YAMNet (2 threads).
 * Create lazily, use for one batch, then [close]. Not thread-safe; call from one worker thread.
 */
class YamnetClassifier(private val context: Context) : Closeable {

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputIndex = 0
    private var outputShape: IntArray = intArrayOf(1, SmartModel.NUM_CLASSES)

    private fun ensureInterpreter(): Interpreter {
        interpreter?.let { return it }
        val model = loadModel()
        val options = Interpreter.Options().setNumThreads(2)
        val created = Interpreter(model, options)
        val inputShape = created.getInputTensor(0).shape()
        val inputSize = inputShape.fold(1) { acc, v -> acc * v.coerceAtLeast(1) }
        if (inputSize != SmartModel.FRAME_SAMPLES) {
            created.resizeInput(0, intArrayOf(SmartModel.FRAME_SAMPLES))
            created.allocateTensors()
        }
        // Pick the output tensor that has 521 classes (the MediaPipe build has only "scores").
        for (i in 0 until created.outputTensorCount) {
            val shape = created.getOutputTensor(i).shape()
            if (shape.lastOrNull() == SmartModel.NUM_CLASSES) {
                outputIndex = i
                outputShape = shape
                break
            }
        }
        inputBuffer = ByteBuffer.allocateDirect(SmartModel.FRAME_SAMPLES * 4).order(ByteOrder.nativeOrder())
        interpreter = created
        return created
    }

    private fun loadModel(): MappedByteBuffer {
        context.assets.openFd(SmartModel.ASSET_PATH).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    /** Scores for one 0.975 s frame of 16 kHz mono audio starting at [offset] in [pcm]. */
    fun classify(pcm: FloatArray, offset: Int): FloatArray {
        val interp = ensureInterpreter()
        val input = inputBuffer!!
        input.rewind()
        for (i in 0 until SmartModel.FRAME_SAMPLES) {
            val idx = offset + i
            input.putFloat(if (idx < pcm.size) pcm[idx].coerceIn(-1f, 1f) else 0f)
        }
        input.rewind()
        val rows = if (outputShape.size >= 2) outputShape[outputShape.size - 2].coerceAtLeast(1) else 1
        val output = Array(rows) { FloatArray(SmartModel.NUM_CLASSES) }
        val outputs = HashMap<Int, Any>()
        outputs[outputIndex] = if (outputShape.size == 1) output[0] else output
        // Other outputs (if any) get scratch buffers.
        for (i in 0 until interp.outputTensorCount) {
            if (i == outputIndex) continue
            val shape = interp.getOutputTensor(i).shape()
            outputs[i] = ByteBuffer.allocateDirect(shape.fold(4) { acc, v -> acc * v.coerceAtLeast(1) })
                .order(ByteOrder.nativeOrder())
        }
        interp.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)
        if (rows == 1) return output[0]
        val avg = FloatArray(SmartModel.NUM_CLASSES)
        output.forEach { row -> for (c in avg.indices) avg[c] += row[c] / rows }
        return avg
    }

    /** Mean scores over consecutive non-overlapping frames of every window. */
    fun classifyWindows(windows: List<FloatArray>, isCancelled: () -> Boolean = { false }): FloatArray? {
        val sum = FloatArray(SmartModel.NUM_CLASSES)
        var count = 0
        for (pcm in windows) {
            var offset = 0
            while (offset + SmartModel.FRAME_SAMPLES <= pcm.size || (offset == 0 && pcm.size >= SmartModel.FRAME_SAMPLES / 2)) {
                if (isCancelled()) return null
                val scores = classify(pcm, offset)
                for (c in sum.indices) sum[c] += scores[c]
                count++
                offset += SmartModel.FRAME_SAMPLES
            }
        }
        if (count == 0) return null
        for (c in sum.indices) sum[c] /= count
        return sum
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        inputBuffer = null
    }
}
