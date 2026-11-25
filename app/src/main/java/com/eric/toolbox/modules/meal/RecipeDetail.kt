package com.eric.toolbox.modules.meal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun RecipeDetail(recipe: Recipe, mode: String, modifier: Modifier, onAdd: () -> Unit) {
    val context = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val steps = recipe.cookingSteps()
    val guide = recipeGuides[recipe.id]
    var servings by remember(recipe.id) { mutableIntStateOf(2) }
    var cookingStep by remember(recipe.id) { mutableIntStateOf(-1) }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(modifier) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DishPhoto(recipe, Modifier.fillMaxWidth().aspectRatio(1f), ContentScale.Fit)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(recipe.name, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(listOf(recipe.category, "约 ${recipe.minutes} 分钟", recipe.description)
                    .filter(String::isNotBlank).joinToString(" · "), color = palette.onSurfaceVariant, fontSize = 13.sp)
            }
            if (guide != null) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(palette.secondaryContainer).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("为什么推荐")
                Text(guide.nutrition, lineHeight = 23.sp, fontSize = 14.sp)
                Text("怎么搭配  ${guide.pairing}", lineHeight = 22.sp, fontSize = 13.sp)
                when (mode) {
                    "孕期" -> Text("孕期备餐：生熟分开。肉、鱼和蛋类按安全要求做熟。", fontSize = 12.sp)
                    "产后" -> Text("产后备餐：按口味和食量选择；哺乳期也注意食物多样。", fontSize = 12.sp)
                    else -> Unit
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = palette.surface) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("用料", Modifier.weight(1f))
                        TextButton(onClick = { servings-- }, enabled = servings > 1) { Text("－") }
                        Text("${servings} 人份", fontSize = 13.sp)
                        TextButton(onClick = { servings++ }, enabled = servings < 12) { Text("＋") }
                    }
                    for ((item, amount) in recipe.ingredientsFor(servings)) {
                        HorizontalDivider(color = palette.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(item, Modifier.weight(1f), fontSize = 14.sp)
                            Text(amount, fontSize = 13.sp, color = palette.onSurfaceVariant)
                        }
                    }
                    Text("按原菜谱 2 人份换算；调味、少许和适量按实际调整。", fontSize = 11.sp,
                        color = palette.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("分步做法", Modifier.weight(1f))
                FilledTonalButton(onClick = { cookingStep = 0 }) { Text("烹饪模式", fontSize = 13.sp) }
            }
            if (recipe.id == "melon") Text("步骤图片为烹饪示意。", fontSize = 11.sp, color = palette.onSurfaceVariant)
            steps.forEachIndexed { index, step ->
                Surface(shape = RoundedCornerShape(14.dp), color = palette.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("步骤 ${index + 1}", fontWeight = FontWeight.SemiBold, color = palette.primary)
                        StepPhoto(recipe, index)
                        Text(step, fontSize = 14.sp, lineHeight = 23.sp)
                    }
                }
            }
            if (recipe.source.isNotBlank()) TextButton(onClick = { open(recipe.source) }) {
                Text("查看下厨房参考菜谱和原版步骤图")
            }
            if (guide != null) {
                SectionTitle("参考资料")
                TextButton(onClick = { open("https://cnsoc.org/knowledge/7125112038.html") }) { Text("中国营养学会 · 孕期营养") }
                TextButton(onClick = { open("https://www.cogonline.com/info/132067967216305793_1") }) { Text("哺乳期膳食指南") }
                TextButton(onClick = { open("https://www.cdc.gov/food-safety/foods/pregnant-women.html") }) { Text("CDC · 孕期食品安全") }
                if (recipe.id == "melon") TextButton(onClick = { open("https://nlc.chinanutri.cn/fq/foodinfo/419.html") }) {
                    Text("中国疾控中心食物成分表 · 冬瓜")
                }
            }
        }
        HorizontalDivider(color = palette.outlineVariant)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Text("加入菜单") }
    }

    if (cookingStep in steps.indices) {
        AlertDialog(
            onDismissRequest = { cookingStep = -1 },
            title = { Text("步骤 ${cookingStep + 1} / ${steps.size}") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepPhoto(recipe, cookingStep)
                    Text(steps[cookingStep], fontSize = 17.sp, lineHeight = 26.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { cookingStep = if (cookingStep == steps.lastIndex) -1 else cookingStep + 1 }) {
                    Text(if (cookingStep == steps.lastIndex) "完成" else "下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { cookingStep-- }) { Text(if (cookingStep == 0) "关闭" else "上一步") }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StepPhoto(recipe: Recipe, index: Int) {
    if (recipe.id == "melon") {
        AsyncImage(
            "file:///android_asset/meal/melon-step-${index + 1}.jpg",
            "步骤示意图",
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}
