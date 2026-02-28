package voice.core.remote

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import net.schmizz.sshj.common.SecurityUtils
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
class SshSecurityProviderInitializer : AppInitializer {

  override fun onAppStart(application: Application) {
    SecurityUtils.setRegisterBouncyCastle(false)
  }
}
