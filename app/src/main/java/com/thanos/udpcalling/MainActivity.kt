package com.thanos.udpcalling

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

class MainActivity : AppCompatActivity() {
    private var port = 0
    private lateinit var startCallButton: Button
    private lateinit var endCallButton: Button
    private lateinit var ipAddressEditText: EditText
    private lateinit var portEditText: EditText
    private lateinit var deviceIpTextView: TextView
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var isCalling = false
    private var callJob: Job? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val END_CALL_MESSAGE = "END_CALL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startCallButton = findViewById(R.id.startCallButton)
        endCallButton = findViewById(R.id.endCallButton)
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        portEditText = findViewById(R.id.portEditText)
        deviceIpTextView = findViewById(R.id.deviceIpTextView)

        // Display the device IP address
        deviceIpTextView.text = "Device IP: ${getDeviceIpAddress()}"

        // Check for audio recording permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            setupButtons()
        }
    }

    private fun setupButtons() {
        startCallButton.setOnClickListener {
            val ipAddress = ipAddressEditText.text.toString()
            val portText = portEditText.text.toString()
            if (ipAddress.isEmpty() || portText.isEmpty()) {
                Toast.makeText(this, "Please enter IP address and port", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            port = portText.toInt()

            if (!isCalling) {
                isCalling = true
                startCallButton.visibility = Button.GONE
                endCallButton.visibility = Button.VISIBLE

                callJob = coroutineScope.launch {
                    startCall(ipAddress)
                }
            }
        }

        endCallButton.setOnClickListener {
            isCalling = false
            callJob?.cancel()
            startCallButton.visibility = Button.VISIBLE
            endCallButton.visibility = Button.GONE

            coroutineScope.launch {
                sendEndCallMessage(ipAddressEditText.text.toString(), port)
            }
        }
    }
    @SuppressLint("MissingPermission")
    private suspend fun startCall(ipAddress: String) {
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(ipAddress)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            val audioTrack = AudioTrack(
                AudioFormat.CHANNEL_OUT_MONO,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            audioRecord.startRecording()
            audioTrack.play()

            val receiveJob = launch {
                try {
                    val receiveSocket = DatagramSocket(port)
                    val buffer = ByteArray(bufferSize)
                    while (isCalling) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        receiveSocket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        if (message == END_CALL_MESSAGE) {
                            withContext(Dispatchers.Main) {
                                endCallUIUpdate()
                            }
                            isCalling = false
                            break
                        }
                        audioTrack.write(packet.data, 0, packet.length)
                    }
                    receiveSocket.close()
                } catch (e: SocketException) {
                    // Handle socket closed exception
                }
            }

            try {
                while (isCalling) {
                    val buffer = ByteArray(bufferSize)
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read, address, port)
                        socket.send(packet)
                    }
                }
                sendEndCallMessage(ipAddress, port)
            } finally {
                audioRecord.stop()
                audioRecord.release()
                audioTrack.stop()
                audioTrack.release()
                socket.close()
                receiveJob.cancel()
                withContext(Dispatchers.Main) {
                    endCallUIUpdate()
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

    private fun endCallUIUpdate() {
        startCallButton.visibility = Button.VISIBLE
        endCallButton.visibility = Button.GONE
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return Formatter.formatIpAddress(wifiInfo.ipAddress)
    }

    override fun onDestroy() {
        super.onDestroy()
        isCalling = false
        callJob?.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupButtons()
                } else {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}