package com.eric.toolbox.modules.meal

import java.math.BigDecimal

internal fun Recipe.cookingSteps() = if (id == "melon") winterMelonSteps else steps.split('｜')

internal fun Recipe.ingredientsFor(people: Int): List<Pair<String, String>> =
    ingredients.split('、', '\n').mapNotNull { item ->
        val clean = item.trim()
        if (clean.isEmpty()) return@mapNotNull null
        val name = clean.substringBefore(' ')
        val amount = clean.substringAfter(' ', "")
        val match = measurableIngredient.matchEntire(amount)
        val quantity = if (match == null) amount else {
            val (approximate, number, unit, suffix) = match.destructured
            val scaled = number.toBigDecimal().multiply(BigDecimal(people)).divide(BigDecimal(2))
            "$approximate${scaled.stripTrailingZeros().toPlainString()}$unit$suffix"
        }
        name to quantity
    }

private val measurableIngredient = Regex("""(约\s*)?(\d+(?:\.\d+)?)(毫升|千克|克|大勺|小勺|个|只|根|颗|片|朵|瓣|枚|张|粒)\s*(可选)?""")

internal data class RecipeGuide(val nutrition: String, val pairing: String)

private fun guide(nutrition: String, pairing: String) = RecipeGuide(nutrition, pairing)

internal val recipeGuides = mapOf(
    "tomato" to guide("鸡蛋提供蛋白质，番茄提供维生素 C，炒出汁后能给鸡蛋带来自然的酸甜味。", "配米饭和清炒小白菜。"),
    "chicken" to guide("鸡腿肉提供蛋白质，香菇带来鲜味，也能增加这一餐的食材种类。", "配杂粮饭和西兰花炒胡萝卜。"),
    "greens" to guide("小白菜属于绿叶菜，提供膳食纤维和维生素。", "配香菇蒸鸡和米饭。"),
    "melon" to guide("冬瓜水分多，煮软后口感清爽，也含维生素 C。丸子里的瘦猪肉提供蛋白质，所以喝汤时也把冬瓜和丸子吃上。", "配米饭和清炒小白菜。这碗汤已有肉，可以先按食量决定是否再加荤菜。"),
    "rice" to guide("大米主要提供碳水化合物，是一餐的主食。", "和肉、蛋或豆腐，再配一份蔬菜。"),
    "fenji" to guide("鸡肉提供蛋白质，裹粉给鸡肉增加滑嫩口感。", "配青菜和少量米饭，鸡肉也一起吃。"),
    "bones" to guide("牛骨上吃得到的牛肉提供蛋白质和铁。", "配米饭和炒绿叶菜。"),
    "lamb" to guide("羊肉提供蛋白质和铁，喝汤时也可以吃些炖软的羊肉。", "配布袋馍和一份青菜。"),
    "noodles" to guide("粗面条提供碳水化合物，芝麻酱里的脂肪使拌面更香。", "配蒸水蛋或鸡肉，再补一份蔬菜。"),
    "pocket" to guide("面粉制成的蒸馍主要提供碳水化合物，口袋能夹入熟菜。", "夹熟鸡丝和熟青菜，就能把主食和两类配菜放在一起。"),
    "buns" to guide("面皮提供碳水化合物，肉馅提供蛋白质，白菜增加食材种类。", "配一份熟蔬菜，按食量决定煎包个数。"),
    "dumpling" to guide("饺子皮是主食的一部分，猪肉馅提供蛋白质。", "配一份清淡蔬菜或白菜豆腐汤。"),
    "mustard" to guide("腌芥菜主要用来调味，成品通常含盐较多。", "少量佐餐，另外搭配新鲜蔬菜。"),
    "trotter" to guide("猪蹄含蛋白质和脂肪，卤汁也会增加一餐的盐分。", "想吃家乡口味时可和清淡绿叶菜、米饭一起点。"),
    "fish" to guide("鱼肉提供蛋白质，椒盐和煎炸决定这道菜的风味。", "配素菜和米饭，椒盐可另放。"),
    "roll" to guide("馍皮提供碳水化合物，鸡蛋和面筋提供蛋白质，豆芽补充蔬菜种类。", "还可以配一碗热汤或一份熟青菜。"),
    "wheat" to guide("杂面条提供碳水化合物，熟鸡蛋提供蛋白质。", "按实际菜量再配一份绿叶菜。"),
    "beef" to guide("牛腱提供蛋白质和铁，切薄更方便分食。", "配米饭和胡萝卜炒藕片，卤汁少加。"),
    "twist" to guide("麻馓主要由面粉制成，油炸让它酥脆，也增加油脂。", "作为小吃少量搭配，正餐可另配主食、蔬菜和蛋白质菜。"),
    "porridge" to guide("米粉提供碳水化合物，芝麻和花生提供脂肪及一些蛋白质。", "配蒸水蛋和熟青菜。含花生和芝麻。"),
    "broccoli" to guide("西兰花提供维生素 C，胡萝卜提供 β-胡萝卜素。", "配米饭、蒸鸡或蒸鱼。"),
    "mushroomtofu" to guide("豆腐提供大豆蛋白，香菇增鲜。", "配米饭和一份深色叶菜。"),
    "millet" to guide("小米提供碳水化合物，南瓜提供 β-胡萝卜素。", "配鸡蛋和一份蔬菜，粥按主食算。"),
    "sweetpotato" to guide("红薯提供碳水化合物和膳食纤维。", "用它替换一部分米饭，配熟蔬菜和鸡肉。"),
    "eggcustard" to guide("鸡蛋提供蛋白质，蒸成蛋羹后质地柔软。", "配小米粥和软炒青菜。"),
    "zucchini" to guide("西葫芦水分多，胡萝卜带来 β-胡萝卜素。", "配米饭和肉末蒸豆腐。"),
    "tomatobeef" to guide("牛肉提供蛋白质和铁，番茄增加蔬菜种类并给炖汁带来酸味。", "配米饭和绿叶菜。"),
    "seabass" to guide("鱼肉提供蛋白质，清蒸便于按口味控制调料。", "配主食和两色蔬菜，吃鱼前仔细去刺。"),
    "cornchicken" to guide("鸡腿提供蛋白质，玉米含淀粉，胡萝卜提供 β-胡萝卜素。", "吃到汤里的肉和蔬菜。玉米吃得多时，可少盛一些米饭。"),
    "cabbagesoup" to guide("白菜提供蔬菜成分，豆腐提供大豆蛋白。", "搭配杂粮饭，按食量加一份鸡肉或蛋。"),
    "banji" to guide("板鸡的鸡肉提供蛋白质，加工后的咸度取决于产品。", "偶尔吃家乡味时配米饭和少盐蔬菜。"),
    "lambleg" to guide("羊蹄含蛋白质和脂肪，家常版做成冷藏定型的菜。", "配熟蔬菜和主食。想吃热菜时可选临泉羊肉汤。"),
    "huangzi" to guide("肉馅提供蛋白质，白菜增加蔬菜，淀粉帮助肉团成形。", "配米饭或馍，加一份炒青菜。"),
    "pillow" to guide("枕头馍是面食，主要提供碳水化合物。", "切片配羊肉汤和熟青菜。"),
    "banmian" to guide("宽面提供碳水化合物，牛肉卤中的肉提供蛋白质和铁。", "青菜可以多配一些，卤汁和辣椒按口味加。"),
    "grapefish" to guide("鱼肉提供蛋白质，裹粉和酸甜汁主要带来口感与风味。", "配绿叶菜和米饭，酸甜汁可另放。"),
    "hock" to guide("肘子含蛋白质和脂肪，冰糖和生抽用于调味。", "与清淡蔬菜和主食同餐，浓汁可以少淋。"),
    "pisi" to guide("皮丝来自猪皮，青菜让这道菜多一种蔬菜食材。", "可另搭配豆腐或鸡蛋，再加主食。"),
    "tripe" to guide("牛肚能提供蛋白质，红烧的盐分取决于调味。", "配米饭和一份少盐青菜。"),
    "tofuherbs" to guide("腐竹提供大豆蛋白，荆芥主要增加香气。", "当作小菜，配一份热蔬菜和主食。"),
    "shiitakegreens" to guide("上海青提供绿叶菜中的膳食纤维及维生素，香菇带来鲜味。", "配米饭和清蒸鱼。"),
    "beans" to guide("四季豆是蔬菜，可增加膳食纤维摄入。", "配米饭和鸡肉；四季豆需要彻底熟透。"),
    "glassnoodles" to guide("白菜增加蔬菜种类，粉丝主要提供淀粉。", "配蒸鸡或蒸蛋，粉丝量多时相应调整米饭。"),
    "eggplant" to guide("茄子提供膳食纤维，先蒸软更方便用少量油烧入味。", "配米饭和瘦肉或豆腐。"),
    "lotus" to guide("莲藕含淀粉，胡萝卜提供 β-胡萝卜素。", "配番茄炖牛肉，主食按食量添。"),
    "carrotpork" to guide("瘦猪肉提供蛋白质，胡萝卜提供 β-胡萝卜素。", "配米饭和一份绿叶菜。"),
    "pepperchicken" to guide("鸡胸肉提供蛋白质，甜椒提供维生素 C。", "配米饭和香菇炒上海青。"),
    "mincedtofu" to guide("豆腐提供大豆蛋白，瘦猪肉同时提供动物性蛋白质。", "配米饭和一份熟青菜。"),
    "chestnut" to guide("鸡肉提供蛋白质，板栗含淀粉。", "搭配绿叶菜，板栗吃得多时可少盛米饭。"),
    "shrimp" to guide("虾仁和豆腐分别提供动物性和植物性蛋白质。", "配米饭和熟青菜。含虾和大豆。"),
    "tomatosoup" to guide("蛋花提供蛋白质，番茄给清汤带来自然酸味。", "配主食和一份绿叶菜，按食量选其他荤菜。"),
    "seaweed" to guide("豆腐提供大豆蛋白，紫菜增鲜并含碘。", "和米饭、蔬菜同餐。已有碘摄入医嘱时按医嘱选。"),
    "pumpkin" to guide("南瓜含 β-胡萝卜素，红枣增加甜味。", "可作为配菜，搭配豆腐或鸡蛋和绿叶菜。"),
    "blackrice" to guide("黑米和大米主要提供碳水化合物，全谷黑米也能增加膳食纤维。", "粥作主食，配蒸蛋和熟青菜。"),
    "corn" to guide("玉米提供碳水化合物和膳食纤维。", "当作主食的一部分，配蛋白质菜和蔬菜。"),
    "sesameflat" to guide("面粉提供碳水化合物，芝麻增加香气和脂肪。", "配白菜豆腐汤和一份熟蔬菜。"),
    "vegbao" to guide("包子皮提供碳水化合物，青菜和香菇增加蔬菜与菌菇种类。", "配鸡蛋、瘦肉或豆腐，再按食量加菜。"),
    "kelp" to guide("海带含膳食纤维和碘，不同产品的碘含量有差别。", "少量作配菜，搭配熟蛋和米饭。有碘摄入医嘱时按医嘱选。"),
    "cucumberfungus" to guide("黄瓜水分多，木耳增加膳食纤维和口感。", "趁温热吃，配米饭和香菇蒸鸡。"),
    "redbean" to guide("红豆和大米提供碳水化合物，红豆也含膳食纤维与植物蛋白。", "粥作主食，配蒸蛋和熟青菜。"),
)

internal val winterMelonSteps = listOf(
    "备料。冬瓜去皮去籽，切成约半厘米厚的片；姜一部分切末，一部分切片。生肉和蔬菜分开处理。",
    "拌肉。瘦猪肉馅加姜末、淀粉和少许盐，分次加一两勺清水，顺一个方向搅到发黏，用勺分成大小接近的小丸子。",
    "下丸子。锅里加水和姜片，水开后转小火，让水面保持微沸。逐个下丸子，等表面定型再轻推。",
    "煮冬瓜。下冬瓜片，小火煮到冬瓜变透明、能轻松夹断；用食品温度计确认最大肉丸中心至少 71°C，再少量盐调味。"
)
