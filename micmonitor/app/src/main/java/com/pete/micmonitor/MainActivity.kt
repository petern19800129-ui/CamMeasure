package com.pete.micmonitor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.*
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var audioManager: AudioManager
    private lateinit var inputSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var gainSeek: SeekBar
    private lateinit var gainText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var refreshButton: Button

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    private var inputChoices: List<InputChoice> = emptyList()
    private var outputChoices: List<AudioDeviceInfo> = emptyList()

    data class InputChoice(
        val label: String,
        val device: AudioDeviceInfo?,
        val source: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setContentView(buildUi())
        requestPermissionsIfNeeded()
        refreshDevices()
    }

    private fun buildUi(): View {
        val pad = (20 * resources.displayMetrics.density).roundToInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Mic Monitor"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Listen to a selected microphone through a headset with adjustable gain."
            textSize = 15f
            setPadding(0, 6, 0, 18)
        })

        root.addView(label("Microphone input"))
        inputSpinner = Spinner(this)
        root.addView(inputSpinner, matchWrap())

        root.addView(label("Audio output").apply { setPadding(0, 18, 0, 4) })
        outputSpinner = Spinner(this)
        root.addView(outputSpinner, matchWrap())

        gainText = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 22, 0, 4)
        }
        root.addView(gainText)

        gainSeek = SeekBar(this).apply {
            max = 36
            progress = 12
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateGainText()
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(gainSeek, matchWrap())
        updateGainText()

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }
        startButton = Button(this).apply {
            text = "START"
            setOnClickListener { if (running.get()) stopMonitor() else startMonitor() }
        }
        refreshButton = Button(this).apply {
            text = "REFRESH DEVICES"
            setOnClickListener { refreshDevices() }
        }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(refreshButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        statusText = TextView(this).apply {
            text = "Stopped"
            textSize = 15f
            setPadding(0, 18, 0, 0)
        }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "Tip: +6 dB ≈ 2×, +12 dB ≈ 4×, +20 dB ≈ 10×. High gain can clip or feed back. Bluetooth-headset microphones normally use call/HFP mode, which can reduce audio quality."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 4, 0, 4)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) refreshDevices()
    }

    private fun refreshDevices() {
        if (running.get()) stopMonitor()

        val inputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        val choices = mutableListOf<InputChoice>()
        choices += InputChoice("Phone mic — normal", null, MediaRecorder.AudioSource.MIC)
        choices += InputChoice("Phone mic — camcorder/rear preference", null, MediaRecorder.AudioSource.CAMCORDER)

        inputs.forEach { d ->
            choices += InputChoice("${deviceName(d)} [input]", d, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }
        inputChoices = choices.distinctBy { "${it.label}:${it.device?.id}:${it.source}" }

        outputChoices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }

        inputSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, inputChoices.map { it.label })
        outputSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, outputChoices.map { deviceName(it) })

        val btIndex = outputChoices.indexOfFirst { isBluetooth(it) }
        if (btIndex >= 0) outputSpinner.setSelection(btIndex)

        statusText.text = "Found ${inputChoices.size} input choices and ${outputChoices.size} outputs."
    }

    private fun deviceName(d: AudioDeviceInfo): String {
        val product = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio device"
        val type = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
            else -> "type ${d.type}"
        }
        return "$product — $type"
    }

    private fun isBluetooth(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
        else -> false
    }

    private fun isCommunicationBluetooth(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> true
        else -> false
    }

    private fun updateGainText() {
        val db = gainDb()
        val factor = dbToGain(db)
        gainText.text = "Gain: ${if (db >= 0) "+" else ""}$db dB  (×${"%.2f".format(factor)})"
    }

    private fun gainDb(): Int = gainSeek.progress - 12
    private fun dbToGain(db: Int): Float = 10.0.pow(db / 20.0).toFloat()

    private fun startMonitor() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsIfNeeded()
            statusText.text = "Microphone permission is required."
            return
        }
        if (outputChoices.isEmpty()) {
            statusText.text = "No audio output device found."
            return
        }

        val inChoice = inputChoices.getOrNull(inputSpinner.selectedItemPosition) ?: return
        val outDevice = outputChoices.getOrNull(outputSpinner.selectedItemPosition) ?: return

        running.set(true)
        startButton.text = "STOP"
        inputSpinner.isEnabled = false
        outputSpinner.isEnabled = false
        refreshButton.isEnabled = false

        worker = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runAudioLoop(inChoice, outDevice)
        }.apply { start() }
    }

    private fun runAudioLoop(input: InputChoice, output: AudioDeviceInfo) {
        val sampleRate = 16000
        val channelIn = AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val recordMin = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding).coerceAtLeast(2048)
        val playMin = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding).coerceAtLeast(2048)
        val bufferBytes = maxOf(recordMin, playMin) * 2

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (isCommunicationBluetooth(output)) {
                audioManager.setCommunicationDevice(output)
            }

            val formatIn = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelIn)
                .build()

            val r = AudioRecord.Builder()
                .setAudioSource(input.source)
                .setAudioFormat(formatIn)
                .setBufferSizeInBytes(bufferBytes)
                .build()
            recorder = r

            input.device?.let { r.setPreferredDevice(it) }

            val formatOut = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelOut)
                .build()

            val p = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(formatOut)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            player = p
            p.setPreferredDevice(output)

            if (r.state != AudioRecord.STATE_INITIALIZED || p.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("Audio input/output could not be initialized")
            }

            r.startRecording()
            p.play()

            runOnUiThread {
                statusText.text = "Live: ${input.label} → ${deviceName(output)}"
            }

            val samples = ShortArray(bufferBytes / 2)
            while (running.get()) {
                val count = r.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                val gain = dbToGain(gainDb())
                for (i in 0 until count) {
                    val amplified = (samples[i] * gain).roundToInt()
                    samples[i] = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                p.write(samples, 0, count, AudioTrack.WRITE_BLOCKING)
            }
        } catch (t: Throwable) {
            runOnUiThread { statusText.text = "Error: ${t.message ?: t.javaClass.simpleName}" }
        } finally {
            cleanupAudio()
            runOnUiThread {
                running.set(false)
                startButton.text = "START"
                inputSpinner.isEnabled = true
                outputSpinner.isEnabled = true
                refreshButton.isEnabled = true
                if (!statusText.text.startsWith("Error:")) statusText.text = "Stopped"
            }
        }
    }

    private fun stopMonitor() {
        running.set(false)
        try { recorder?.stop() } catch (_: Throwable) {}
        try { player?.pause() } catch (_: Throwable) {}
    }

    private fun cleanupAudio() {
        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        try { audioManager.clearCommunicationDevice() } catch (_: Throwable) {}
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    override fun onDestroy() {
        running.set(false)
        cleanupAudio()
        super.onDestroy()
    }
}
