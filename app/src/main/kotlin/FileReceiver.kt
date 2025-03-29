import java.io.*
import java.net.Socket

class FileReceiver(private val socket: Socket, private val username: String) {
    private val dataInput = DataInputStream(socket.getInputStream())

    fun receiveFile(fileName: String, fileSize: Long) {
        val receivedDir = File("./users/$username/Received").apply { mkdirs() }
        val file = File(receivedDir, fileName)

        FileOutputStream(file).use { fos ->
            val buffer = ByteArray(65536) // Buffer 64KB giúp truyền nhanh hơn
            var totalRead: Long = 0

            try {
                while (totalRead < fileSize) {
                    val bytesRead = dataInput.read(buffer)
                    if (bytesRead == -1) throw IOException("Connection lost while receiving file!")

                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    // Log tiến trình
                    println("📥 Receiving $fileName: ${((totalRead * 100) / fileSize)}%")
                }

                // Kiểm tra nếu file bị mất dữ liệu
                if (file.length() != fileSize) {
                    throw IOException("Incomplete file! Expected $fileSize bytes, got ${file.length()} bytes")
                }

                println("✅ File received successfully: ${file.absolutePath}")
            } catch (e: IOException) {
                println("❌ Error receiving file: ${e.message}")
                file.delete() // Xóa file bị lỗi để tránh hỏng dữ liệu
            }
        }
    }
}
