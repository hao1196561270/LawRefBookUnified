package com.lawrefbook.unified.ui.reader

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.IntOffset

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

/** 放大镜镜片半径（dp） */
private const val LOUPE_RADIUS_DP = 34f
/** 放大镜缩放倍数 */
private const val LOUPE_ZOOM = 1.8f
/** 放大镜中心相对手指的上移距离（dp），使镜片悬浮于手指上方 */
private const val LOUPE_OFFSET_ABOVE_DP = 24f
/** 正文内边距（dp），用于把指针坐标换算到文本内容坐标 */
private const val TEXT_PADDING_DP = 8f

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
 * - 进入选区瞬间触发**设备震动反馈**（短促、强度适中）并弹出**放大镜**：镜片实时放大手指下方区域、跟随手指移动，
 *   内部绘制与正文一致的高亮与对称边界手柄，并以中心十字标记当前选中的字符位置；
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
    var loupePos by remember { mutableStateOf<Offset?>(null) }
    var loupeVisible by remember { mutableStateOf(false) }

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
                                        val hitR = maxOf(viewConfiguration.touchSlop, 22f * density)
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
                                        loupeVisible = false
                                        loupePos = null
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
                                            // 触觉反馈 + 显示放大镜
                                            vibrateConfirm(context)
                                            loupeVisible = true
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
                                            loupePos = change.position
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            )

            // 放大镜：长按时跟随手指，放大手指下方区域并标示当前选中范围
            val loupeLayout = textLayout
            val loupeSel = selection
            val lp = loupePos
            if (loupeVisible && lp != null && loupeLayout != null && loupeSel != null) {
                val ls = minOf(loupeSel.first, loupeSel.last)
                val le = maxOf(loupeSel.first, loupeSel.last)
                if (le > ls) {
                    val ld = density
                    val rPx = LOUPE_RADIUS_DP * ld
                    val rDp = LOUPE_RADIUS_DP.dp
                    val zoom = LOUPE_ZOOM
                    val pad = TEXT_PADDING_DP * ld
                    val off = loupeLayout.getOffsetForPosition(Offset(lp.x - pad, lp.y - pad)).coerceIn(0, text.length)
                    val p = loupeLayout.getCursorRect(off).center
                    val contentW = loupeLayout.size.width
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset((lp.x - rPx).toInt(), (lp.y - rPx - LOUPE_OFFSET_ABOVE_DP * ld).toInt()) }
                            .size(rDp * 2)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                            .border(BorderStroke(1.5.dp, handleColor))
                    ) {
                        Text(
                            text = text,
                            style = style,
                            softWrap = true,
                            modifier = Modifier
                                .requiredWidth((contentW / ld).dp)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = rPx - p.x * zoom
                                    translationY = rPx - p.y * zoom
                                }
                                .drawWithContent {
                                    val ss = if (loupeSel.first <= loupeSel.last) loupeSel.first else loupeSel.last
                                    val ee = if (loupeSel.first <= loupeSel.last) loupeSel.last else loupeSel.first
                                    if (ee > ss) {
                                        for (rr in selectionRects(loupeLayout, ss, ee)) {
                                            drawRect(
                                                color = highlightColor,
                                                topLeft = Offset(rr.left, rr.top),
                                                size = Size(rr.width, rr.height),
                                                alpha = 0.26f
                                            )
                                        }
                                        val hr = HANDLE_DOT_RADIUS_DP * ld
                                        val hline = HANDLE_LINE_DP * ld
                                        val hstroke = HANDLE_STROKE_DP * ld
                                        val stR = loupeLayout.getCursorRect(ss)
                                        val enR = loupeLayout.getCursorRect(ee)
                                        val lX = stR.left
                                        val lY = stR.top + hr
                                        drawLine(color = handleColor, start = Offset(lX, lY), end = Offset(lX, lY + hline), strokeWidth = hstroke)
                                        drawCircle(color = handleColor, radius = hr, center = Offset(lX, lY))
                                        val rX = enR.left
                                        val rY = enR.bottom - hr
                                        drawLine(color = handleColor, start = Offset(rX, rY - hline), end = Offset(rX, rY), strokeWidth = hstroke)
                                        drawCircle(color = handleColor, radius = hr, center = Offset(rX, rY))
                                    }
                                    drawContent()
                                }
                        )
                        // 中心十字标记：明确标示当前选中的字符位置
                        Canvas(Modifier.fillMaxSize()) {
                            val c = rPx
                            drawLine(
                                color = handleColor,
                                start = Offset(c, c - 9.dp.toPx()),
                                end = Offset(c, c + 9.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(color = handleColor, radius = 3.dp.toPx(), center = Offset(c, c))
                        }
                    }
                }
            }

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
    if (idx <= 0 || idx >= text.length) return IntRange(0, text.length)
    val stops = setOf('。', '！', '？', '；', '：', '\n', '.', '!', '?', ';', ':')
    var start = idx
    while (start > 0 && !stops.contains(text[start - 1])) start--
    var end = idx
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
