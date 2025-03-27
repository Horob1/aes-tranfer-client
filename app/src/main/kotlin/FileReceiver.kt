import java.io.*
import java.net.Socket

class FileReceiver(private val socket: Socket, private val username: String) {
    private val inputStream: InputStream = socket.getInputStream()

    fun receiveFile(fileName: String, fileSize: Long) {
        val receivedDir = File("./users/$username/Received")
        if (!receivedDir.exists()) receivedDir.mkdirs() // Tạo thư mục nếu chưa có

        val file = File(receivedDir, fileName)

        FileOutputStream(file).use { fos ->
            val buffer = ByteArray(4096) // Buffer 4KB
            var totalRead: Long = 0
            var bytesRead: Int

            try {
                while (totalRead < fileSize) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) throw IOException("Lost connection while receiving file!") // Nếu mất kết nối, báo lỗi ngay

                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }

                println("✅ File received successfully.: ${file.absolutePath}")
            } catch (e: IOException) {
                println("❌ Error receiving file: ${e.message}")
                file.delete() // Nếu lỗi xảy ra, xóa file để tránh file bị hỏng
            }
        }
    }
}
