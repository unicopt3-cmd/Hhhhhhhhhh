package com.example.engine

import android.graphics.Bitmap
import com.example.model.CandidateSource
import com.example.model.RectBounds
import com.example.model.UiCandidate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class OcrExtractionResult(
  val candidates: List<UiCandidate>,
  val allTexts: List<String>,
  val latencyMs: Long,
)

class OcrProcessor {
  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  suspend fun processBitmap(
    bitmap: Bitmap,
    scaleToScreenX: Float,
    scaleToScreenY: Float,
    foregroundPackage: String,
  ): OcrExtractionResult {
    val startMs = System.currentTimeMillis()
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    val resultText = recognizer.process(inputImage).awaitTask()

    val candidates = mutableListOf<UiCandidate>()
    val allTexts = mutableListOf<String>()
    var idx = 0

    for (block in resultText.textBlocks) {
      val blockText = block.text.trim()
      if (blockText.isNotEmpty()) {
        allTexts.add(blockText)
      }
      for (line in block.lines) {
        val lineText = line.text.trim()
        val lineRect = line.boundingBox ?: continue
        if (lineText.isEmpty()) continue

        val scaledBounds =
          RectBounds(
            left = (lineRect.left * scaleToScreenX).toInt(),
            top = (lineRect.top * scaleToScreenY).toInt(),
            right = (lineRect.right * scaleToScreenX).toInt(),
            bottom = (lineRect.bottom * scaleToScreenY).toInt(),
          )

        candidates.add(
          UiCandidate(
            id = "ocr_line_${idx++}",
            text = lineText,
            contentDescription = "",
            viewIdResourceName = "",
            className = "OcrTextLine",
            packageName = foregroundPackage,
            bounds = scaledBounds,
            isClickable = true,
            isEnabled = true,
            isVisibleToUser = true,
            source = CandidateSource.OCR,
          )
        )

        // Also inspect individual short elements for compact buttons like "X", ">>", "Skip"
        if (line.elements.size > 1) {
          for (element in line.elements) {
            val elText = element.text.trim()
            val elRect = element.boundingBox ?: continue
            if (elText.length in 1..14) {
              val elBounds =
                RectBounds(
                  left = (elRect.left * scaleToScreenX).toInt(),
                  top = (elRect.top * scaleToScreenY).toInt(),
                  right = (elRect.right * scaleToScreenX).toInt(),
                  bottom = (elRect.bottom * scaleToScreenY).toInt(),
                )
              candidates.add(
                UiCandidate(
                  id = "ocr_el_${idx++}",
                  text = elText,
                  contentDescription = "",
                  viewIdResourceName = "",
                  className = "OcrTextElement",
                  packageName = foregroundPackage,
                  bounds = elBounds,
                  isClickable = true,
                  isEnabled = true,
                  isVisibleToUser = true,
                  source = CandidateSource.OCR,
                )
              )
            }
          }
        }
      }
    }

    val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
    return OcrExtractionResult(
      candidates = candidates,
      allTexts = allTexts,
      latencyMs = latency,
    )
  }

  fun close() {
    runCatching { recognizer.close() }
  }
}

private suspend fun <T> Task<T>.awaitTask(): T =
  suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
      if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { e ->
      if (continuation.isActive) continuation.resumeWithException(e)
    }
    addOnCanceledListener {
      continuation.cancel()
    }
  }
