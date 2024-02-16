package com.example.health_tracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.health_tracker.ui.theme.HealthTrackerTheme
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File


class DoctorActivity : AppCompatActivity() {
    private var userId: String? = null

    private val mqttServerUri = "tcp://test.mosquitto.org:1883"
    private val mqttClientId =  MqttClient.generateClientId()
    private val mqttTopics = arrayOf("/hr", "/sys", "/dia", "/spo2")

    private lateinit var mqttClient: MqttClient

    private var isConnected by mutableStateOf(false)
    private var isOutOfRangeHr by mutableStateOf(false)
    private var isOutOfRangeSys by mutableStateOf(false)
    private var isOutOfRangeDia by mutableStateOf(false)
    private var isOutOfRangeSpo2 by mutableStateOf(false)
    private var hrValue by mutableStateOf(0)
    private var sysValue by mutableStateOf(0)
    private var diaValue by mutableStateOf(0)
    private var spo2Value by mutableStateOf(0f)
    private val CHANNEL_ID = "my channel"
    private var lastNotificationTimes: MutableMap<String, Long> = mutableMapOf()
    private val notificationInterval = 30 * 1000
    private lateinit var mainScope: CoroutineScope


    object AppContextProvider {
        lateinit var appContext: Context
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val persistenceDirectory = File(applicationContext.filesDir, "mqtt-persistence")
        persistenceDirectory.mkdirs() // Crea la directory se non esiste

        // Inizializzazione dell'istanza di MqttDefaultFilePersistence
        val persistence = MqttDefaultFilePersistence(persistenceDirectory.absolutePath)

        // Inizializzazione del client MQTT
        mqttClient = MqttClient(mqttServerUri, mqttClientId, persistence)

        mainScope = CoroutineScope(Dispatchers.Main)

        setContentView(R.layout.activity_doctor)

        // Inizializza il contesto dell'applicazione
        AppContextProvider.appContext = applicationContext

        val healthDataComposeView: ComposeView = findViewById(R.id.healthDataComposeView)

        // Chiama HealthData() in ComposeView
        healthDataComposeView.setContent {
            HealthData()
        }

        connectToMqttBroker()

        FirebaseApp.initializeApp(this)
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "FCM Token: $token")
        })

        // Controlla se l'utente è autenticato
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            userId = currentUser.uid
        } else {
            // Se l'utente non è autenticato, rimane nella pagina di login
            startActivity(Intent(this@DoctorActivity, LoginActivity::class.java))
        }
        createNotificationChannel()
    }

    private fun connectToMqttBroker() {
        if (!isConnected) {
            try {
                val persistenceDirectory = File(applicationContext.filesDir, "mqtt-persistence")
                val persistence = MqttDefaultFilePersistence(persistenceDirectory.absolutePath)
                mqttClient = MqttClient(mqttServerUri, mqttClientId, persistence)
                val mqttConnectOptions = MqttConnectOptions()

                mqttConnectOptions.isAutomaticReconnect = true
                mqttConnectOptions.isCleanSession = true
                mqttClient.connect(mqttConnectOptions)

                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        isConnected = false
                        Log.e("MQTT_CONNECTION_LOST", "Connessione persa al broker MQTT: ${cause?.message}")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        message?.let {
                            val data = String(it.payload)
                            Log.d("MQTT_MESSAGE", "Messaggio ricevuto sul topic: $topic - Contenuto: $data")
                            processData(topic, data)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    }
                })

                // Sottoscrizione ai topic
                for (topic in mqttTopics) {
                    mqttClient.subscribe(topic)
                }

                isConnected = true
            } catch (e: MqttException) {
                isConnected = false
                Log.e("MQTT_ERROR", "Errore durante la connessione al broker MQTT: ${e.message}", e)
            }
        }
    }

    private fun disconnectFromMqttBroker() {
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Crea il canale di notifica
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("HealthTracker", "Notification channel created!")
        }
    }

    private fun processData(topic: String?, data: String) {
        Log.d("PROCESS_DATA", "Data received on topic $topic: $data")
        try {
            when (topic) {
                "/sys" -> {
                    sysValue = data.trim().toInt()
                    Log.d("DATA_PROCESSING", "sysValue: $sysValue")
                    isOutOfRangeSys = (sysValue < 90) || (sysValue >130)
                    Log.d("DATA_PROCESSING", "isOutOfRangeSys: $isOutOfRangeSys")

                }
                "/dia" -> {
                    diaValue = data.trim().toInt()
                    Log.d("DATA_PROCESSING", "diaValue: $diaValue")
                    isOutOfRangeDia = (diaValue < 60) || (diaValue >80)

                }
                "/spo2" -> {
                    spo2Value = data.trim().toFloat()
                    Log.d("DATA_PROCESSING", "spo2Value: $spo2Value")
                    isOutOfRangeSpo2 = (spo2Value < 95)

                }
                "/hr" -> {
                    hrValue = data.trim().toInt()
                    Log.d("DATA_PROCESSING", "hrValue: $hrValue")
                    isOutOfRangeHr = (hrValue < 40) || (hrValue >200)

                }
            }
            checkValue(isOutOfRangeHr, isOutOfRangeSys, isOutOfRangeDia, isOutOfRangeSpo2)
        } catch (e: NumberFormatException) {
            Log.e("DATA_PROCESSING_ERROR", "Errore durante l'elaborazione dei dati: ${e.message}")
        }
    }

    @Composable
    fun HealthData() {

        // Variabile per gestire la visualizzazione delle informazioni
        var infoDialogVisible by remember { mutableStateOf(false) }
        val infoButton = findViewById<Button>(R.id.infoButton)

        infoButton.setOnClickListener {
            infoDialogVisible = true
        }

        val logoutButton = findViewById<Button>(R.id.logoutButton)

        logoutButton.setOnClickListener {
            // Chiama la funzione signOut()
            FirebaseAuth.getInstance().signOut()

            // Torna alla pagina di login
            startActivity(Intent(this@DoctorActivity, LoginActivity::class.java))
            finish() // Optional: Close the current activity
        }

        Column(
            modifier = Modifier.background(color = Color(0xFFACD8E5)).fillMaxSize()
        ) {
            Text(
                text = "Health Tracker",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF3F51B5),
                modifier = Modifier.padding(top = 40.dp).align(Alignment.CenterHorizontally) // Aggiungi spazio sopra il titolo
            )
            Greeting(
                "Health-Tracker",
                isConnected,
                isOutOfRangeHr,
                isOutOfRangeSys,
                isOutOfRangeDia,
                isOutOfRangeSpo2,
                hrValue,
                sysValue,
                diaValue,
                spo2Value,

            )


            // Mostra un AlertDialog quando infoDialogVisible è true
            if (infoDialogVisible) {
                AlertDialog(
                    onDismissRequest = {
                        // Chiudi il dialog quando si preme al di fuori di esso
                        infoDialogVisible = false
                    },
                    title = {
                        Text(text = "Informazioni")
                    },
                    text = {
                        Column {
                            Text(
                                buildAnnotatedString {
                                    withStyle(style = SpanStyle(color = Color(0xFF000000))) {
                                        append("Se un parametro è riportato in ")
                                    }
                                    withStyle(style = SpanStyle(color = Color(0xFF70e000), fontWeight = FontWeight.Bold)) {
                                        append("verde")
                                    }
                                    withStyle(style = SpanStyle(color = Color(0xFF000000))) {
                                        append(", allora è all’interno del suo range di normalità, in caso contrario viene visualizzato in ")
                                    }
                                    withStyle(style = SpanStyle(color = Color(0xFFef233c), fontWeight = FontWeight.Bold)) {
                                        append("rosso.\n\n")
                                    }
                                    withStyle(style = SpanStyle(color = Color(0xFF000000))) {
                                        append("I range di normalità per ciascun parametro sono:\n\n")
                                        append(" - Frequenza cardiaca: 40-200 bpm\n")
                                        append(" - Pressione sistolica: 90-130 mmHg\n")
                                        append(" - Pressione diastolica: 60-80 mmHg\n")
                                        append(" - Saturazione dell’ossigeno: > 95%\n")
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor =  Color(0xFF3F51B5) // Imposta il colore di sfondo del bottone
                            ),
                            onClick = {
                                // Chiudi il dialog quando si preme il pulsante di conferma
                                infoDialogVisible = false
                            }
                        ) {
                            Text(text = "Chiudi")
                        }
                    }
                )
            }
        }

    }


    fun checkValue(outOfRangeHr: Boolean, outOfRangeSys: Boolean, outOfRangeDia: Boolean, outOfRangeSpo2: Boolean) {
        checkVariable("Frequenza cardiaca", outOfRangeHr)
        checkVariable("Pressione sistolica", outOfRangeSys)
        checkVariable("Pressione diastolica", outOfRangeDia)
        checkVariable("Saturazione dell'ossigeno", outOfRangeSpo2)
    }

    private fun checkVariable(variableName: String, outOfRange: Boolean) {
        val currentTime = System.currentTimeMillis()

        // Verifica se è passato abbastanza tempo dall'ultima notifica per questa variabile
        if (currentTime - lastNotificationTimes.getOrDefault(variableName, 0) >= notificationInterval) {
            if (outOfRange) {
                sendNotification("Valori anomali", "$variableName fuori dal range di normalità!")
                // Aggiorna il timestamp dell'ultima notifica per questa variabile
                lastNotificationTimes[variableName] = currentTime
            }
        }
    }


    private fun sendNotification(title: String, message: String) {
        val notificationId = System.currentTimeMillis().toInt()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(this, R.color.colorRed))
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@DoctorActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("HealthTracker", "Inside sendNotification(): Permission denied :(")
            } else {
                Log.d("HealthTracker", "Inside sendNotification(): before notify()")
                notify(notificationId, builder.build())
                Log.d("HealthTracker", "Inside sendNotification(): after notify()")
            }

        }
    }



    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        HealthTrackerTheme {
            Greeting(
                "Heath-Tracker",
                isConnected = false,
                isOutOfRangeHr = false,
                isOutOfRangeSys = false,
                isOutOfRangeDia = false,
                isOutOfRangeSpo2 = false,
                hrValue = 0,
                sysValue = 0,
                diaValue = 0,
                spo2Value = 0f,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        disconnectFromMqttBroker()
    }

    @Composable
    fun Greeting(
        name: String,
        isConnected: Boolean,
        isOutOfRangeHr: Boolean,
        isOutOfRangeSys: Boolean,
        isOutOfRangeDia: Boolean,
        isOutOfRangeSpo2: Boolean,
        hrValue: Int,
        sysValue: Int,
        diaValue: Int,
        spo2Value: Float,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutOfRangeValue("Pressione sistolica", sysValue, isOutOfRangeSys, Modifier.padding(8.dp).weight(1f))
                OutOfRangeValue("Pressione diastolica", diaValue, isOutOfRangeDia, Modifier.padding(8.dp).weight(1f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutOfRangeValue("Frequenza cardiaca", hrValue, isOutOfRangeHr, Modifier.padding(8.dp).weight(1f))
                OutOfRangeValue("Saturazione dell'ossigeno (SpO₂)", spo2Value, isOutOfRangeSpo2, Modifier.padding(8.dp).weight(1f))
            }

            }

    }


    private var isDialogVisibleMap: MutableMap<String, Boolean> = mutableMapOf()

    @Composable
    fun OutOfRangeValue(
        label: String,
        value: Number,
        outOfRange: Boolean,
        modifier: Modifier = Modifier
    ) {
        val backgroundColor = if (outOfRange) {
            Color(android.graphics.Color.parseColor("#ef233c")) //rosso se outOfRange è true
        } else {
            Color(android.graphics.Color.parseColor("#70e000")) //verde se outOfRange è false
        }
        var selectedVariable: String? by remember { mutableStateOf(null) }
        var isDialogVisible by remember { mutableStateOf(isDialogVisibleMap.getOrDefault(label, false)) }

        Box(
            modifier = modifier
                .width(150.dp)
                .height(150.dp)
                .clickable {
                    isDialogVisible = true
                    selectedVariable = label
                }
                .background(color = backgroundColor, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$label\n\n $value",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
            if (isDialogVisible) {
                AlertDialog(
                    onDismissRequest = {
                        // Chiudi il dialog quando si preme al di fuori di esso
                        isDialogVisible = false
                    },
                    title = {
                        Text(text = "$label")
                    },
                    text = {
                        Text("Seleziona un'opzione:")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val iframeUrl = when (selectedVariable) {
                                    "Pressione sistolica" -> "https://stem.ubidots.com/app/dashboards/public/widget/VEDFP2lyihIDS_wa4XSubjCsd159Ej8qC_Shvb0Dsj8?from=1705309599118&to=1707901599118&datePicker=true"
                                    "Pressione diastolica" -> "https://stem.ubidots.com/app/dashboards/public/widget/lsQbP9VRrhitmHfxy0_60YoNhdd9lQlFpmQm4pFgWsE?from=1705225110794&to=1707817110794&datePicker=true"
                                    "Frequenza cardiaca" -> "https://stem.ubidots.com/app/dashboards/public/widget/Si0eBmBqA7WkWaz0gwnUZ_4l_WnVd5uBmR4FForUzLM?from=1705225110794&to=1707817110794&datePicker=true"
                                    "Saturazione dell'ossigeno (SpO₂)" -> "https://stem.ubidots.com/app/dashboards/public/widget/mont6BtE3ASgBngEyXA84YgHU93Pz5SMpPwilc-3IAw?from=1705225110794&to=1707817110794&datePicker=true"
                                    else -> ""
                                }
                                startActivity(Intent(this@DoctorActivity, IFrameActivity::class.java).apply {
                                    putExtra("iframeUrl", iframeUrl)
                                })
                                isDialogVisible = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor =  Color(0xFF3F51B5) // Imposta il colore di sfondo del bottone

                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                        ) {
                            Text(text = "Andamento")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                val iframeUrl = when (selectedVariable) {
                                    "Pressione sistolica" -> "https://stem.ubidots.com/app/dashboards/public/widget/ynX6hij6mlbp_e7lV7yBrrnUa8iTbpSnvjZyNU_nAwY?from=1705225612240&to=1707817612240&datePicker=true"
                                    "Pressione diastolica" -> "https://stem.ubidots.com/app/dashboards/public/widget/3DTzJ6hzXkXk_glzOIoW9k3q9f84VScKUJAh3A62w18?from=1705225612240&to=1707817612240&datePicker=true"
                                    "Frequenza cardiaca" -> "https://stem.ubidots.com/app/dashboards/public/widget/dhBNdMBYwGf8RW6LV9dP4VXcfrOkmESIS-FePXZpDuE?from=1705225612240&to=1707817612240&datePicker=true"
                                    "Saturazione dell'ossigeno (SpO₂)" -> "https://stem.ubidots.com/app/dashboards/public/widget/CBnQHDY_ly1hQQRp0kbYP7qIaXVwUmq8_o_AMXVh5pA?from=1705225612240&to=1707817612240&datePicker=true"
                                    else -> ""
                                }
                                startActivity(Intent(this@DoctorActivity, IFrameActivity::class.java).apply {
                                    putExtra("iframeUrl", iframeUrl)
                                })
                                isDialogVisible = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor =  Color(0xFF3F51B5)

                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                        ) {
                            Text(text = "Media")
                        }
                    }
                )
            }
        }
    }







}



