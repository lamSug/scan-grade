package vn.edu.chamtracnghiem

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import vn.edu.chamtracnghiem.databinding.ActivityScanBinding
import java.io.File
import java.nio.ByteBuffer
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {
    private lateinit var b: ActivityScanBinding
    private lateinit var store: ExamStore
    private var exams = emptyList<Exam>()
    private var imageCapture: ImageCapture? = null
    private var selectedCode: String? = null
    @Volatile private var scanLocked = false
    @Volatile private var grading = false
    private var alignedFrames = 0
    private var lastAnalysis = 0L
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        b.status.text = "Đang tải ảnh..."
        worker.execute {
            runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) }
                .onSuccess { runOnUiThread { grade(it) } }
                .onFailure { runOnUiThread { show(it.message ?: "Không đọc được ảnh") } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = ExamStore(this)
        exams = store.exams()
        b.examPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, exams.map { it.title })
        b.examPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = loadCodes()
        }
        b.camera.setOnClickListener { show("Quét tự động đang bật. Chỉ cần căn 4 ô đen vào khung.") }
        b.gallery.setOnClickListener { gallery.launch("image/*") }
        b.retake.setOnClickListener { resumeCamera() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
    }

    private fun loadCodes() {
        val exam = exams.getOrNull(b.examPicker.selectedItemPosition) ?: return
        val values = listOf("Tự nhận diện mã đề trên phiếu") + store.codes(exam).map { "Mã đề $it" }
        b.codePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
        b.codePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCode = if (position == 0) null else values[position].removePrefix("Mã đề ")
            }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = b.cameraPreview.surfaceProvider }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(Size(640, 480))
                .build()
            analysis.setAnalyzer(worker) { image -> analyzeAlignment(image) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            scanLocked = false; alignedFrames = 0
            b.status.text = "Căn 4 ô vuông đen của phiếu vào các khung. Ứng dụng sẽ tự chấm."
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndGrade() {
        if (exams.isEmpty()) { show("Hãy tạo bài kiểm tra trước."); return }
        val capture = imageCapture ?: run { show("Camera chưa sẵn sàng"); return }
        b.camera.isEnabled = false; scanLocked = true
        b.status.text = "Đã nhận đủ 4 ô góc. Đang chụp và chấm..."
        val file = File(cacheDir, "sheet_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    b.camera.isEnabled = true
                    worker.execute {
                        runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) }
                            .onSuccess { runOnUiThread { grade(it) } }
                            .onFailure { runOnUiThread { show("Không đọc được ảnh vừa chụp") } }
                    }
                }
                override fun onError(exception: ImageCaptureException) { b.camera.isEnabled = true; scanLocked = false; show("Không thể chụp ảnh: ${exception.message}") }
            })
    }

    /** Looks for the four filled registration squares on the live camera frames. */
    private fun analyzeAlignment(image: ImageProxy) {
        try {
            if (scanLocked || grading || exams.isEmpty()) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysis < 550) return
            lastAnalysis = now
            val frame = rgbaBitmap(image)
            val upright = if (image.imageInfo.rotationDegrees == 0) frame else Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true)
            if (hasFourRegistrationSquares(upright)) {
                alignedFrames++
                val message = if (alignedFrames == 1) "Đã thấy 4 ô góc — giữ yên phiếu..." else "Phiếu đã căn đúng. Đang tự chụp..."
                runOnUiThread { b.status.text = message }
                if (alignedFrames >= 2) runOnUiThread { if (!scanLocked) captureAndGrade() }
            } else {
                alignedFrames = 0
                runOnUiThread { b.status.text = "Đưa 4 ô vuông đen trên phiếu vào 4 khung lớn." }
            }
        } finally { image.close() }
    }

    private fun rgbaBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]; val buffer = plane.buffer; val w = image.width; val h = image.height
        val pixels = IntArray(w * h); val row = plane.rowStride; val px = plane.pixelStride
        for (y in 0 until h) for (x in 0 until w) {
            val pos = y * row + x * px
            val r = buffer.get(pos).toInt() and 255; val g = buffer.get(pos + 1).toInt() and 255; val b = buffer.get(pos + 2).toInt() and 255
            pixels[y * w + x] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun hasFourRegistrationSquares(bmp: Bitmap): Boolean {
        // The document may fill the screen or sit inside the large on-screen guide.
        val zones = arrayOf(.02f to .28f to (.04f to .30f), .72f to .98f to (.04f to .30f), .02f to .28f to (.58f to .94f), .72f to .98f to (.58f to .94f))
        return zones.all { (xs, ys) -> darkestInZone(bmp, xs, ys) > 92f }
    }

    private fun darkestInZone(bmp: Bitmap, xs: Pair<Float, Float>, ys: Pair<Float, Float>): Float {
        var best = 0f; val step = (minOf(bmp.width, bmp.height) / 42).coerceAtLeast(8)
        for (y in (bmp.height * ys.first).toInt()..(bmp.height * ys.second).toInt() step step)
            for (x in (bmp.width * xs.first).toInt()..(bmp.width * xs.second).toInt() step step)
                best = maxOf(best, patchDarkness(bmp, x, y, (step * .9f).toInt().coerceAtLeast(6)))
        return best
    }

    private fun patchDarkness(bmp: Bitmap, cx: Int, cy: Int, radius: Int): Float {
        var total = 0; var count = 0
        for (y in cy - radius..cy + radius step 2) for (x in cx - radius..cx + radius step 2)
            if (x in 0 until bmp.width && y in 0 until bmp.height) {
                val c = bmp.getPixel(x, y); total += 255 - ((Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100); count++
            }
        return if (count == 0) 0f else total.toFloat() / count
    }

    private fun darkestNear(bmp: Bitmap, cx: Int, cy: Int): Float {
        val span = (minOf(bmp.width, bmp.height) * .09f).toInt().coerceAtLeast(16); val r = (span * .20f).toInt().coerceAtLeast(7)
        var best = 0f
        for (y in cy - span..cy + span step 8) for (x in cx - span..cx + span step 8) {
            var total = 0; var n = 0
            for (py in y-r..y+r step 3) for (px in x-r..x+r step 3) if (px in 0 until bmp.width && py in 0 until bmp.height) {
                val c = bmp.getPixel(px, py); total += 255 - ((Color.red(c)*30 + Color.green(c)*59 + Color.blue(c)*11) / 100); n++
            }
            if (n > 0) best = maxOf(best, total.toFloat() / n)
        }
        return best
    }

    private fun grade(bitmap: Bitmap) {
        if (grading) return
        val exam = exams.getOrNull(b.examPicker.selectedItemPosition) ?: return
        val manualCode = selectedCode
        grading = true
        b.status.text = "Đang nhận dạng mã đề, ô tô và chấm điểm..."
        b.cameraPreview.visibility = View.GONE
        b.sheetGuide.visibility = View.GONE
        worker.execute {
            runCatching {
                val detected = OmrReader.read(bitmap, exam.questionCount, null).first.code
                val code = manualCode ?: detected
                val key = store.key(exam, code) ?: throw IllegalArgumentException("Chưa có đáp án cho mã đề $code. Hãy vào bài kiểm tra để thêm mã đề này.")
                OmrReader.read(bitmap, exam.questionCount, key)
            }.onSuccess { (result, annotated) -> runOnUiThread {
                grading = false; b.sheet.setImageBitmap(annotated); b.sheet.visibility = View.VISIBLE; b.retake.visibility = View.VISIBLE
                val missing = result.choices.count { it == null }; b.status.text = "Mã đề ${result.code} • ${result.correct}/${result.total} câu đúng"
                AlertDialog.Builder(this).setTitle("Kết quả chấm").setMessage("Mã đề sử dụng: ${manualCode ?: result.code}\nĐiểm: ${result.correct}/${result.total}\n" + if (missing > 0) "Có $missing câu chưa tô hoặc tô nhiều đáp án." else "Đã đánh dấu xanh/đỏ trên ảnh.").setPositiveButton("Đóng", null).show()
            }}.onFailure { error -> runOnUiThread { grading = false; b.sheet.visibility = View.GONE; b.cameraPreview.visibility = View.VISIBLE; b.sheetGuide.visibility = View.VISIBLE; show(error.message ?: "Không thể nhận diện phiếu") } }
        }
    }

    private fun resumeCamera() { b.sheet.visibility = View.GONE; b.retake.visibility = View.GONE; b.cameraPreview.visibility = View.VISIBLE; b.sheetGuide.visibility = View.VISIBLE; startCamera() }
    private fun show(message: String) { b.status.text = message; Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); if (r == CAMERA_PERMISSION && g.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera() else if (r == CAMERA_PERMISSION) show("Cần quyền camera để quét phiếu") }
    companion object { private const val CAMERA_PERMISSION = 7 }
}
