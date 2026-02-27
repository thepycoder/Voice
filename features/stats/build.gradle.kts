plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.navigation)
  implementation(projects.core.data.api)
  implementation(projects.core.strings)
  implementation(projects.core.ui)

  implementation(libs.material)
  implementation(libs.datastore)
  implementation(libs.immutable)
}
