import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.*
import javafx.stage.Stage
import java.awt.Desktop
import java.io.*
import java.net.Socket
import kotlin.concurrent.thread

class ClientApp : Application() {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val logArea = TextArea()
    private var currentUsername: String? = null

    override fun start(primaryStage: Stage) {
        primaryStage.title = "AES Transfer Client"
        primaryStage.scene = createLoginScene(primaryStage)
        primaryStage.isResizable = false
        primaryStage.isMaximized = false
        primaryStage.icons.add(Image(javaClass.getResourceAsStream("/icons/logo.png")))
        primaryStage.show()
    }

    private fun createLoginScene(stage: Stage): Scene {
        val gridPane = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            padding = Insets(20.0)
            alignment = Pos.CENTER
        }

        val ipLabel = Label("IP:")
        val ipField = TextField("127.0.0.1").apply { prefWidth = 200.0 }

        val portLabel = Label("PORT:")
        val portField = TextField("5000").apply { prefWidth = 200.0 }

        val userLabel = Label("Username:")
        val userField = TextField().apply { prefWidth = 200.0 }

        val loginButton = Button("Login").apply {
            minWidth = 260.0
            setOnAction {
                val ip = ipField.text.trim()
                val portText = portField.text.trim()
                val username = userField.text.trim()

                // Kiểm tra nếu IP hoặc PORT bị bỏ trống
                if (ip.isEmpty() || portText.isEmpty()) {
                    showAlert("Please enter the full IP and PORT!")
                    return@setOnAction
                }
                // Chuyển PORT từ String sang Int, nếu lỗi thì báo người dùng
                val port = portText.toIntOrNull()
                if (port == null || port <= 0 || port > 65535) {
                    showAlert("Invalid PORT!")
                    return@setOnAction
                }
                // Kiểm tra username
                if (username.isEmpty()) {
                    showAlert("Please enter a Username!")
                    return@setOnAction
                }

                // Nếu tất cả hợp lệ, tiến hành kết nối
                connectToServer(ip, port, username, stage)
            }
        }


        gridPane.add(ipLabel, 0, 0)
        gridPane.add(ipField, 1, 0)
        gridPane.add(portLabel, 0, 1)
        gridPane.add(portField, 1, 1)
        gridPane.add(userLabel, 0, 2)
        gridPane.add(userField, 1, 2)
        gridPane.add(loginButton, 0, 3, 2, 1)

        return Scene(gridPane, 350.0, 250.0)
    }

    private fun createMainScene(stage: Stage): Scene {
        val fileButton = Button("My Files").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                currentUsername?.let { username ->
                    val userDecryptDir = File("./users/$username/Decrypt")
                    if (!userDecryptDir.exists()) {
                        userDecryptDir.mkdirs() // Tạo thư mục nếu chưa có
                    }

                    try {
                        Desktop.getDesktop().open(userDecryptDir)
                    } catch (e: IOException) {
                        showAlert("Cannot open the folder!")
                    }
                } ?: showAlert("Not logged in!")
            }
        }
        val sendButton = Button("Send File").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                stage.scene = SendFileScene(
                    stage,
                    username = currentUsername!!,
                    serverIp = socket!!.inetAddress.hostAddress,
                    serverPort = socket!!.port
                ) { stage.scene = createMainScene(stage) }.createScene()
            }
        }

        val decryptButton = Button("Decrypt File").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                currentUsername?.let { username ->
                    stage.scene = DecryptFileScene(stage, username) {
                        stage.scene = createMainScene(stage)
                    }.createScene()
                } ?: showAlert("Not logged in!")
            }
        }

        val logoutButton = Button("Logout").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                disconnectFromServer()
                stage.scene = createLoginScene(stage)
            }
        }

        // Tạo VBox chứa các nút, căn giữa và thêm khoảng cách
        val buttonBox = VBox(15.0, fileButton, sendButton, decryptButton, logoutButton).apply {
            alignment = Pos.CENTER
            padding = Insets(30.0)  // Khoảng cách từ mép ngoài vào
            prefWidth = 300.0       // Định nghĩa chiều rộng cố định cho layout
        }

        val root = StackPane(buttonBox).apply {
            alignment = Pos.CENTER
            padding = Insets(50.0)
        }

        return Scene(root, 400.0, 300.0)
    }
    private fun connectToServer(ip: String, port: Int, username: String, stage: Stage) {
        val task = object : Task<Boolean>() {
            override fun call(): Boolean {
                return try {
                    socket = Socket(ip, port)
                    writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8), true)
                    reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))

                    // Gửi yêu cầu đăng nhập đến server
                    writer!!.println("LOGIN:$username")
                    currentUsername = username

                    true  // Kết nối thành công
                } catch (e: IOException) {
                    false // Kết nối thất bại
                }
            }
        }

        task.setOnSucceeded {
            if (task.value) {
                Platform.runLater {
                    appendLog("Connected as $username")
                    stage.scene = createMainScene(stage)
                }
                listenToServer()
            } else {
                showAlert("Cannot connect to the server!")
            }
        }

        Thread(task).start()
    }

    private fun listenToServer() {
        thread {
            val fileReceiver = FileReceiver(socket!!, currentUsername!!)
            try {
                while (true) {
                    val serverMessage = reader?.readLine() ?: break
                    Platform.runLater { appendLog("📩 Server: $serverMessage") }

                    if (serverMessage.startsWith("FILE:")) {
                        val fileName = serverMessage.substring(5) // Lấy tên file
                        val fileSize = reader?.readLine()?.toLongOrNull() ?: break

                        Platform.runLater {
                            showAlert("📂 A file named \"$fileName\" has been sent to you")
                        }

                        fileReceiver.receiveFile(fileName, fileSize)

                        Platform.runLater { appendLog("✅ File $fileName received.") }
                    }
                }
            } catch (e: IOException) {
                Platform.runLater { appendLog("Lost connection to the server!") }
            } finally {
                disconnectFromServer()
            }
        }
    }



    private fun disconnectFromServer() {
        try {
            socket?.close()
            appendLog("Disconnected")
        } catch (e: IOException) {
            appendLog("Error disconnecting: ${e.message}")
        }
    }

    private fun appendLog(text: String) {
        Platform.runLater { logArea.appendText("$text\n") }
    }

    private fun showAlert(message: String) {
        Platform.runLater {
            Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait()
        }
    }
}

fun main() {
    Application.launch(ClientApp::class.java)
}
