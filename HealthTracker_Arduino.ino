#include <Wire.h>
#include "max32664.h"
#include <SoftwareSerial.h>
#include "WiFiS3.h"
#include <ArduinoBLE.h>
#include <PubSubClient.h>

// Sensor MAX32664
#define RESET_PIN 4
#define MFIO_PIN 2
#define RAWDATA_BUFFLEN 250

max32664 MAX32664(RESET_PIN, MFIO_PIN, RAWDATA_BUFFLEN);

// A9G module
#define DEBUG true
int PWR_KEY = 9;
int RST_KEY = 6;
int LOW_PWR_KEY = 5;
bool ModuleState = false;
SoftwareSerial mySerial(0, 1);

String lat = "0";
String lng = "0";

// Connettività

// Bluetooth® Low Energy
BLEService btService("19B10000-E8F2-537E-4F6C-D104768A1214");
BLEStringCharacteristic btCharacteristic("19B10001-E8F2-537E-4F6C-D104768A1214", BLERead | BLEWrite | BLENotify, 50);

// WiFi
char ssid[] = "iPhone di Nicole";
char pass[] = "ciaociao";  
WiFiClient wifiClient;

// MQTT broker mosquitto
const char* mqtt_server_mosquitto = "test.mosquitto.org";
const int mqtt_port_mosquitto = 1883;
PubSubClient client_mosquitto(wifiClient);


void setup() {
  Serial.begin(57600);
  Wire.begin();

  // BLE set up
  if (!BLE.begin()) {
    Serial.println("Starting Bluetooth® Low Energy module failed!");
    while (1);
  }
  BLE.setLocalName("Health Tracker");
  BLE.setAdvertisedService(btService);
  btService.addCharacteristic(btCharacteristic);
  BLE.addService(btService);
  btCharacteristic.writeValue("");
  BLE.advertise();

  // A9G set up
  pinMode(PWR_KEY, OUTPUT);
  pinMode(RST_KEY, OUTPUT);
  pinMode(LOW_PWR_KEY, OUTPUT);
  digitalWrite(RST_KEY, LOW);
  digitalWrite(LOW_PWR_KEY, HIGH);
  digitalWrite(PWR_KEY, HIGH);

  mySerial.begin(115200);
  digitalWrite(PWR_KEY, LOW);
  delay(3000);
  digitalWrite(PWR_KEY, HIGH);
  delay(10000);

  ModuleState = moduleStateCheck();
  if (!ModuleState) {
    digitalWrite(PWR_KEY, LOW);
    delay(3000);
    digitalWrite(PWR_KEY, HIGH);
    delay(10000);
    Serial.println("Now turning the A9/A9G on.");
  }

  Serial.println("Maduino A9/A9G Test Begin!");
  sendData("AT+GPS=1", 1000, DEBUG);
  sendData("AT+GPSRD=10", 1000, DEBUG);
  sendData("AT+GPSRD=0", 1000, DEBUG);

  // MAX32664 set up
  loadAlgomodeParameters();
  int result = MAX32664.hubBegin();
  if (result == CMD_SUCCESS) {
    Serial.println("Sensorhub begin!");
  } else {
    while (1) {
      Serial.println("Could not communicate with the sensor! Please make proper connections");
      delay(5000);
    }
  }

  bool ret = MAX32664.startBPTcalibration();
  if (!ret) {
    Serial.println("Use BLE");
  }

  // Comunicazione BLE fallimento calibrazione
  while (!ret) {
    delay(100);
    BLEDevice central = BLE.central();
    if (central.connected()) {
      btCharacteristic.writeValue("Calibration failed");
    }
  };

  delay(1000);
  ret = MAX32664.configAlgoInEstimationMode();
  while (!ret) {
    ret = MAX32664.configAlgoInEstimationMode();
    delay(10000);
  }

  Serial.println("Getting the device ready..");
  delay(1000);


  // Connessione Wi-Fi
  connectToWiFi();

  // Inizializzazione broker MQTT 
  client_mosquitto.setServer(mqtt_server_mosquitto, mqtt_port_mosquitto);
  client_mosquitto.setCallback(callback);
}

void loop() {

  // Controllo connessione Wi-Fi
  while (WiFi.status() != WL_CONNECTED) {
    connectToWiFi();
  }

  // Controllo connessione MQTT broker
  while (!client_mosquitto.connected()) {
    reconnect(client_mosquitto, mqtt_server_mosquitto, "", "", "mosquittoClient");
  }
  client_mosquitto.loop();

  // Comunicazione GPS
  String response = sendData("AT+LOCATION=2", 1000, DEBUG);
  response.replace("AT+LOCATION=2", "");
  response.replace(" ", "");
  if (response.indexOf("NOT FIX") < 0) {
    int idx = findFirstNumericIndex(response);
    int commaIndex = response.indexOf(",");
    if (commaIndex != -1) {
      lat = response.substring(idx, commaIndex);
      lng = response.substring(commaIndex + 1, findFLastNumericIndex(response) + 1);
    }
  }

  // Invio dei dati al broker
  processDataAndSend(lat, lng);

  // Dati inviati ogni 20s
  delay(20000);
}


void handleSerialCommunication() {
  while (mySerial.available() > 0) {
    Serial.write(mySerial.read());
    yield();
  }
  while (Serial.available() > 0) {
    mySerial.write(Serial.read());
    yield();
  }
}

void processDataAndSend(String lat, String lng) {

  // Dati sul monitor seriale e BLE
  processSensorData();
  
  // Invio dei dati al broker MQTT
  sendToBroker(lat, lng);
}

void connectToWiFi() {
  int status = WL_IDLE_STATUS;

  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Communication with WiFi module failed!");
    while (true);
  }

  String fv = WiFi.firmwareVersion();
  if (fv < WIFI_FIRMWARE_LATEST_VERSION) {
    Serial.println("Please upgrade the firmware");
  }

  while (status != WL_CONNECTED) {
    BLEDevice central = BLE.central();

    if (central.connected()) {
      btCharacteristic.writeValue("Attempting to connect to SSID: ");
      btCharacteristic.writeValue(ssid);
      Serial.print("Attempting to connect to SSID: ");
      Serial.println(ssid);
    }
    status = WiFi.begin(ssid, pass);
    Serial.println(status);
    delay(100);
  }
  Serial.println("Connected to WiFi");
}

void reconnect(PubSubClient& client, const char* mqtt_server, const char* mqtt_user, const char* mqtt_password, const char* client_name) {
  while (!client.connected()) {
    if (client.connect(client_name, mqtt_user, mqtt_password)) {
      Serial.println("Connected to MQTT broker");
    } else {
      Serial.print("Failed to connect to MQTT broker, rc=");
      Serial.print(client.state());
      Serial.println(" Trying again in 5 seconds");
      delay(5000);
    }
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");
  for (int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
}

void sendToBroker(String lat, String lng) {

  //Publish MQTT
  if (client_mosquitto.connected()) {
    client_mosquitto.publish("/sys", String(MAX32664.max32664Output.sys).c_str());
    client_mosquitto.publish("/dia", String(MAX32664.max32664Output.dia).c_str());
    client_mosquitto.publish("/hr", String(MAX32664.max32664Output.hr).c_str());
    client_mosquitto.publish("/spo2", String(MAX32664.max32664Output.spo2).c_str());
    client_mosquitto.publish("/lat", lat.c_str());
    client_mosquitto.publish("/lng", lng.c_str());
    Serial.println("Data sent to MQTT broker");
  } else {
    Serial.println("Unable to connect to MQTT broker");
  }
}

void processSensorData() {
  uint8_t num_samples = MAX32664.readSamples();

  // Stampa sul monitor seriale - se connesso
  if (num_samples) {
    Serial.print("sys = ");
    Serial.print(MAX32664.max32664Output.sys);
    Serial.print(", dia = ");
    Serial.print(MAX32664.max32664Output.dia);
    Serial.print(", hr = ");
    Serial.print(MAX32664.max32664Output.hr);
    Serial.print(" spo2 = ");
    Serial.println(MAX32664.max32664Output.spo2);
  }

  // Stampa su BLE - se connesso
  String toBLE = "sys = " + String(MAX32664.max32664Output.sys) + ", dia = " + String(MAX32664.max32664Output.dia) + ", hr = " + String(MAX32664.max32664Output.hr) + " spo2 = " + String(MAX32664.max32664Output.spo2);
  btCharacteristic.writeValue(toBLE);
}

//Gestione risposte A9G
String sendData(String command, const int timeout, boolean debug) {
  String response = "";
  mySerial.println(command);
  long int time = millis();
  while ((time + timeout) > millis()) {
    while (mySerial.available()) {
      char c = mySerial.read();
      response += c;
    }
  }
  if (debug) {
    Serial.print(response);
  }
  return response;
}

bool moduleStateCheck() {
  int i = 0;
  bool moduleState = false;
  for (i = 0; i < 5; i++) {
    String msg = sendData("AT", 1000, DEBUG);
    if (msg.indexOf("OK") >= 0) {
      Serial.println("A9/A9G Module had turned on.");
      moduleState = true;
      return moduleState;
    }
    delay(1000);
  }
  return moduleState;
}

void loadAlgomodeParameters() {
  algomodeInitialiser algoParameters;

  //Calibrazione del sensore MAX32664
  algoParameters.calibValSys[0] = 120;
  algoParameters.calibValSys[1] = 122;
  algoParameters.calibValSys[2] = 125;

  algoParameters.calibValDia[0] = 80;
  algoParameters.calibValDia[1] = 81;
  algoParameters.calibValDia[2] = 82;

  algoParameters.spo2CalibCoefA = 1.5958422;
  algoParameters.spo2CalibCoefB = -34.659664;
  algoParameters.spo2CalibCoefC = 112.68987;

  MAX32664.loadAlgorithmParameters(&algoParameters);
}

// Funzioni implementate per elaborare correttamente la posizione GPS

int findFirstNumericIndex(String inputString) {
  for (int i = 0; i < inputString.length(); i++) {
    if (isDigit(inputString.charAt(i))) {
      return i;
    }
  }
  return -1;
}
int findFLastNumericIndex(String inputString) {
  for (int i = inputString.length(); i > 0; i--) {
    if (isDigit(inputString.charAt(i))) {
      return i;
    }
  }
  return -1;
}
