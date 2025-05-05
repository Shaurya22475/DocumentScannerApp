package com.example.documentscannerportable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

suspend fun extractTextFromPdf(context: Context, pdfFile: File): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val extractedTextBuilder = StringBuilder()

    try {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        for (i in 0 until pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            extractedTextBuilder.append("Page ${i + 1}:\n${result.text}\n\n")
        }

        pdfRenderer.close()
        fileDescriptor.close()
    } catch (e: Exception) {
        Log.e("OCR", "Failed to extract text", e)
        extractedTextBuilder.append("OCR failed: ${e.message}")
    }

    return extractedTextBuilder.toString()
}
