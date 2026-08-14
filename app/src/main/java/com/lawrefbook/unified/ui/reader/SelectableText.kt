package com.lawrefbook.unified.ui.reader

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** 进入选区模式所需的长按阈值（毫秒） */
private const val SELECT_LONG_PRESS_MS = 200L

/** 边界手柄调整模式 */
private const val ADJ_NONE = 0
private const val ADJ_START = 1
private const val ADJ_END = 2

/** 边界手柄圆点半径（dp） */
private const val HANDLE_DOT_RADIUS_DP = 4f
/** 边界手柄竖线长度（dp） */
private const val HANDLE_LINE_DP = 16f
/** 边界手柄竖线线宽（dp） */
private const val HANDLE_STROKE_DP = 2f

/** 长按确认震动时长（毫秒） */
private const val HAPTIC_DURATION_MS = 30L
/** 长按确认震动强度（0–255，取中值偏柔，强度适中） */
private const val HAPTIC_AMPLITUDE = 45

/**
 * 可在正文中长按选择的文本组件。
 *
 * 交互约定：
 * - 在文本上按住超过 [SELECT_LONG_PRESS_MS]（且未触发滚动）即进入选区模式；
 *   若未拖动，则以「长按位置所在句子」作为初始选区（保证有内容可操作）；
 * - 进入选区后，手指/指针继续移动会动态扩展选区的结束边界（含反向拖动）；
 * - 高亮以半透明色块覆盖选中字符范围，并在起、止字符处绘制对称边界手柄：左侧圆点贴顶部原点正下方并向下延伸竖线、右侧圆点贴底部并向上延伸竖线，两者镜像对应；
 * - **拖动手柄**：按下时若命中某个边界圆点，则进入「调整该端点」模式，仅移动对应端点、
 *   保留另一端，拖动过程中高亮稳定保留、不会自动消失；
 * - 进入选区瞬间触发**设备震动反馈**（短促、强度适中）；
 * - **放大镜**：使用 Jetpack Compose 官方 `Modifier.magnifier`（androidx.compose.foundation，
 *   内部为 android.widget.Magnifier 封装）——长按/拖动时实时放大手指下方文本、自动悬浮于
 *   手指上方并跟随移动、越界自动夹紧，彻底替代早期自实现的 loupe 贴图（其缩放原点缺失
 *   transformOrigin 导致镜内内容与手指严重错位）。
 *   通过把 [loupePos] 置为 `Offset.Unspecified` 控制隐藏，置为手指位置控制显示与跟随；
 * - 松开后保留选区并**立即弹出上下文菜单**，提供「复制」与「分享」；
 * - 在已有选区上短按正文（未命中手柄、未达阈值即抬起）会清除选区；
 * - 进入选区前不消费指针事件，因此与 LazyColumn 滚动、普通短按均无冲突。
 *
 * @param text           完整纯文本（条号 + 正文），字符顺序须与显示一致，
 *                       用于按字符索引切分选区并计算选中子串。
 * @param highlightBackground 是否使用目标条目高亮背景（如跳转定位的法条）。
 * @param onCopy         点击「复制」时回调，参数即选中的子串。
 */
@Composable
fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    highlightBackground: Boolean = false,
    onCopy: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember { mutableStateOf<IntRange?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 放大镜锚点（元素局部坐标）。Offset.Unspecified = 隐藏放大镜；
    // 官方 Modifier.magnifier 每帧读取它并自动完成 屏幕坐标换算/悬浮/跟随/夹紧。
    var loupePos by remember { mutableStateOf(Offset.Unspecified) }

    val highlightColor = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.primary

    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = style,
                overflow = TextOverflow.Visible,
                softWrap = true,
                onTextLayout = { textLayout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (highlightBackground) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(8.dp)
                    .drawWithContent {
                        val sel = selection
                        val layout = textLayout
                        if (sel != null && layout != null) {
                            val s = if (sel.first <= sel.last) sel.first else sel.last
                            val e = if (sel.first <= sel.last) sel.last else sel.first
                            if (e > s) {
                                for (r in selectionRects(layout, s, e)) {
                                    drawRect(
                                        color = highlightColor,
                                        topLeft = Offset(r.left, r.top),
                                        size = Size(r.width, r.height),
                                        alpha = 0.26f
                                    )
                                }
                            }
                        }
                        drawContent()
                        val sel2 = selection
                        val layout2 = textLayout
                        if (sel2 != null && layout2 != null) {
                            val s = if (sel2.first <= sel2.last) sel2.first else sel2.last
                            val e = if (sel2.first <= sel2.last) sel2.last else sel2.first
                            if (e > s) {
                                val r = HANDLE_DOT_RADIUS_DP * drawContext.density.density
                                val lineLen = HANDLE_LINE_DP * drawContext.density.density
                                val stroke = HANDLE_STROKE_DP * drawContext.density.density
                                val startRect = layout2.getCursorRect(s)
                                val endRect = layout2.getCursorRect(e)
                                // 左侧：原点在顶部，圆点紧贴其正下方，并向下延伸一条竖线
                                val leftX = startRect.left
                                val leftDotY = startRect.top + r
                                drawLine(
                                    color = handleColor,
                                    start = Offset(leftX, leftDotY),
                                    end = Offset(leftX, leftDotY + lineLen),
                                    strokeWidth = stroke
                                )
                                drawCircle(color = handleColor, radius = r, center = Offset(leftX, leftDotY))
                                // 右侧：圆点在底部，竖线绘制于其上方（与左侧对称）
                                val rightX = endRect.left
                                val rightDotY = endRect.bottom - r
                                drawLine(
                                    color = handleColor,
                                    start = Offset(rightX, rightDotY - lineLen),
                                    end = Offset(rightX, rightDotY),
                                    strokeWidth = stroke
                                )
                                drawCircle(color = handleColor, radius = r, center = Offset(rightX, rightDotY))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pressPos = down.position
                                val downTime = down.uptimeMillis
                                // 命中检测：若按下位置贴近某个边界手柄，则进入「调整该端点」模式
                                var adjustMode = ADJ_NONE
                                val layout = textLayout
                                val sel = selection
                                if (layout != null && sel != null) {
                                    val s = minOf(sel.first, sel.last)
                                    val e = maxOf(sel.first, sel.last)
                                    if (e > s) {
                                        val startC = layout.getCursorRect(s)
                                        val endC = layout.getCursorRect(e)
                                        val dotR = HANDLE_DOT_RADIUS_DP * density
                                        // 命中区按官方 48dp 最小触控目标（半径 24dp）放大，便于点按
                                        val hitR = maxOf(viewConfiguration.touchSlop, 24f * density)
                                        val leftDot = Offset(startC.left, startC.top + dotR)
                                        val rightDot = Offset(endC.left, endC.bottom - dotR)
                                        val dStart = (pressPos - leftDot).getDistance()
                                        val dEndReal = (pressPos - rightDot).getDistance()
                                        adjustMode = when {
                                            dStart <= hitR && dEndReal <= hitR -> if (dStart <= dEndReal) ADJ_START else ADJ_END
                                            dStart <= hitR -> ADJ_START
                                            dEndReal <= hitR -> ADJ_END
                                            else -> ADJ_NONE
                                        }
                                        // 命中手柄时若菜单开着则先收起，避免遮挡/重复消费
                                        if (adjustMode != ADJ_NONE) {
                                            menuExpanded = false
                                            vibrateConfirm(context)
                                        }
                                    }
                                }
                                var activated = adjustMode != ADJ_NONE
                                var dragAnchor = -1
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (!change.pressed) {
                                        // 抬起：隐藏放大镜；已进入选区（含手柄调整）→ 重新弹出菜单；
                                        // 仅短按正文且已有选区 → 清除
                                        loupePos = Offset.Unspecified
                                        if (activated) {
                                            menuExpanded = true
                                        } else if (selection != null) {
                                            selection = null
                                            menuExpanded = false
                                        }
                                        break
                                    }
                                    val lay = textLayout ?: break
                                    val off = lay.getOffsetForPosition(change.position).coerceIn(0, text.length)
                                    if (adjustMode != ADJ_NONE) {
                                        // 调整边界：仅改对应端点，保留另一端；持续消费并保留高亮
                                        val cur = selection
                                        if (cur != null) {
                                            val s0 = minOf(cur.first, cur.last)
                                            val e0 = maxOf(cur.first, cur.last)
                                            selection = if (adjustMode == ADJ_START) IntRange(off, e0) else IntRange(s0, off)
                                        }
                                        change.consume()
                                        activated = true
                                        loupePos = change.position
                                    } else if (!activated) {
                                        // 阈值前移动超 touchSlop 视为滚动，放行不进入选区
                                        if ((change.position - pressPos).getDistance() > viewConfiguration.touchSlop) break
                                        if (change.uptimeMillis - downTime >= SELECT_LONG_PRESS_MS) {
                                            dragAnchor = lay.getOffsetForPosition(pressPos).coerceIn(0, text.length)
                                            activated = true
                                            // 不拖动也给出「所在句子」内容，保证菜单有可用文本
                                            selection = sentenceRange(text, dragAnchor)
                                            change.consume()
                                            // 触觉反馈 + 显示放大镜（官方 magnifier 悬浮于手指上方并跟随）
                                            vibrateConfirm(context)
                                            loupePos = pressPos
                                        }
                                    } else {
                                        // 拖动中：仅当指针真正移动超过 touchSlop 时，才以锚点(dragAnchor)
                                        // 为起点扩展选区；否则（手指基本不动）保留长按自动选中的「句子」
                                        // 范围，避免下一事件用零长度区间 IntRange(dragAnchor, off)（off≈dragAnchor）
                                        // 覆盖它，导致高亮瞬间折叠而「闪烁一下」。
                                        val dragged = (change.position - pressPos).getDistance() > viewConfiguration.touchSlop
                                        if (dragged) {
                                            selection = IntRange(dragAnchor, off)
                                        }
                                        loupePos = change.position
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                    // 官方放大镜：sourceCenter/magnifierCenter 均为元素局部坐标
                    // （节点内部自动换算到屏幕），loupePos=Unspecified 时隐藏。
                    .magnifier(
                        sourceCenter = { loupePos },
                        magnifierCenter = { loupePos }
                    )
            )

            val selBounds = selection?.let { sel ->
                val s = if (sel.first <= sel.last) sel.first else sel.last
                val e = if (sel.first <= sel.last) sel.last else sel.first
                if (e > s) s to e else null
            }
            DropdownMenu(
                expanded = menuExpanded && selBounds != null,
                onDismissRequest = {
                    // 仅收起菜单，不清除选区：保证手柄始终可抓、高亮持续保留
                    menuExpanded = false
                }
            ) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    text = { Text("复制") },
                    onClick = {
                        selBounds?.let { (s, e) -> onCopy(text.substring(s, e)) }
                        menuExpanded = false
                        selection = null
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    text = { Text("分享") },
                    onClick = {
                        selBounds?.let { (s, e) -> shareText(context, text.substring(s, e)) }
                        menuExpanded = false
                        selection = null
                    }
                )
            }
        }
    }
}

/**
 * 根据字符位置 [idx] 计算其所在句子范围（含结尾标点），返回 [IntRange]。
 * 范围采用「光标偏移」语义（last 为 exclusive-end），与高亮绘制、复制子串一致。
 * 若附近无终止标点（如纯条号或整段无标点），返回整段范围。
 */
private fun sentenceRange(text: String, idx: Int): IntRange {
    if (text.isEmpty()) return IntRange(0, 0)
    val clamped = idx.coerceIn(0, text.length - 1)
    val stops = setOf('。', '！', '？', '；', '：', '\n', '.', '!', '?', ';', ':')
    var start = clamped
    while (start > 0 && !stops.contains(text[start - 1])) start--
    var end = clamped
    while (end < text.length && !stops.contains(text[end])) end++
    if (end < text.length) end++ // 包含终止符
    return IntRange(start, end)
}

/**
 * 计算 [start, end) 字符范围在文本布局中每行对应的高亮矩形。
 * 跨多行时，首行取段起点到选区尾、末行取选区头到段尾、中间行取整行宽度。
 */
private fun selectionRects(layout: TextLayoutResult, start: Int, end: Int): List<Rect> {
    val s = if (start <= end) start else end
    val e = if (start <= end) end else start
    if (e <= s) return emptyList()
    val startLine = layout.getLineForOffset(s)
    val endLine = layout.getLineForOffset(e)
    val rects = mutableListOf<Rect>()
    for (line in startLine..endLine) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val segStart = if (s > lineStart) s else lineStart
        val segEnd = if (e < lineEnd) e else lineEnd
        if (segEnd <= segStart) continue
        val left = if (line == startLine) layout.getHorizontalPosition(segStart, true) else layout.getLineLeft(line)
        val right = if (line == endLine) layout.getHorizontalPosition(segEnd, true) else layout.getLineRight(line)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        rects.add(Rect(left, top, right, bottom))
    }
    return rects
}

/** 通过系统分享面板分享文本 */
private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "分享法条"))
    } catch (_: Exception) {
        // 无可用分享目标时静默忽略
    }
}

/** 长按确认的触觉反馈：短促、强度适中的设备震动（振幅取中值，不刺耳） */
private fun vibrateConfirm(context: Context) {
    try {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS, HAPTIC_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_DURATION_MS)
        }
    } catch (_: Exception) {
        // 无震动硬件或权限时静默忽略
    }
}
