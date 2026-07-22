package com.jp.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * 手写的预测式返回容器（项目无 Navigation，无法白送 Navigation Compose 的预测式返回）。
 *
 * [background] 铺在下层（返回后要露出的目标屏），[foreground] 是当前正在被关闭的屏。
 * 返回手势推进时，上层随进度缩小、向滑动边缘平移、圆角变大并轻微淡出，露出下层；
 * 手势完成回调 [onBack]，取消则弹回原位。
 *
 * 完整跨屏预览动画在 Android 14 (API 34)+ 生效；更低版本优雅降级为瞬时返回。
 */
@Composable
fun PredictiveBackContainer(
    onBack: () -> Unit,
    background: @Composable () -> Unit,
    foreground: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    // 记录手势来自哪个边缘：左缘 → 向右让位(+1)，右缘 → 向左让位(-1)。
    var edgeSign by remember { mutableFloatStateOf(1f) }

    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event ->
                edgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                progress.snapTo(event.progress)
            }
            // flow 正常结束 = 手势完成：补完动画后真正返回。
            progress.animateTo(1f, tween(120))
            onBack()
            progress.snapTo(0f)
        } catch (_: CancellationException) {
            // 手势取消：弹回原位。
            progress.animateTo(0f, spring())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        background()

        val p = FastOutSlowInEasing.transform(progress.value.coerceIn(0f, 1f))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.10f * p
                    scaleX = scale
                    scaleY = scale
                    translationX = edgeSign * size.width * 0.06f * p
                    alpha = 1f - 0.05f * p
                    // 以竖直居中、水平贴向让位方向的边缘为缩放锚点。
                    transformOrigin = TransformOrigin(if (edgeSign > 0f) 1f else 0f, 0.5f)
                }
                .clip(RoundedCornerShape((28f * p).dp))
        ) {
            foreground()
        }
    }
}
