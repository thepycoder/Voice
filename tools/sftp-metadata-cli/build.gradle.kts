plugins {
  id("org.jetbrains.kotlin.jvm")
  application
}

kotlin {
  jvmToolchain(17)
}

application {
  mainClass.set("voice.tools.sftpmetadata.MainKt")
}

dependencies {
  implementation(projects.core.mp4Metadata)
  implementation("com.github.ajalt.clikt:clikt-jvm:5.1.0")
  implementation("com.hierynomus:sshj:0.39.0")
  implementation("org.bouncycastle:bcprov-jdk18on:1.80")
}
