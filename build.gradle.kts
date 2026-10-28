plugins {
    id("java")
    id("application")
}

group = "com.ccat"
version = "1.1-SNAPSHOT"

val jdaVersion = "6.7.0"
val gsonVersion = "2.15.0"
val sqliteVersion = "3.53.4.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("net.dv8tion:JDA:${jdaVersion}")
    implementation("com.google.code.gson:gson:${gsonVersion}")
    implementation("org.xerial:sqlite-jdbc:${sqliteVersion}")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.ccat.Main")
}