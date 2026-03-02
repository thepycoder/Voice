package voice.app.di

import voice.app.features.widget.BaseWidgetProvider
import voice.app.sync.SyncForegroundService
import voice.features.widget.WidgetGraph

interface AppGraph : WidgetGraph {

  fun inject(target: App)
  fun inject(target: SyncForegroundService)
  override fun inject(target: BaseWidgetProvider)
}
