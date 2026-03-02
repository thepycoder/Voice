package voice.core.remote

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import voice.core.initializer.AppInitializer
import java.security.Security

@ContributesIntoSet(AppScope::class)
class SshSecurityProviderInitializer : AppInitializer {

  override fun onAppStart(application: Application) {
    setupBouncyCastle()
  }

  companion object {
    @Volatile
    private var initialized = false

    @Synchronized
    fun setupBouncyCastle() {
      if (initialized) return
      initialized = true

      // Add EdDSA provider for Ed25519 support
      if (Security.getProvider(EdDSASecurityProvider.PROVIDER_NAME) == null) {
        Security.addProvider(EdDSASecurityProvider())
      }

      val existingBc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
      if (existingBc != null && existingBc.javaClass != BouncyCastleProvider::class.java) {
        // Android registers its own outdated BC provider (com.android.org.bouncycastle)
        // under the name "BC". Replace it with our bundled full BouncyCastle.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
      }

      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
      }
    }
  }
}
