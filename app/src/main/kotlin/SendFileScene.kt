import Aes
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


    private val refreshButton = Button("🔄").apply {
        setOnAction { requestClientList() }
    }
    private val keySizeComboBox = ComboBox<String>().apply {
        items.addAll("128", "192", "256")
        value = "128"
        maxWidth = buttonWidth
    }

    fun createScene(): Scene {
        requestClientList()
        val backButton = Button("⬅ Back").apply {
            maxWidth = buttonWidth
            setOnAction { onBack() }
        }

        val fileIcon = ImageView(Image(javaClass.getResourceAsStream("/icons/data-encryption.png"))).apply {
            fitWidth = 16.0
            fitHeight = 16.0
        }

        val chooseFileButton = Button("Select File", fileIcon).apply {
            maxWidth = buttonWidth
            setOnAction {
                val fileChooser = FileChooser()
                val file = fileChooser.showOpenDialog(stage)
                if (file != null) {
                    selectedFile = file
                    fileLabel.text = file.name
                }
            }
        }

        val fileSelectionRow = HBox(10.0, keySizeComboBox, chooseFileButton).apply {
            alignment = Pos.CENTER
        }

        val recipientRow = HBox(10.0, refreshButton, recipientComboBox).apply {
            alignment = Pos.CENTER
        }

        val sendButton = Button("📤 Send").apply {
            maxWidth = buttonWidth
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

        val layout = VBox(12.0, backButton, fileLabel, keyField, fileSelectionRow, recipientRow, sendButton).apply {
            alignment = Pos.CENTER
            padding = Insets(20.0)
        }

        return Scene(layout, 400.0, 350.0)
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
