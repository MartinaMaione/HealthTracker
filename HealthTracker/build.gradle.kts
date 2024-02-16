buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.0")
    }

//    repositories {
//        mavenCentral()
//        maven { url = uri("https://jitpack.io") }
//    }

}
//allprojects{
//    repositories {
//        mavenCentral()
//        maven { url = uri("https://jitpack.io") }
//    }
//}

plugins {
    id("com.android.application") version "8.1.3" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("com.android.library") version "8.1.3" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}
