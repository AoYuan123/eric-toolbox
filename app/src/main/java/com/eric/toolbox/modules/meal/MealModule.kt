package com.eric.toolbox.modules.meal

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object MealModule : ToolModule {
    override val id = "meal"
    override val name = "点菜"
    override val category = Category.TOOL
    override val icon = Icons.Filled.RestaurantMenu

    @Composable
    override fun Screen(onBack: () -> Unit) = MealScreen(onBack)
}

private val mealColors = lightColorScheme(
    primary = Color(0xFF4D705D),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDDECE0),
    onSecondaryContainer = Color(0xFF284936),
    background = Color(0xFFF5F8F4),
    surface = Color(0xFFFCFDFB),
    onSurface = Color(0xFF23302A),
    onSurfaceVariant = Color(0xFF56665C),
    outlineVariant = Color(0xFFDCE6DE),
)
private val dishCategories = listOf("荤菜", "素菜", "凉菜", "汤类", "主食")
private val categories = listOf("最受欢迎", "上次点了", "全部", "家乡菜") + dishCategories
private val modes = listOf("孕期", "产后", "全家")

@Composable
private fun MealScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { Store.module(context, MealModule.id) }
    var custom by remember { mutableStateOf(readCustom(prefs.getString("custom", null))) }
    val recipes = mealRecipes + custom
    var counts by remember { mutableStateOf(readCounts(prefs.getString("counts", null))) }
    var last by remember { mutableStateOf(readCounts(prefs.getString("last", null))) }
    var cart by remember { mutableStateOf(readCounts(prefs.getString("draft", null))) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "孕期") ?: "孕期") }
    var tab by remember { mutableStateOf("点菜") }
    var category by remember { mutableStateOf("全部") }
    var search by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Recipe?>(null) }
    var editor by remember { mutableStateOf<Recipe?>(null) }
    var menu by remember { mutableStateOf(false) }
    var draw by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var withStaple by remember { mutableStateOf(false) }
    var includeSteps by remember { mutableStateOf(false) }
    var people by remember { mutableIntStateOf(2) }
    var meal by remember { mutableStateOf("午餐") }
    var note by remember { mutableStateOf("") }

    fun saveCart(value: Map<String, Int>) {
        cart = value
        prefs.edit().putString("draft", JSONObject(value).toString()).apply()
    }

    fun adjust(recipe: Recipe, change: Int) {
        val amount = ((cart[recipe.id] ?: 0) + change).coerceIn(0, 99)
        saveCart(if (amount == 0) cart - recipe.id else cart + (recipe.id to amount))
    }

    fun suggestions(): List<Recipe> {
        val pool = recipes.filter { mode == "全家" || it.everyday }
        val desired = listOf("荤菜", "素菜", if (withStaple) "主食" else "汤类")
        return desired.filterNot { type -> pool.any { it.category == type && it.id in cart } }.mapNotNull { type ->
            pool.filter { it.category == type && it.id !in cart && it.id !in last }.randomOrNull()
                ?: pool.filter { it.category == type && it.id !in cart }.randomOrNull()
                ?: pool.filter { it.category == type }.randomOrNull()
        }
    }

    fun back() {
        when {
            editor != null -> editor = null
            detail != null -> detail = null
            menu -> menu = false
            tab != "点菜" -> tab = "点菜"
            else -> onBack()
        }
    }

    val selected = recipes.filter { (cart[it.id] ?: 0) > 0 }
    val matches = recipes.filter { recipe ->
        (mode == "全家" || recipe.everyday) && listOf(recipe.name, recipe.ingredients, recipe.description)
            .any { it.contains(search.trim(), ignoreCase = true) }
    }
    fun group(name: String): List<Recipe> = when (name) {
        "全部" -> matches
        "最受欢迎" -> matches.filter { (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] ?: 0 }
        "上次点了" -> matches.filter { it.id in last }
        "家乡菜" -> matches.filter { it.local }
        else -> matches.filter { it.category == name }
    }
    BackHandler(editor != null || detail != null || menu || tab != "点菜") { back() }

    MaterialTheme(colorScheme = mealColors) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text(detail?.name ?: if (menu) "今日菜单" else tab, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            bottomBar = {
                if (detail == null && !menu) Column(
                    Modifier.fillMaxWidth().background(mealColors.surface).navigationBarsPadding(),
                ) {
                    HorizontalDivider(color = mealColors.outlineVariant)
                    if (tab == "点菜") Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (selected.isEmpty()) "还没点菜" else "已选 ${selected.size} 道 · 共 ${selected.sumOf { cart[it.id] ?: 0 }} 份",
                            Modifier.weight(1f), fontSize = 13.sp,
                        )
                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = { menu = true },
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = mealColors.secondaryContainer,
                                disabledContentColor = mealColors.onSurfaceVariant,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) { Text("生成菜单", fontSize = 13.sp) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                        val icons = listOf(Icons.Filled.Casino, Icons.Filled.RestaurantMenu, Icons.Filled.ListAlt)
                        for ((index, item) in listOf("随机吃什么", "点菜", "菜品管理").withIndex()) {
                            val active = tab == item
                            val color = if (active) mealColors.primary else mealColors.onSurfaceVariant
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (active) mealColors.secondaryContainer else Color.Transparent)
                                    .clickable(role = Role.Tab) { tab = item }.heightIn(min = 48.dp).padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(icons[index], null, Modifier.size(17.dp), tint = color)
                                Text(item, fontSize = 11.sp, color = color)
                            }
                        }
                    }
                }
            },
        ) { inset ->
            val body = Modifier.fillMaxSize().padding(inset).padding(horizontal = 10.dp)
            val recipe = detail
            when {
                recipe != null -> Column(
                    body.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DishPhoto(recipe, Modifier.fillMaxWidth().height(200.dp))
                    Text("每份约 2 人 · 约 ${recipe.minutes} 分钟", fontSize = 13.sp)
                    Text("食材", style = MaterialTheme.typography.titleMedium)
                    Text(recipe.ingredients)
                    Text("做法", style = MaterialTheme.typography.titleMedium)
                    recipe.instructions().lines().forEach { Text(it) }
                    if (recipe.source.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(recipe.source)))
                            }
                        }) { Text("参考菜谱 · 下厨房 · 樱桃777") }
                    }
                    Button(onClick = { adjust(recipe, 1); detail = null }) { Text("加入菜单") }
                }
                menu -> Column(body.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (item in listOf("早餐", "午餐", "晚餐")) {
                            FilterChip(meal == item, { meal = item }, { Text(item) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("人数", Modifier.weight(1f))
                        Quantity(people, { people = (people - 1).coerceAtLeast(1) }, { people = (people + 1).coerceAtMost(30) })
                    }
                    for (item in selected) {
                        RecipeRow(item, cart[item.id] ?: 0, counts[item.id] ?: 0, { detail = item }, { adjust(item, it) })
                    }
                    Text("备菜清单 · 用量按每份列出")
                    for (item in selected) {
                        Text("${item.name} ×${cart[item.id]}：${item.ingredients}", fontSize = 13.sp)
                    }
                    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("口味备注或家里已有食材") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!includeSteps, { includeSteps = false }, { Text("只发菜单") })
                        FilterChip(includeSteps, { includeSteps = true }, { Text("菜单＋做法") })
                    }
                    Button(enabled = selected.isNotEmpty(), onClick = {
                        val share = buildString {
                            appendLine("$meal · ${people}人")
                            selected.forEach { item ->
                                appendLine("${item.name} ×${cart[item.id]}份")
                                if (includeSteps) {
                                    appendLine("每份食材：${item.ingredients}")
                                    appendLine(item.instructions())
                                }
                            }
                            if (note.isNotBlank()) appendLine("备注：$note")
                        }
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share)
                        runCatching { context.startActivity(Intent.createChooser(send, "分享菜单")) }.onSuccess {
                            last = cart.filterKeys { id -> selected.any { it.id == id } }
                            counts = counts + last.mapValues { (id, _) -> (counts[id] ?: 0) + 1 }
                            prefs.edit().putString("last", JSONObject(last).toString())
                                .putString("counts", JSONObject(counts).toString()).apply()
                        }
                    }) { Text("分享菜单") }
                }
                tab == "随机吃什么" -> Column(body.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!withStaple, { withStaple = false; draw = suggestions() }, { Text("一荤一素一汤") })
                        FilterChip(withStaple, { withStaple = true; draw = suggestions() }, { Text("主食＋菜") })
                    }
                    Button(onClick = { draw = suggestions() }) { Text("帮我搭一餐") }
                    selected.forEach { Text("保留 · ${it.name}", fontSize = 13.sp) }
                    for (item in draw) {
                        RecipeRow(item, cart[item.id] ?: 0, counts[item.id] ?: 0, { detail = item }, { adjust(item, it) })
                        TextButton(onClick = {
                            val replacement = recipes.filter {
                                it.category == item.category && it.id != item.id && (mode == "全家" || it.everyday)
                            }.randomOrNull()
                            if (replacement != null) draw = draw.map { if (it.id == item.id) replacement else it }
                        }) { Text("换一道") }
                    }
                    if (draw.isNotEmpty()) Button(onClick = {
                        draw.forEach { if (it.id !in cart) adjust(it, 1) }
                        menu = true
                    }) { Text("加入菜单") }
                }
                tab == "菜品管理" -> LazyColumn(body, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Button(onClick = {
                            editor = Recipe(UUID.randomUUID().toString(), "", "荤菜", 20, "", "", "", everyday = false)
                        }) { Text("新增菜品") }
                    }
                    items(recipes, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, Modifier.weight(1f))
                            TextButton(onClick = {
                                editor = if (item in mealRecipes) item.copy(
                                    id = UUID.randomUUID().toString(),
                                    photo = "file:///android_asset/meal/${item.id}.jpg",
                                ) else item
                            }) { Text(if (item in mealRecipes) "另存做法" else "编辑") }
                            if (item in custom) TextButton(onClick = {
                                custom = custom.filterNot { it.id == item.id }
                                saveCustom(prefs, custom)
                                saveCart(cart - item.id)
                            }) { Text("删除") }
                        }
                    }
                }
                else -> Column(body, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFE8F0E8)).padding(3.dp),
                    ) {
                        for (item in modes) {
                            val active = mode == item
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                    .background(if (active) mealColors.primary else Color.Transparent)
                                    .clickable(role = Role.Tab) {
                                        mode = item
                                        prefs.edit().putString("mode", mode).apply()
                                    }.heightIn(min = 42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item, fontSize = 13.sp, color = if (active) Color.White else mealColors.onSurfaceVariant)
                            }
                        }
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = mealColors.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(mealColors.primary),
                        decorationBox = { field ->
                            Row(
                                Modifier.clip(RoundedCornerShape(22.dp)).background(mealColors.surface)
                                    .border(1.dp, mealColors.outlineVariant, RoundedCornerShape(22.dp))
                                    .heightIn(min = 44.dp).padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = mealColors.primary)
                                Box(Modifier.weight(1f)) {
                                    if (search.isEmpty()) Text("搜菜名或食材", fontSize = 14.sp, color = mealColors.onSurfaceVariant)
                                    field()
                                }
                            }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(mealColors.secondaryContainer)
                            .clickable(role = Role.Button) {
                                withStaple = selected.isNotEmpty()
                                draw = suggestions()
                                tab = "随机吃什么"
                            }.heightIn(min = 44.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Casino, null, Modifier.size(18.dp), tint = mealColors.primary)
                        Text(if (selected.isEmpty()) "手气推荐 · 一荤一素一汤" else "帮我搭一餐",
                            color = mealColors.onSecondaryContainer, fontSize = 13.sp)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.width(88.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEFF4EE)).verticalScroll(rememberScrollState()).padding(3.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (item in categories) {
                                val active = category == item
                                val color = if (active) mealColors.secondaryContainer else Color.Transparent
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color)
                                        .clickable(role = Role.Tab) { category = item }.heightIn(min = 44.dp).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item, Modifier.weight(1f), fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(group(item).size.toString(), fontSize = 11.sp, color = mealColors.primary)
                                }
                            }
                        }
                        key(category) {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                val visible = group(category)
                                if (visible.isEmpty()) item { Text("这里还没有符合条件的菜", fontSize = 13.sp) }
                                items(visible, key = { it.id }) { item ->
                                    RecipeRow(item, cart[item.id] ?: 0, counts[item.id] ?: 0, { detail = item }, { adjust(item, it) })
                                }
                            }
                        }
                    }
                }
            }
        }
        editor?.let { item ->
            RecipeEditor(item, { editor = null }) { saved ->
                custom = custom.filterNot { it.id == saved.id } + saved
                saveCustom(prefs, custom)
                editor = null
            }
        }
    }
}

@Composable
private fun Quantity(amount: Int, minus: () -> Unit, plus: () -> Unit, label: String = "人数") {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (amount == 0) Color.Transparent else mealColors.secondaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (amount > 0) {
            IconButton(onClick = minus, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.Remove, "减少${label}", Modifier.size(17.dp), tint = mealColors.primary)
            }
            Text(amount.toString(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = mealColors.onSecondaryContainer)
        }
        IconButton(onClick = plus, modifier = Modifier.size(44.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (amount == 0) mealColors.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, "增加${label}", Modifier.size(17.dp),
                    tint = if (amount == 0) mealColors.onPrimary else mealColors.primary)
            }
        }
    }
}

@Composable
private fun DishPhoto(recipe: Recipe, modifier: Modifier) {
    val id = recipe.id.takeIf { candidate -> mealRecipes.any { it.id == candidate } }
    val url = recipe.photo.ifBlank { id?.let { "file:///android_asset/meal/$it.jpg" }.orEmpty() }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) AsyncImage(url, recipe.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("暂无图片", fontSize = 11.sp)
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, amount: Int, history: Int, open: () -> Unit, change: (Int) -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = mealColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, mealColors.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable(role = Role.Button, onClick = open)) {
                DishPhoto(recipe, Modifier.size(66.dp))
            }
            Column(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open)) {
                    Text(recipe.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(recipe.description.takeIf { it.isNotBlank() },
                        "点过 ${history}次".takeIf { history > 0 }).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp,
                        color = mealColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${recipe.minutes}分钟", Modifier.weight(1f), fontSize = 10.sp, color = mealColors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Quantity(amount, { change(-1) }, { change(1) }, recipe.name)
                }
            }
        }
    }
}

@Composable
private fun RecipeEditor(recipe: Recipe, close: () -> Unit, save: (Recipe) -> Unit) {
    val context = LocalContext.current
    var name by remember(recipe.id) { mutableStateOf(recipe.name) }
    var category by remember(recipe.id) { mutableStateOf(recipe.category) }
    var minutes by remember(recipe.id) { mutableStateOf(recipe.minutes.toString()) }
    var ingredients by remember(recipe.id) { mutableStateOf(recipe.ingredients) }
    var steps by remember(recipe.id) { mutableStateOf(recipe.steps.replace("｜", "\n")) }
    var photo by remember(recipe.id) { mutableStateOf(recipe.photo) }
    var local by remember(recipe.id) { mutableStateOf(recipe.local) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photo = uri.toString()
        }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("编辑菜谱") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("菜名") })
                TextButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                    Text(if (photo.isBlank()) "从相册选图" else "更换图片")
                }
                if (photo.isNotBlank()) DishPhoto(recipe.copy(photo = photo), Modifier.size(72.dp))
                FilterChip(local, { local = !local }, { Text("家乡菜") })
                for (item in dishCategories) FilterChip(category == item, { category = item }, { Text(item) })
                OutlinedTextField(minutes, { minutes = it }, label = { Text("预计分钟") })
                OutlinedTextField(ingredients, { ingredients = it }, label = { Text("每份食材（约2人）") })
                OutlinedTextField(steps, { steps = it }, label = { Text("做法（每行一步）") })
            }
        },
        confirmButton = {
            val time = minutes.toIntOrNull()
            TextButton(
                enabled = name.isNotBlank() && ingredients.isNotBlank() && steps.isNotBlank() && time != null && time in 1..1440,
                onClick = {
                    save(recipe.copy(name = name.trim(), category = category, minutes = time ?: 20,
                        ingredients = ingredients.trim(), photo = photo, local = local, steps = steps.lines().map(String::trim)
                            .filter(String::isNotEmpty).joinToString("｜")))
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = close) { Text("取消") } },
    )
}

private fun readCounts(value: String?): Map<String, Int> = runCatching {
    val json = JSONObject(value ?: "{}")
    json.keys().asSequence().associateWith { json.optInt(it) }.filterValues { it > 0 }
}.getOrDefault(emptyMap())

private fun readCustom(value: String?): List<Recipe> = runCatching {
    val json = JSONArray(value ?: "[]")
    List(json.length()) { index ->
        val item = json.getJSONObject(index)
        Recipe(item.getString("id"), item.getString("name"), item.getString("category"), item.getInt("minutes"),
            item.optString("description"), item.getString("ingredients"), item.getString("steps"),
            item.optBoolean("local"), item.optBoolean("everyday"), item.optString("source"), item.optString("photo"))
    }
}.getOrDefault(emptyList())

private fun saveCustom(prefs: android.content.SharedPreferences, recipes: List<Recipe>) {
    val data = JSONArray()
    recipes.forEach { item ->
        data.put(JSONObject().put("id", item.id).put("name", item.name).put("category", item.category)
            .put("minutes", item.minutes).put("description", item.description).put("ingredients", item.ingredients)
            .put("steps", item.steps).put("local", item.local).put("everyday", item.everyday)
            .put("source", item.source).put("photo", item.photo))
    }
    prefs.edit().putString("custom", data.toString()).apply()
}
