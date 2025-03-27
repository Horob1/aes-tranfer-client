import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
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

    private val fileLabel = TextField("No file selected.").apply {
        isEditable = false
        maxWidth = fieldWidth
    }

    private val keyField = TextField().apply {
        promptText = "Enter key"
        maxWidth = fieldWidth
    }

    private val keySizeComboBox = ComboBox<String>().apply {
        items.addAll("128", "192", "256")
        value = "128"
        maxWidth = buttonWidth
    }

    fun createScene(): Scene {
        val backIcon = ImageView(Image(javaClass.getResourceAsStream("/left-arrow.png"))).apply {
            fitWidth = 20.0
            fitHeight = 20.0
            styleClass.add("back-icon")
        }

        val backButton = Button("Back", backIcon).apply {
            maxWidth = 100.0 // Giữ nút nhỏ gọn
            setOnAction { onBack() }
        }

        val backButtonContainer = HBox(backButton).apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(10.0, 0.0, 20.0, 0.0)
        }

        val fileIcon = ImageView(Image(javaClass.getResourceAsStream("/icons/data-encryption.png"))).apply {
            fitWidth = 20.0
            fitHeight = 20.0
        }

        val chooseFileButton = Button("Select file", fileIcon).apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                val fileChooser = FileChooser()
                val receivedDir = File("users/$username/Received")
                if (!receivedDir.exists()) receivedDir.mkdirs()
                fileChooser.initialDirectory = receivedDir

                val selectedFile = fileChooser.showOpenDialog(stage)
                selectedFile?.let {
                    fileLabel.text = it.name
                    this@DecryptFileScene.selectedFile = it
                }
            }
        }

        keySizeComboBox.maxWidth = Double.MAX_VALUE

        // Căn chỉnh 2 ô input full dòng
        fileLabel.maxWidth = Double.MAX_VALUE
        keyField.maxWidth = Double.MAX_VALUE

        // Tạo các dòng riêng biệt
        val fileLabelRow = VBox(fileLabel).apply {
            alignment = Pos.CENTER
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val keyFieldRow = VBox(keyField).apply {
            alignment = Pos.CENTER
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val keySizeRow = VBox(keySizeComboBox).apply {
            alignment = Pos.CENTER
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val fileSelectRow = VBox(chooseFileButton).apply {
            alignment = Pos.CENTER
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val decryptIcon = ImageView(Image(javaClass.getResourceAsStream("/unlocked.png"))).apply {
            fitWidth = 20.0
            fitHeight = 20.0
        }

        val decryptButton = Button("Decrypt", decryptIcon).apply {
            maxWidth = Double.MAX_VALUE
            setOnAction { decryptFile() }
        }

        val layout = VBox(12.0, backButtonContainer, fileLabelRow, keyFieldRow, keySizeRow, fileSelectRow, decryptButton).apply {
            alignment = Pos.CENTER
            padding = Insets(20.0)
        }

        val animatedBackground = AnimatedBackground(300.0, 400.0)
        val root = StackPane( animatedBackground, layout)
        root.alignment = Pos.CENTER
        val scene = Scene(root, 300.0, 400.0)
        scene.stylesheets.add(javaClass.getResource("/destyles.css")?.toExternalForm())

        return scene
    }


    private fun decryptFile() {
        val file = selectedFile
        val key = keyField.text.trim()
        val keySize = keySizeComboBox.value.toIntOrNull()

        if (file == null || key.isEmpty() || keySize == null) {
            showAlert("Please select a file and enter a key!")
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
                showAlert("Invalid key length!")
                return
            }
        }

        val aes = Aes(keyLength, key)
        aes.decryptFile(file.absolutePath, decryptedFile.absolutePath)

        // Kiểm tra lỗi từ Aes
        if (aes.getError() == "No error") {
            showAlert("✅ File has been decrypted successfully!\nSaved at: ${decryptedFile.absolutePath}")
            // Mở file ngay sau khi giải mã thành công
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(decryptedFile)
                } else {
                    showAlert("Your system does not support automatic file opening..")
                }
            } catch (e: Exception) {
                showAlert("Cannot open file: ${e.message}")
            }
        } else {
            showAlert("❌ Decryption failed! Error: ${aes.getError()}")
            decryptedFile.delete() // Xóa file rác nếu giải mã thất bại
        }
    }
    private fun showAlert(message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait()
        }
    }
}
