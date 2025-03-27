import javafx.animation.AnimationTimer
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.CycleMethod
import javafx.scene.paint.LinearGradient
import javafx.scene.paint.Stop
import kotlin.math.sin

class AnimatedBackground(private val width: Double, private val height: Double) : Pane() {
    private val canvas = Canvas(width, height)
    private val gc: GraphicsContext = canvas.graphicsContext2D
    private var time = 0.0

    init {
        children.add(canvas)
        object : AnimationTimer() {
            override fun handle(now: Long) {
                time += 0.01
                drawBackground()
            }
        }.start()
    }

    private fun drawBackground() {
        val baseHue = 220.0
        val variation = 60.0
        val saturation = 0.8
        val brightness = 0.3 + 0.2 * sin(time)

        val hue1 = baseHue + variation * sin(time)
        val hue2 = hue1 + 100.0

        val color1 = Color.hsb(hue1, saturation, brightness)
        val color2 = Color.hsb(hue2, saturation, brightness)

        val gradient = LinearGradient(
            1.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
            listOf(Stop(0.0, color1), Stop(1.0, color2))
        )

        gc.fill = gradient
        gc.fillRect(0.0, 0.0, width, height)
    }
}
