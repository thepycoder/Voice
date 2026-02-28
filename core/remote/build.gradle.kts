plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(projects.core.initializer)
  implementation(projects.core.logging.api)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  
  implementation(libs.androidxCore)
  implementation(libs.coroutines.core)
  implementation(libs.datastore)
  implementation(libs.serialization.json)
  
  api("com.hierynomus:sshj:0.38.0")
}
