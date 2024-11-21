package RonaldoWalker.subscriber

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var client: Mqtt5BlockingClient? = null
    private val brokerHost = "broker-816036438.sundaebytestt.com"
    private val brokerPort = 1883
    private val topic = "assignment/location"

    private lateinit var databaseHelper: DatabaseHelper

    private lateinit var mMap: GoogleMap
    private val pointsList = mutableListOf<LatLng>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the database helper
        databaseHelper = DatabaseHelper(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Adjust window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize and connect the MQTT client in a background thread
        setupMqttClient()
    }

    private fun setupMqttClient() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Build and connect the blocking MQTT client
                client = Mqtt5Client.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost(brokerHost)
                    .serverPort(brokerPort)
                    .build()
                    .toBlocking()

                client?.connect()
                showToast("Connected to MQTT broker")

                // Subscribe to the topic
                client?.subscribeWith()
                    ?.topicFilter(topic)
                    ?.send()
                showToast("Subscribed to topic: $topic")

                // Start listening for messages
                listenForMessages()

            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun listenForMessages() {
        try {
            val publishFlow = client?.publishes(MqttGlobalPublishFilter.ALL)
            if (publishFlow == null) {
                showToast("Unable to get MQTT publishes flow")
                return
            }

            while (true) {
                val message = publishFlow.receive()
                val payload = String(message.payloadAsBytes ?: ByteArray(0), StandardCharsets.UTF_8)

                // Parse the message
                val parsedData = parseMessage(payload)

                // Save to database and update map
                if (parsedData.isNotEmpty()) {
                    val studentId = parsedData["StudentID"]!!
                    val latitude = parsedData["Latitude"]!!.toDouble()
                    val longitude = parsedData["Longitude"]!!.toDouble()
                    val speed = parsedData["Speed"]!!.toDouble()

                    databaseHelper.addLocationToDatabase(studentId, latitude, longitude, speed)
                    showToast("Data saved to database for StudentID: $studentId")

                    updateMap(LatLng(latitude, longitude), studentId)
                }
            }
        } catch (e: Exception) {
            showToast("Error while receiving messages: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun parseMessage(message: String): Map<String, String> {
        return try {
            val parts = message.split(", ")
            val studentId = parts[0].split(": ")[1]
            val latitude = parts[1].split(": ")[1]
            val longitude = parts[2].split(": ")[1]
            val speed = parts[3].split(": ")[1]

            mapOf(
                "StudentID" to studentId,
                "Latitude" to latitude,
                "Longitude" to longitude,
                "Speed" to speed
            )
        } catch (e: Exception) {
            //showToast("Error parsing message: ${e.message}")
            emptyMap()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client?.disconnect()
                showToast("Disconnected from MQTT broker")
            } catch (e: Exception) {
                showToast("Error during disconnection: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    private fun updateMap(location: LatLng, studentId: String) {
        runOnUiThread {
            pointsList.add(location)

            // Draw the polyline
            val polylineOptions = PolylineOptions()
                .addAll(pointsList)
                .color(Color.BLUE)
                .width(5f)
                .geodesic(true)
            mMap.addPolyline(polylineOptions)

            // Adjust camera to include all points
            val bounds = LatLngBounds.builder()
            pointsList.forEach { bounds.include(it) }
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))

            // Update the marker with current location
            mMap.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(location).title("Student ID: $studentId"))
        }
    }


}
