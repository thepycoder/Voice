package voice.features.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.navigation.Navigator
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningStatsScreen(
  viewModel: ListeningStatsViewModel,
  navigator: Navigator,
) {
  val state = viewModel.viewState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Listening Stats") },
        navigationIcon = {
          IconButton(onClick = { navigator.goBack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      // Summary stats
      SummaryCard(
        title = "Today",
        duration = state.todaySeconds.seconds,
      )
      Spacer(modifier = Modifier.height(12.dp))
      SummaryCard(
        title = "This Month",
        duration = state.monthSeconds.seconds,
      )
      Spacer(modifier = Modifier.height(12.dp))
      SummaryCard(
        title = "This Year",
        duration = state.yearSeconds.seconds,
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Heatmap
      Text(
        text = "Listening Activity",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(8.dp))
      
      HeatmapGrid(state.heatmapData)

      Spacer(modifier = Modifier.height(16.dp))
      HeatmapLegend()
    }
  }
}

@Composable
private fun SummaryCard(
  title: String,
  duration: kotlin.time.Duration,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
      )
      .padding(16.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = formatDuration(duration),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun HeatmapGrid(data: List<DayData>) {
  // Group by week (7 days per row, 53 weeks)
  val weeks = data.chunked(7)
  
  LazyVerticalGrid(
    columns = GridCells.Fixed(7),
    modifier = Modifier.height(400.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    items(data) { day ->
      Box(
        modifier = Modifier
          .size(12.dp)
          .background(
            color = intensityColor(day.intensity),
            shape = RoundedCornerShape(2.dp),
          ),
      )
    }
  }
}

@Composable
private fun HeatmapLegend() {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Less",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(8.dp))
    DayData.Intensity.entries.forEach { intensity ->
      Box(
        modifier = Modifier
          .size(12.dp)
          .background(
            color = intensityColor(intensity),
            shape = RoundedCornerShape(2.dp),
          ),
      )
      Spacer(modifier = Modifier.width(4.dp))
    }
    Text(
      text = "More",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun intensityColor(intensity: DayData.Intensity) = when (intensity) {
  DayData.Intensity.NONE -> MaterialTheme.colorScheme.surfaceVariant
  DayData.Intensity.LOW -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
  DayData.Intensity.MEDIUM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
  DayData.Intensity.HIGH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
  DayData.Intensity.VERY_HIGH -> MaterialTheme.colorScheme.primary
}

private fun formatDuration(duration: kotlin.time.Duration): String {
  val hours = duration.inWholeHours
  val minutes = (duration.inWholeMinutes % 60)
  
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    minutes > 0 -> "${minutes}m"
    else -> "0m"
  }
}
