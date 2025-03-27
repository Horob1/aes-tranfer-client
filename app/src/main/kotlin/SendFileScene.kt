
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
import java.io.*
import java.net.Socket

class SendFileScene(
    private val stage: Stage,
    private val username: String, // Thêm username để lưu file đúng thư mục
    private val serverIp: String,
    private val serverPort: Int,
    private val onBack: () -> Unit
) {
    private val fieldWidth = 300.0
    private val buttonWidth = fieldWidth * 0.8

    private var selectedFile: File? = null

    private val fileLabel = TextField("No file selected.").apply {
        isEditable = false
        maxWidth = fieldWidth
    }
    private val keyField = TextField().apply {
        promptText = "Enter key"
        maxWidth = fieldWidth
    }

    private val recipientComboBox = ComboBox<String>().apply {
        promptText = "Select recipient"
        maxWidth = fieldWidth
    }
    private fun requestClientList() {
        Thread {
            try {
                Socket(serverIp, serverPort).use { socket ->
                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                    writer.println("LIST_CLIENTS")
                    writer.flush()

                    val response = reader.readLine()
                    val clients = response?.split(",")?.filter { it.isNotBlank() } ?: listOf()
                    val filteredClients = clients.filter { it != username }
                    Platform.runLater {
                        if (clients.isEmpty()) {
                            showAlert("No clients online!")
                            return@runLater
                        }
                        recipientComboBox.items.setAll(filteredClients)
                        recipientComboBox.isDisable = false // Mở khóa ComboBox khi có client
                    }
                }

            } catch (e: Exception) {
                Platform.runLater {
                    showAlert("Error retrieving client list: ${e.message}")
                }
            }
        }.start()
    }


    private val keySizeComboBox = ComboBox<String>().apply {
        items.addAll("128", "192", "256")
        value = "128"
        maxWidth = buttonWidth
    }

    fun createScene(): Scene {
        requestClientList()

        val backIcon = ImageView(Image(javaClass.getResourceAsStream("/left-arrow.png"))).apply {
            fitWidth = 20.0
            fitHeight = 20.0
        }

        val backButton = Button("Back", backIcon).apply {
            maxWidth = 100.0
            setOnAction { onBack() }
        }

        val backButtonContainer = HBox(backButton).apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(10.0, 0.0, 20.0, 0.0)
        }

        fileLabel.maxWidth = Double.MAX_VALUE
        keyField.maxWidth = Double.MAX_VALUE
        keyField.prefHeight = 35.0  // Đặt chiều cao chuẩn cho input

        val fileIcon = ImageView(Image(javaClass.getResourceAsStream("/icons/data-encryption.png"))).apply {
            fitWidth = 20.0
            fitHeight = 20.0
        }

        val chooseFileButton = Button("Select file", fileIcon).apply {
            maxWidth = Double.MAX_VALUE
            prefHeight = 35.0  // Đặt chiều cao bằng input
            setOnAction {
                val fileChooser = FileChooser()
                val file = fileChooser.showOpenDialog(stage)
                if (file != null) {
                    selectedFile = file
                    fileLabel.text = file.name
                }
            }
        }

        keySizeComboBox.maxWidth = Double.MAX_VALUE
        keySizeComboBox.prefHeight = 35.0 // Đặt chiều cao bằng input

        val fileSelectionRow = HBox(10.0, keySizeComboBox, chooseFileButton).apply {
            alignment = Pos.CENTER
            HBox.setHgrow(keySizeComboBox, Priority.ALWAYS)
            HBox.setHgrow(chooseFileButton, Priority.ALWAYS)
        }

        val refreshIcon = ImageView(Image(javaClass.getResourceAsStream("/reload.png"))).apply {
            fitWidth = 16.0
            fitHeight = 16.0
        }

        val refreshButton = Button("", refreshIcon).apply {
            minWidth = 35.0
            minHeight = 35.0  // Đặt chiều cao bằng input
            tooltip = Tooltip("Refresh recipient list")
            setOnAction { requestClientList() }
        }

        recipientComboBox.maxWidth = Double.MAX_VALUE
        recipientComboBox.prefHeight = 35.0 // Đặt chiều cao bằng input

        val recipientRow = HBox(10.0, refreshButton, recipientComboBox).apply {
            alignment = Pos.CENTER
            HBox.setHgrow(recipientComboBox, Priority.ALWAYS)
        }

        val sendButton = Button("📤 Send").apply {
            maxWidth = Double.MAX_VALUE
            prefHeight = 35.0  // Đặt chiều cao bằng input
            setOnAction {
                if (selectedFile == null) {
                    showAlert("Please select a file!")
                    return@setOnAction
                }

                val key = keyField.text.trim()
                val keySize = keySizeComboBox.value.toInt()
                val receiver = recipientComboBox.value?.trim()

                if (receiver.isNullOrBlank()) {
                    showAlert("Please select a recipient!")
                    return@setOnAction
                }

                if (key.isBlank()) {
                    showAlert("Please enter the encryption key!")
                    return@setOnAction
                }

                val encryptedFile = encryptFile(selectedFile!!, key, keySize)
                if (encryptedFile != null) {
                    sendFileToServer(encryptedFile, receiver)
                    showAlert("📂 File '${selectedFile!!.name}' sent successfully!")
                    onBack()
                } else {
                    showAlert("Error encrypting file!")
                }
            }
        }

        val layout = VBox(12.0, backButtonContainer, fileLabel, keyField, fileSelectionRow, recipientRow, sendButton).apply {
            alignment = Pos.CENTER
            padding = Insets(20.0)
        }
        val animatedBackground = AnimatedBackground(400.0, 400.0)
        val root = StackPane(animatedBackground, layout)
        root.alignment = Pos.CENTER
        val scene = Scene(root, 400.0, 400.0)
        scene.stylesheets.add(javaClass.getResource("/destyles.css")?.toExternalForm())

        return scene
    }


    private fun encryptFile(file: File, key: String, keySize: Int): File? {
        try {
            val encryptDir = File("users/$username/Encrypt")
            if (!encryptDir.exists()) encryptDir.mkdirs()

            val keyLength = when (keySize) {
                128 -> Aes.KeyLength.AES_128
                192 -> Aes.KeyLength.AES_192
                256 -> Aes.KeyLength.AES_256
                else -> throw IllegalArgumentException("Invalid key size!")
            }

            val encryptedFile = File(encryptDir, file.name)

            val aes = Aes(keyLength, key)
            aes.encryptFile(file.absolutePath, encryptedFile.absolutePath)

            val error = aes.getError()
            return if (error == "No error") {
                println("✅ Encryption successful! File saved at: ${encryptedFile.absolutePath}")
                encryptedFile
            } else {
                println("❌ Encryption failed: $error")
                null
            }
        } catch (e: Exception) {
            println("❌ Error during encryption: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun sendFileToServer(file: File, receiver: String) {
        Thread {
            try {
                Socket(serverIp, serverPort).use { socket ->
                    val outputStream = socket.getOutputStream()
                    val writer = PrintWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), true)

                    // Gửi tín hiệu bắt đầu
                    writer.println("START_FILE")

                    // Gửi thông tin file trước
                    writer.println(receiver)
                    writer.println(file.name)
                    writer.println(file.length())

                    FileInputStream(file).use { fileInputStream ->
                        fileInputStream.copyTo(outputStream)
                        outputStream.flush() // Đảm bảo gửi hết dữ liệu
                    }

                    // Đọc phản hồi từ server
                    BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).use { reader ->
                        val serverResponse = reader.readLine()
                        Platform.runLater {
                            showAlert("📤 File '${file.name}' sent successfully! Server Response: $serverResponse")
                        }
                    }
                }
            } catch (e: Exception) {
                Platform.runLater {
                    showAlert("❌ Error sending file: ${e.message}")
                }
            }
        }.start()
    }

    private fun showAlert(message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait()
        }
    }
}
