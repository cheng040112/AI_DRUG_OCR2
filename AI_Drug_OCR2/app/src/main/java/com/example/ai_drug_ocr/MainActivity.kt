package com.example.ai_drug_ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ai_drug_ocr.network.GeminiApiService
import com.example.ai_drug_ocr.network.RetrofitInstance
import com.example.ai_drug_ocr.network.TextRequest
import com.example.ai_drug_ocr.network.SummaryResponse
import com.example.ai_drug_ocr.ui.theme.AI_Drug_OCR_Theme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AI_Drug_OCR_Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OCRScreen()
                }
            }
        }
    }
}

@Composable
fun OCRScreen() {
    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) } // <== 修改點 1: 改名為 tempImageUri，只用來暫存路徑
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) } // <== 新增點 1: 用這個 State 來存放真正要顯示的圖片
    var resultText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // 負責將 Uri 解碼成 Bitmap 並觸發後續處理的函式
    val processUri: (Uri) -> Unit = { uri ->
        isProcessing = true
        resultText = "" // 清空上次結果
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            imageBitmap = bitmap // <== 新增點 2: 更新圖片 Bitmap 狀態

            // 使用 ML Kit 辨識文字
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    resultText = "📄 辨識結果：\n${result.text}"
                    // 呼叫 Gemini
                    processTextWithGemini(result.text) { summary ->
                        resultText += "\n\n🔍 Gemini 摘要：\n$summary"
                        isProcessing = false
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "OCR 失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                }

        } catch (e: Exception) {
            Toast.makeText(context, "影像處理錯誤：${e.message}", Toast.LENGTH_SHORT).show()
            isProcessing = false
        }
    }

    // 1. 拍照啟動器
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            processUri(tempImageUri!!) // <== 修改點 2: 成功後呼叫統一的處理函式
        }
    }

    // 2. 從相簿選取圖片啟動器
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            processUri(it) // <== 修改點 3: 同樣呼叫統一的處理函式
        }
    }

    // 3. 請求相機權限啟動器
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("ocr_", ".jpg", context.cacheDir)
            tempImageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(tempImageUri)
        } else {
            Toast.makeText(context, "需要相機權限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    // (UI 介面的 Column 保持不變，除了 Image 元件)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 拍照按鈕
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val file = File.createTempFile("ocr_", ".jpg", context.cacheDir)
                tempImageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                takePictureLauncher.launch(tempImageUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }) {
            Text("📸 拍照")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 從相簿選擇按鈕
        Button(onClick = {
            pickImageLauncher.launch("image/*")
        }) {
            Text("🖼️ 從相簿選擇")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // <== 修改點 4: 顯示解碼後的 Bitmap，而不是去解碼 Uri
        imageBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Selected Image",
                modifier = Modifier.height(200.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isProcessing) {
            CircularProgressIndicator()
        }

        Text(resultText)
    }
}

// 注意：我把你的 processImage 函式邏輯整合到 OCRScreen 裡面了，
// 所以你可以把舊的 processImage 函式刪除，以避免混淆。
// processTextWithGemini 函式保持不變，所以不用動它。

// 影像文字辨識處理
fun processImage(context: Context, uri: Uri, onResult: (String) -> Unit) {
    try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { result ->
                onResult(result.text)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "OCR 失敗：${e.message}", Toast.LENGTH_SHORT).show()
                onResult("")
            }

    } catch (e: Exception) {
        Toast.makeText(context, "影像處理錯誤：${e.message}", Toast.LENGTH_SHORT).show()
        onResult("")
    }
}

// 呼叫後端 Gemini API 取得摘要
fun processTextWithGemini(text: String, onResult: (String) -> Unit) {
    val apiService = RetrofitInstance.api
    val call = apiService.getSummary(TextRequest(text))

    call.enqueue(object : Callback<SummaryResponse> {
        override fun onResponse(call: Call<SummaryResponse>, response: Response<SummaryResponse>) {
            if (response.isSuccessful) {
                val summary = response.body()?.summary ?: "沒有摘要"
                onResult(summary)
            } else {
                onResult("伺服器錯誤：${response.code()}")
            }
        }

        override fun onFailure(call: Call<SummaryResponse>, t: Throwable) {
            onResult("無法連線到後端：${t.localizedMessage}")
        }
    })
}
