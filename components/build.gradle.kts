plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    api("org.apache.logging.log4j:log4j-core")
    api(platform("org.apache.logging.log4j:log4j-bom:2.13.1"))
    api("org.apache.logging.log4j:log4j-slf4j-impl:2.13.3")
    api("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")
    api("com.github.insanusmokrassar:TelegramBotAPI:0.28.0")
    api(platform("software.amazon.awssdk:bom:2.11.9"))
    api("software.amazon.awssdk:dynamodb")
}
