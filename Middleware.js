const mqtt = require('mqtt');

// Definisci i dettagli del broker MQTT
const mqtt_server = 'mqtt://test.mosquitto.org'; // Indirizzo del broker MQTT
const mqtt_port = 1883; // Porta del broker MQTT

// Definisci i dettagli del client MQTT per Ubidots
const ubidots_token = 'BBUS-Kmna5ZDX96ZwEdaurdUuGgikEVebQP'; // Token di accesso a Ubidots
const ubidots_topic = '/v1.6/devices/arduinounor4wifi'; // Topic per Ubidots

// Crea un client MQTT per il broker MQTT
const mqtt_client = mqtt.connect(mqtt_server + ':' + mqtt_port);

// Crea un client MQTT per Ubidots
const ubidots_client = mqtt.connect('mqtt://industrial.api.ubidots.com', {
  username: ubidots_token,
  password: ''
});

// Variabili per memorizzare i valori
let sysValue, diaValue, hrValue, spo2Value, latValue, lngValue;

// Callback che gestisce la connessione al broker MQTT
mqtt_client.on('connect', function () {
  console.log('Connesso al broker MQTT');
  
  // Sottoscrivi ai topic di interesse
  mqtt_client.subscribe('/sys');
  mqtt_client.subscribe('/dia');
  mqtt_client.subscribe('/hr');
  mqtt_client.subscribe('/spo2');
  mqtt_client.subscribe('/lat');
  mqtt_client.subscribe('/lng');
  console.log('Sottoscritto ai topic di interesse');
});

// Callback che gestisce il messaggio ricevuto dal broker MQTT
mqtt_client.on('message', function (topic, message) {

  // Decommentare se si vogliono controllare i valori in tempo reale
  //console.log('Messaggio ricevuto sul topic:', topic);
  //console.log('Valore:', message.toString());

  // Aggiorna i valori in base al topic
  switch (topic) {
    case '/sys':
      sysValue = parseFloat(message.toString());
      break;
    case '/dia':
      diaValue = parseFloat(message.toString());
      break;
    case '/hr':
      hrValue = parseFloat(message.toString());
      break;
    case '/spo2':
      spo2Value = parseFloat(message.toString());
      break;
    case '/lat':
      latValue = parseFloat(message.toString());
      break;
    case '/lng':
      lngValue = parseFloat(message.toString());
      break;
    default:
      console.log('Topic non riconosciuto:', topic);
  }

  // Se tutti i valori sono stati ricevuti, invia il payload a Ubidots
  if (sysValue !== undefined && diaValue !== undefined && hrValue !== undefined && spo2Value !== undefined && latValue !== undefined && lngValue !== undefined) {
    const payload = {
      sys: { value: sysValue, context: { lat: latValue, lng: lngValue } },
      dia: { value: diaValue, context: { lat: latValue, lng: lngValue } },
      hr: { value: hrValue, context: { lat: latValue, lng: lngValue } },
      spo2: { value: spo2Value, context: { lat: latValue, lng: lngValue } }
    };

    // Converti il payload in formato JSON
    const jsonData = JSON.stringify(payload);

    // Pubblica il messaggio su Ubidots
    ubidots_client.publish(ubidots_topic, jsonData, function (err) {
      if (err) {
        console.error('Errore durante la pubblicazione del messaggio su Ubidots:', err);
      } else {
        //console.log('Messaggio pubblicato su Ubidots');
      }
    });

    // Resetta i valori
    sysValue = diaValue = hrValue = spo2Value = latValue = lngValue = undefined;
  }
});

// Gestione degli errori del broker MQTT
mqtt_client.on('error', function (error) {
  console.log('Errore nel broker MQTT:', error);
});

// Gestione degli errori di Ubidots
ubidots_client.on('error', function (error) {
  console.error('Errore durante la connessione a Ubidots:', error);
});