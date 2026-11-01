package com.eric.toolbox.modules.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eric.toolbox.ui.C

/** 一部片子的卡片：海报 + 标题 + 角标（清晰度 / 更新到第几集）。 */
@Composable
fun PosterCard(
    title: String,
    pic: String,
    badge: String = "",
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f)
                .clip(RoundedCornerShape(10.dp))
                .background(C.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (pic.isBlank()) {
                // 没有海报时用标题首字占位，避免一片空块
                Text(title.take(2), color = C.Muted, fontSize = 20.sp)
            } else {
                AsyncImage(
                    model = pic,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (badge.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Text(
            title, color = C.Text, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 横向滑动的分类 / 线路小标签，选中态跟 Segmented 保持一致。 */
@Composable
fun LineChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) C.Gold else C.Surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (selected) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted,
            fontSize = 13.sp,
        )
    }
}

/** 空态提示。统一居中样式，避免每处都自己垫 padding。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}
