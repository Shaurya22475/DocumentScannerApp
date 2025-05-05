import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

fun performOcrOnPdf(context: Context, pdfFile: File) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    try {
        val fileDescriptor: ParcelFileDescriptor =
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        if (pdfRenderer.pageCount == 0) {
            Toast.makeText(context, "PDF is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val page = pdfRenderer.openPage(0)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        pdfRenderer.close()

        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d("OCR", "Extracted Text:\n$text")

                // Write to file
                val outputFile = File(context.filesDir, "extracted_text.txt")
                try {
                    FileOutputStream(outputFile).use { it.write(text.toByteArray()) }
                    Toast.makeText(context, "OCR saved to ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("OCR", "Error writing text file", e)
                    Toast.makeText(context, "Failed to save OCR output: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed", e)
                Toast.makeText(context, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    } catch (e: Exception) {
        Log.e("OCR", "Error reading PDF", e)
        Toast.makeText(context, "Failed to read PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
