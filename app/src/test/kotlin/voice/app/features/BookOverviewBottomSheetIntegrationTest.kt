package voice.app.features

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import voice.app.di.ProductionAppGraph
import voice.core.common.rootGraph
import voice.features.bookOverview.di.BookOverviewGraph

/**
 * Integration test that uses the same dependency graph as the real app.
 * Verifies that the bottom sheet receives all 5 item view models (so the menu is not empty).
 *
 * If this test passes but the bottom sheet is empty on device, try:
 * - ./gradlew clean :app:assemblePlayDebug
 * - Uninstall the app from the device and reinstall
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BookOverviewBottomSheetIntegrationTest {

  @Test
  fun `bottom sheet has all item view models when created from real app graph`() {
    val application = ApplicationProvider.getApplicationContext() as Application
    val appGraph = createGraphFactory<ProductionAppGraph.Factory>().create(application)
    rootGraph = appGraph

    val bookGraph = (rootGraph as BookOverviewGraph.Factory.Provider)
      .bookOverviewGraphProviderFactory
      .create()
    val bottomSheetViewModel = bookGraph.bottomSheetViewModel

    bottomSheetViewModel.itemViewModelsCountForTest shouldBe 5
  }
}
