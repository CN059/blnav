package com.powercess.blnav.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlin.math.roundToInt

/**
 * 虚拟坐标点数据类
 * @param x 虚拟坐标系中的x坐标（0.0 到 1.0，相对于地图宽度）
 * @param y 虚拟坐标系中的y坐标（0.0 到 1.0，相对于地图高度）
 * @param label 点的标签（如蓝牙信标ID）
 * @param color 点的颜色
 */
data class MapPoint(
    val x: Float,
    val y: Float,
    val label: String = "",
    val color: Color = Color.Red
)

/**
 * 可拖动的SVG地图视图
 *
 * 功能特性：
 * 1. 加载并显示SVG格式的室内地图
 * 2. 支持双指缩放（pinch to zoom）
 * 3. 支持单指拖动平移
 * 4. 提供虚拟坐标系统，可在地图上添加定位点
 *
 * @param svgFileName SVG文件名（放在assets文件夹中，如 "indoor_map.svg"）
 * @param points 要在地图上显示的定位点列表（使用0-1的归一化坐标）
 * @param modifier Modifier
 */
@Composable
fun DraggableMapView(
    modifier: Modifier = Modifier,
    svgFileName: String,
    points: List<MapPoint> = emptyList()
) {
    // 地图偏移量（拖动位置）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 地图缩放比例
    var scale by remember { mutableFloatStateOf(1f) }

    // 使用Coil加载SVG图片（从assets文件夹）
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/$svgFileName")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // 可拖动和缩放的地图图片
        Image(
            painter = painter,
            contentDescription = "室内地图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .scale(scale)
                .pointerInput(Unit) {
                    // 检测双指缩放手势
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 更新缩放比例，限制在0.5倍到5倍之间
                        scale = (scale * zoom).coerceIn(0.5f, 5f)

                        // 更新偏移量（拖动）
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    // 检测单指拖动手势
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        )

        // 在地图上绘制定位点
        points.forEach { point ->
            MapMarker(
                point = point,
                offsetX = offsetX,
                offsetY = offsetY,
                scale = scale
            )
        }
    }
}

/**
 * 地图标记点
 * 根据虚拟坐标和地图的缩放、偏移状态来定位
 */
@Composable
private fun MapMarker(
    point: MapPoint,
    offsetX: Float,
    offsetY: Float,
    scale: Float
) {
    // 这里需要知道地图的实际尺寸来计算真实像素位置
    // 暂时使用相对定位，实际使用时需要获取地图容器的尺寸
    Box(
        modifier = Modifier
            .offset {
                // 将虚拟坐标（0-1）转换为实际像素偏移
                // 这里需要根据实际地图容器大小计算
                IntOffset(
                    x = (offsetX + point.x * 1000 * scale).roundToInt(), // 1000是假设的地图宽度
                    y = (offsetY + point.y * 1000 * scale).roundToInt()  // 1000是假设的地图高度
                )
            }
            .size((12 * scale).dp)
            .background(point.color, CircleShape)
    )
}

