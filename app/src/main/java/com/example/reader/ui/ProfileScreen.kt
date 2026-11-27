package com.example.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.ui.theme.ReaderColors
import com.example.reader.vm.BookReadStat
import com.example.reader.vm.ProfileStats
import com.example.reader.vm.ProfileViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onShelf: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel()
    val stats by viewModel.stats.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
        bottomBar = { ReaderBottomNav(selected = 1, onShelf = onShelf, onProfile = {}) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { SummaryCard(stats) }
            item { WeeklyCard(stats.weeklySeconds) }

            item {
                Text("阅读记录", style = MaterialTheme.typography.titleMedium)
            }

            if (stats.bookStats.isEmpty()) {
                item {
                    Text(
                        "还没有阅读记录，去书架打开一本书开始阅读吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(items = stats.bookStats, key = { it.bookId }) { book ->
                    BookStatItem(book)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(stats: ProfileStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp)) {
            SummaryCell("总阅读", formatDuration(stats.totalSeconds), Modifier.weight(1.2f))
            SummaryCell("连续阅读", "${stats.streakDays} 天", Modifier.weight(1f))
            SummaryCell("已读书籍", "${stats.bookCount} 本", Modifier.weight(1f))
        }
        Text(
            text = "今日 ${formatDuration(stats.todaySeconds)} · 累计阅读 ${stats.readDays} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        )
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = ReaderColors.Brand
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeeklyCard(weekly: List<Long>) {
    val maxSeconds = (weekly.maxOrNull() ?: 0L).coerceAtLeast(60L)
    val labels = (6 downTo 0).map { offset ->
        when (LocalDate.now().minusDays(offset.toLong()).dayOfWeek.value) {
            1 -> "一"
            2 -> "二"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            else -> "日"
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("近 7 天阅读", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekly.forEachIndexed { index, seconds ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .height((seconds.toFloat() / maxSeconds * 110f).coerceAtLeast(3f).dp)
                                .width(22.dp)
                                .background(
                                    color = if (index == weekly.lastIndex) ReaderColors.Brand
                                    else ReaderColors.Brand.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "本周合计 ${formatDuration(weekly.sum())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookStatItem(stat: BookReadStat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(stat.seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReaderColors.Brand
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { stat.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            val date = if (stat.lastReadAt > 0) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(stat.lastReadAt))
            } else {
                "尚未阅读"
            }
            Text(
                text = "已读 ${(stat.progress * 100).toInt()}% · 最近阅读 $date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0 分钟"
    if (seconds < 60) return "不足 1 分钟"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours 小时 $minutes 分" else "$minutes 分钟"
}
