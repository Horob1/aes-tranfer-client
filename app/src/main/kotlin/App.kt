import javafx.animation.*
import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.effect.BlurType
import javafx.scene.effect.DropShadow
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.util.Duration
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

    private fun playMusic(filePath: String) {
        val musicFile = javaClass.getResource(filePath)?.toExternalForm()
        if (musicFile != null) {
            val media = Media(musicFile)
            val mediaPlayer = MediaPlayer(media)
            mediaPlayer.play()
        } else {
            println("Cannot find music file!")
        }
    }

    override fun start(primaryStage: Stage) {
        primaryStage.title = "AES Transfer Client"
        primaryStage.scene = createLoginScene(primaryStage)
        primaryStage.isResizable = false
        primaryStage.isMaximized = false
        primaryStage.icons.add(Image(javaClass.getResourceAsStream("/icons/logo.png")))
        primaryStage.show()
        playMusic("/noti.mp3")
    }

    private fun createLoginScene(stage: Stage): Scene {
        val animatedBackground = AnimatedBackground(350.0, 400.0)
        val gridPane = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            padding = Insets(20.0)
            alignment = Pos.CENTER
        }
        val logoImage = ImageView(Image(javaClass.getResourceAsStream("/logo.png"))).apply {
            fitWidth = 140.0
            fitHeight = 140.0
            isPreserveRatio = true
            isSmooth = true
            style = "-fx-background-color: rgba(255, 255, 255, 0.1); " +
                    "-fx-border-color: white; " +
                    "-fx-border-width: 2px; " +
                    "-fx-border-radius: 20px; " +
                    "-fx-background-radius: 20px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0.5, 0, 4);"

            // Animation khi di chuột vào
            setOnMouseEntered {
                animateHover(this, 1.1, 20.0).play()
            }
            setOnMouseExited {
                animateHover(this, 1.0, 10.0).play()
            }
        }

        val logoContainer = StackPane(logoImage).apply {
            alignment = Pos.CENTER
            padding = Insets(30.0, 0.0, 20.0, 0.0)
        }
        val ipLabel = Label("IP ⚡:").apply {
            style = "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;"
        }
        val ipField = TextField("127.0.0.1").apply {
            prefWidth = 200.0
        }

        val portLabel = Label("PORT:").apply {
            style = "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;"
        }
        val portField = TextField("5000").apply {
            prefWidth = 200.0
        }
        val userLabel = Label("Name:").apply {
            style = "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;"
        }
        val userField = TextField().apply { prefWidth = 200.0 }

        val loginButton = Button("Login").apply {
            minWidth = 260.0
            setOnAction {
                val ip = ipField.text.trim()
                val portText = portField.text.trim()
                val username = userField.text.trim()
                if (ip.isEmpty() || portText.isEmpty()) {
                    showAlert("Please enter the full IP and PORT!")
                    return@setOnAction
                }
                val port = portText.toIntOrNull()
                if (port == null || port <= 0 || port > 65535) {
                    showAlert("Invalid PORT!")
                    return@setOnAction
                }
                if (username.isEmpty()) {
                    showAlert("Please enter a Username!")
                    return@setOnAction
                }

                // Nếu tất cả hợp lệ, tiến hành kết nối
                connectToServer(ip, port, username, stage)
            }
        }
        val buttonContainer = HBox(loginButton).apply {
            alignment = Pos.CENTER
            padding = Insets(10.0, 0.0, 20.0, 0.0)
        }
        gridPane.add(ipLabel, 0, 0)
        gridPane.add(ipField, 1, 0)
        gridPane.add(portLabel, 0, 1)
        gridPane.add(portField, 1, 1)
        gridPane.add(userLabel, 0, 2)
        gridPane.add(userField, 1, 2)
        gridPane.add(buttonContainer, 0, 3, 2, 1)
        val layout = StackPane(animatedBackground, VBox(logoContainer, gridPane).apply {
            alignment = Pos.CENTER
        })
        val scene = Scene(layout, 350.0, 400.0).apply {
            stylesheets.add(javaClass.getResource("/styles.css")?.toExternalForm())
        }
        return scene
    }
    private fun animateHover(node: ImageView, scaleFactor: Double, shadowSize: Double): ParallelTransition {
        val scaleTransition = ScaleTransition(Duration.millis(250.0), node).apply {
            toX = scaleFactor
            toY = scaleFactor
            interpolator = Interpolator.EASE_OUT
        }

        // Hiệu ứng sáng bóng hơn
        val baseHue = 220.0 // Màu xanh dương chủ đạo
        val glowColor = Color.hsb(baseHue, 0.6, 1.0) // Giữ màu sáng bóng

        val glowEffect = DropShadow(BlurType.GAUSSIAN, glowColor, shadowSize, 0.2, 0.0, 4.0)
        val glowAnimation = Timeline(
            KeyFrame(Duration.millis(250.0), KeyValue(node.effectProperty(), glowEffect, Interpolator.EASE_OUT))
        )

        return ParallelTransition(scaleTransition, glowAnimation)
    }

    private fun createMainScene(stage: Stage): Scene {
        val fileIcon = ImageView(Image(javaClass.getResourceAsStream("/myfile.png"))).apply {
             fitWidth = 24.0
             fitHeight = 24.0
         }
        val fileButton = Button("My Files",fileIcon).apply {
            maxWidth = Double.MAX_VALUE
            styleClass.add("myfile-button")
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
        val sendIcon = ImageView(Image(javaClass.getResourceAsStream("/send.png"))).apply {
            fitWidth = 24.0
            fitHeight = 24.0
        }
        val sendButton = Button("Send File",sendIcon).apply {
            maxWidth = Double.MAX_VALUE
            styleClass.add("sendfile-button")
            setOnAction {
                stage.scene = SendFileScene(
                    stage,
                    username = currentUsername!!,
                    serverIp = socket!!.inetAddress.hostAddress,
                    serverPort = socket!!.port
                ) { stage.scene = createMainScene(stage) }.createScene()
            }
        }
        val decryptIcon = ImageView(Image(javaClass.getResourceAsStream("/decryption.png"))).apply {
            fitWidth = 24.0
            fitHeight = 24.0
        }
        val decryptButton = Button("Decrypt File",decryptIcon).apply {
            maxWidth = Double.MAX_VALUE
            styleClass.add("decryptfile-button")
            setOnAction {
                currentUsername?.let { username ->
                    stage.scene = DecryptFileScene(stage, username) {
                        stage.scene = createMainScene(stage)
                    }.createScene()
                } ?: showAlert("Not logged in!")
            }
        }
        val logoutIcon = ImageView(Image(javaClass.getResourceAsStream("/logout.png"))).apply {
            fitWidth = 24.0
            fitHeight = 24.0
        }
        val logoutButton = Button("Logout",logoutIcon).apply {
            maxWidth = Double.MAX_VALUE
            styleClass.add("logout-button")
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

        val animatedBackground = AnimatedBackground(400.0, 300.0)

        val root = StackPane(buttonBox).apply {
            alignment = Pos.CENTER
            padding = Insets(50.0)
        }
        val layout = StackPane(animatedBackground, root)
        val scene = Scene(layout, 400.0, 300.0).apply {
            stylesheets.add(javaClass.getResource("/styles2.css")?.toExternalForm())
        }
        return scene
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
