package voice.features.stats

import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.rememberScoped
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator

@ContributesTo(AppScope::class)
interface StatsGraph {
  val listeningStatsViewModel: ListeningStatsViewModel
}

@ContributesTo(AppScope::class)
interface StatsNavEntryProviderModule {

  @Provides
  @IntoSet
  fun statsNavEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.ListeningStats> { key ->
      NavEntry(key) {
        val navigator = rootGraphAs<Navigator>()
        val viewModel = rememberScoped { rootGraphAs<StatsGraph>().listeningStatsViewModel }
        ListeningStatsScreen(
          viewModel = viewModel,
          navigator = navigator,
        )
      }
    }
}
