import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.awt.Desktop
import java.io.File

class DecryptFileScene(
    private val stage: Stage,
    private val username: String,
    private val onBack: () -> Unit
) {
    private val fieldWidth = 300.0 // Chiều rộng chung của các input field
    private val buttonWidth = fieldWidth * 0.8 // Button rộng 80% so với field

    private var selectedFile: File? = null

    private val fileLabel = TextField("Chưa chọn file").apply {
        isEditable = false
        maxWidth = fieldWidth
    }

    private val keyField = TextField().apply {
        promptText = "Nhập key"
        maxWidth = fieldWidth
    }

    private val keySizeComboBox = ComboBox<String>().apply {
        items.addAll("128", "192", "256")
        value = "128"
        maxWidth = buttonWidth
    }

    fun createScene(): Scene {
        val backButton = Button("⬅ Quay lại").apply {
            maxWidth = buttonWidth
            setOnAction { onBack() }
        }

        val fileIcon = ImageView(Image(javaClass.getResourceAsStream("/icons/data-encryption.png"))).apply {
            fitWidth = 16.0
            fitHeight = 16.0
        }

        val chooseFileButton = Button("Chọn file", fileIcon).apply {
            maxWidth = buttonWidth
            setOnAction {
                val fileChooser = FileChooser()

                // Đặt thư mục mặc định vào user/username/Received
                val receivedDir = File("users/$username/Received")
                if (!receivedDir.exists()) receivedDir.mkdirs()
                fileChooser.initialDirectory = receivedDir

                val selectedFile = fileChooser.showOpenDialog(stage)
                selectedFile?.let {
                    fileLabel.text = it.name
                    this@DecryptFileScene.selectedFile = it  // Lưu file được chọn
                }
            }
        }

        val fileSelectionRow = HBox(10.0, keySizeComboBox, chooseFileButton).apply {
            alignment = Pos.CENTER
        }

        val decryptButton = Button("🔓 Giải mã").apply {
            maxWidth = buttonWidth
            setOnAction {
                decryptFile()
            }
        }

        val layout = VBox(12.0, backButton, fileLabel, keyField, fileSelectionRow, decryptButton).apply {
            alignment = Pos.CENTER
            padding = Insets(20.0)
        }

        return Scene(layout, 400.0, 300.0)
    }
    private fun decryptFile() {
        val file = selectedFile
        val key = keyField.text.trim()
        val keySize = keySizeComboBox.value.toIntOrNull()

        if (file == null || key.isEmpty() || keySize == null) {
            showAlert("Vui lòng chọn file và nhập key!")
            return
        }

        val decryptDir = File("users/$username/Decrypt")
        if (!decryptDir.exists()) decryptDir.mkdirs()

        // File đầu ra giữ nguyên tên gốc
        val decryptedFile = File(decryptDir, file.name)

        val keyLength = when (keySize) {
            128 -> Aes.KeyLength.AES_128
            192 -> Aes.KeyLength.AES_192
            256 -> Aes.KeyLength.AES_256
            else -> {
                showAlert("Độ dài key không hợp lệ!")
                return
            }
        }

        val aes = Aes(keyLength, key)
        aes.decryptFile(file.absolutePath, decryptedFile.absolutePath)

        // Kiểm tra lỗi từ Aes
        if (aes.getError() == "No error") {
            showAlert("✅ File đã được giải mã thành công!\nLưu tại: ${decryptedFile.absolutePath}")
            // Mở file ngay sau khi giải mã thành công
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(decryptedFile)
                } else {
                    showAlert("Hệ thống của bạn không hỗ trợ mở file tự động.")
                }
            } catch (e: Exception) {
                showAlert("Không thể mở file: ${e.message}")
            }
        } else {
            showAlert("❌ Giải mã thất bại! Lỗi: ${aes.getError()}")
            decryptedFile.delete() // Xóa file rác nếu giải mã thất bại
        }
    }
    private fun showAlert(message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait()
        }
    }
}
