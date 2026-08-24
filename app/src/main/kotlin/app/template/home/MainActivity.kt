package com.chronos.examcountdown

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.media.*
import android.os.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.*
import java.util.*
import kotlin.concurrent.thread

// =====================
// DATA MODELS
// =====================
data class Exam(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val code: String,
    val targetTimeMillis: Long
)

data class TimeLeft(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isPast: Boolean
)

fun calculateTimeLeft(targetMillis: Long): TimeLeft {
    val diff = targetMillis - System.currentTimeMillis()

    if (diff <= 0) return TimeLeft(0, 0, 0, 0, true)

    val seconds = (diff / 1000) % 60
    val minutes = (diff / (1000 * 60)) % 60
    val hours = (diff / (1000 * 60 * 60)) % 24
    val days = diff / (1000 * 60 * 60 * 24)

    return TimeLeft(days, hours, minutes, seconds, false)
}

// =====================
// WIDGET DATA STORE
// =====================
object WidgetDataStore {
    private const val PREF = "widget_pref"

    fun save(context: Context, exam: Exam) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("title", exam.title)
            .putString("code", exam.code)
            .putLong("time", exam.targetTimeMillis)
            .apply()
    }

    fun get(context: Context): Triple<String, String, Long> {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Triple(
            p.getString("title", "Exam")!!,
            p.getString("code", "CODE")!!,
            p.getLong("time", System.currentTimeMillis() + 86400000)
        )
    }
}

// =====================
// SOUND GENERATOR
// =====================
class AmbientSoundGenerator {
    private var track: AudioTrack? = null
    private var playing = false

    fun toggle(): Boolean {
        return if (playing) {
            stop(); false
        } else {
            start(); true
        }
    }

    private fun start() {
        playing = true
        thread {
            val rate = 44100
            val size = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                size,
                AudioTrack.MODE_STREAM
            )

            track?.play()
            val buffer = ShortArray(size)
            val random = Random()

            while (playing) {
                for (i in buffer.indices) {
                    val white = random.nextFloat() * 2 - 1
                    buffer[i] = (white * 300).toInt().toShort()
                }
                track?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        playing = false
        track?.stop()
        track?.release()
    }
}

// =====================
// MAIN ACTIVITY
// =====================
class MainActivity : ComponentActivity() {

    private val sound = AmbientSoundGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppUI(sound) { pinWidget() }
        }
    }

    private fun pinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(this)
            val provider = ComponentName(this, ExamWidgetReceiver::class.java)

            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(this, "Not supported", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.stop()
    }
}

// =====================
// UI (COMPOSE)
// =====================
@Composable
fun AppUI(sound: AmbientSoundGenerator, onPin: () -> Unit) {

    val exam = remember {
        Exam(
            title = "Math Exam",
            code = "MATH 101",
            targetTimeMillis = System.currentTimeMillis() + 86400000
        )
    }

    var time by remember { mutableStateOf(calculateTimeLeft(exam.targetTimeMillis)) }

    LaunchedEffect(Unit) {
        while (true) {
            time = calculateTimeLeft(exam.targetTimeMillis)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
    ) {

        Text(exam.title, color = Color.White, fontSize = 22.sp)

        Spacer(Modifier.height(20.dp))

        Text(
            "${time.days}d ${time.hours}h ${time.minutes}m ${time.seconds}s",
            color = Color.Green,
            fontSize = 26.sp
        )

        Spacer(Modifier.height(20.dp))

        Button(onClick = onPin) {
            Text("Add Widget")
        }
    }
}

// =====================
// WIDGET
// =====================
class ExamCountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val (title, code, time) = WidgetDataStore.get(context)
        val t = calculateTimeLeft(time)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.Black))
                    .padding(10.dp)
            ) {

                Text(code)

                Text(title)

                Text("${t.days}d ${t.hours}h ${t.minutes}m")
            }
        }
    }
}

class ExamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ExamCountdownWidget()
}