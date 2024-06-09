package com.thanos.udpcalling

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class ReceiverActivity : AppCompatActivity() {
    private var port = 0
    private lateinit var startListeningButton: Button
    private lateinit var stopListeningButton: Button
    private lateinit var portEditText: EditText
    private lateinit var deviceIpTextView: TextView
    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var isListening = false
    private var listenJob: Job? = null

    companion object {
        private const val END_CALL_MESSAGE = "END_CALL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startListeningButton = findViewById(R.id.startCallButton)
        stopListeningButton = findViewById(R.id.endCallButton)
        portEditText = findViewById(R.id.portEditText)
        deviceIpTextView = findViewById(R.id.deviceIpTextView)

        // Display the device IP address
        deviceIpTextView.text = "Device IP: ${getDeviceIpAddress()}"

        setupButtons()
    }

    private fun setupButtons() {
        startListeningButton.setOnClickListener {
            val portText = portEditText.text.toString()
            if (portText.isEmpty()) {
                Toast.makeText(this, "Please enter port", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            port = portText.toInt()

            if (!isListening) {
                isListening = true
                startListeningButton.visibility = Button.GONE
                stopListeningButton.visibility = Button.VISIBLE

                listenJob = coroutineScope.launch {
                    startListening()
                }
            }
        }

        stopListeningButton.setOnClickListener {
            isListening = false
            listenJob?.cancel()
            startListeningButton.visibility = Button.VISIBLE
            stopListeningButton.visibility = Button.GONE

            coroutineScope.launch {
                sendEndCallMessage(getDeviceIpAddress(), port)
            }
        }
    }

    private suspend fun startListening() {
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket(port)
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioTrack.play()

            try {
                val buffer = ByteArray(bufferSize)
                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message == END_CALL_MESSAGE) {
                        withContext(Dispatchers.Main) {
                            endListeningUIUpdate()
                        }
                        isListening = false
                        break
                    }
                    audioTrack.write(packet.data, 0, packet.length)
                }
            } catch (e: SocketException) {
                // Handle socket closed exception
            } finally {
                audioTrack.stop()
                audioTrack.release()
                socket.close()
                withContext(Dispatchers.Main) {
                    endListeningUIUpdate()
                }
            }
        }
    }

    private suspend fun sendEndCallMessage(ipAddress: String, port: Int) {
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(ipAddress)
            val buffer = END_CALL_MESSAGE.toByteArray()
            val packet = DatagramPacket(buffer, buffer.size, address, port)
            socket.send(packet)
            socket.close()
        }
    }

    private fun endListeningUIUpdate() {
        startListeningButton.visibility = Button.VISIBLE
        stopListeningButton.visibility = Button.GONE
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return Formatter.formatIpAddress(wifiInfo.ipAddress)
    }
}