package com.jp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jp.app.BuildConfig
import com.jp.app.ui.theme.spaces

private const val PROJECT_URL = "https://github.com/NoNameLeGo/jp-media-viewer"
private const val ISSUES_URL = "https://github.com/NoNameLeGo/jp-media-viewer/issues"
private const val DEVELOPER_NAME = "NoNameLeGo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    respectNomedia: Boolean,
    onRespectNomediaChanged: (Boolean) -> Unit,
    pureBlack: Boolean,
    onPureBlackChanged: (Boolean) -> Unit,
    mediaCacheSizeBytes: Long,
    favoriteCount: Int,
    isScanning: Boolean,
    onClearMediaCache: () -> Unit,
    onClearFavorites: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var openLinkError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = MaterialTheme.spaces.large)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.small)
        ) {
            SettingsSectionTitle("外观")
            SwitchRow(
                title = "纯黑模式",
                subtitle = "深色下将背景压成纯黑，适合 OLED 屏幕沉浸浏览",
                checked = pureBlack,
                onCheckedChange = onPureBlackChanged
            )

            SettingsSectionTitle("扫描")
            SwitchRow(
                title = "遵守 .nomedia",
                subtitle = "跳过含 .nomedia 标记的文件夹",
                checked = respectNomedia,
                onCheckedChange = onRespectNomediaChanged
            )

            SettingsSectionTitle("存储")
            SettingsCard {
                Text(
                    text = "媒体缓存：${formatCacheSize(mediaCacheSizeBytes)} · 收藏：$favoriteCount 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.medium)) {
                    OutlinedButton(
                        onClick = onClearMediaCache,
                        enabled = !isScanning && mediaCacheSizeBytes > 0L
                    ) { Text("清除媒体缓存") }
                    OutlinedButton(
                        onClick = onClearFavorites,
                        enabled = !isScanning && favoriteCount > 0
                    ) { Text("清除收藏") }
                }
            }

            SettingsSectionTitle("关于")
            ListItem(
                headlineContent = { Text("关于 JP Media Viewer") },
                supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME}") },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .fillMaxWidth()
                    .clickable { showAbout = true }
            )
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenProject = {
                openLinkError = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                }.onFailure {
                    openLinkError = "无法打开项目地址，请检查是否有可用浏览器。"
                }
            },
            onOpenIssues = {
                openLinkError = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)))
                }.onFailure {
                    openLinkError = "无法打开问题反馈，请检查是否有可用浏览器。"
                }
            }
        )
    }

    if (openLinkError != null) {
        AlertDialog(
            onDismissRequest = { openLinkError = null },
            title = { Text("打开失败") },
            text = { Text(openLinkError ?: "") },
            confirmButton = {
                TextButton(onClick = { openLinkError = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = MaterialTheme.spaces.medium, bottom = MaterialTheme.spaces.extraSmall)
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spaces.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.medium),
            content = content
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spaces.large,
                vertical = MaterialTheme.spaces.medium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 轻量点击修饰符，避免为一处引入 foundation.clickable 的额外 import 噪音。 */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClickLabel = null) { onClick() })

private fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes ${units[unitIndex]}" else "%.1f %s".format(value, units[unitIndex])
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenIssues: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 JP Media Viewer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "本地随机图片/视频浏览器，支持文件夹授权、收藏、手势浏览和媒体缓存。",
                    style = MaterialTheme.typography.bodyMedium
                )
                AboutRow(label = "版本号", value = BuildConfig.VERSION_NAME)
                AboutRow(label = "构建类型", value = BuildConfig.BUILD_TYPE)
                AboutRow(label = "开发者", value = DEVELOPER_NAME)
                AboutRow(label = "许可证", value = "AGPL-3.0")
                AboutRow(label = "项目地址", value = PROJECT_URL)
                AboutRow(label = "问题反馈", value = ISSUES_URL)
                AboutRow(
                    label = "隐私说明",
                    value = "媒体扫描、收藏和缓存均保存在本机，不会上传你的文件。"
                )
                AboutRow(label = "第三方组件", value = "Jetpack Compose、Coil、Media3")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onOpenProject) { Text("项目地址") }
                TextButton(onClick = onOpenIssues) { Text("问题反馈") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
