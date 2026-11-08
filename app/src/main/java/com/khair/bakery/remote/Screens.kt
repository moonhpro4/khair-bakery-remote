package com.khair.bakery.remote

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

val GreenBtn = Color(0xFF22B14C)
val RedBtn = Color(0xFFED1C24)
val Ink = Color(0xFF000000)
val Orange = Color(0xFFC25E00)
const val MOBILE_NOTE = "(تم الدفع عن طريق تطبيق الهاتف)"

sealed class Screen {
    object Shops : Screen()
    object AddComputer : Screen()
    object Me : Screen()
    data class Home(val shopId: String) : Screen()
    data class Pay(val shopId: String, val method: String) : Screen()
    data class ZReport(val shopId: String) : Screen()
    data class Shifts(val shopId: String) : Screen()
    data class Archive(val shopId: String) : Screen()
    data class EditCats(val shopId: String) : Screen()
}

sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    class Ok<T>(val data: T) : LoadState<T>()
    class Fail(val msg: String) : LoadState<Nothing>()
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun money(x: Double): String = String.format(Locale.US, "%.2f", x) + " د.إ"
fun hhmm(t: String): String = if (t.length >= 16) t.substring(11, 16) else t

// ============================================================================ root + navigation
@Composable
fun KhairRoot(openRequest: MutableState<Pair<String, String>?>) {
    var welcome by remember { mutableStateOf(true) }
    if (welcome) WelcomeScreen { welcome = false } else MainShell(openRequest)
}

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val spin = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        spin.animateTo(1f, tween(2200, easing = LinearEasing))
        fade.animateTo(1f, tween(600))
        delay(1200)
        onDone()
    }
    // the classic square rainbow: the frame cycles through every colour, then settles on the bakery red
    val frame = if (spin.value >= 1f) Color(0xFFD62828) else Color.hsv((spin.value * 1080f) % 360f, 0.9f, 1f)
    Column(
        Modifier.fillMaxSize().background(Color.White),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(230.dp).border(10.dp, frame), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(190.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "أهلاً بك في مخبز الخير",
            fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(fade.value)
        )
    }
}

@Composable
fun MainShell(openRequest: MutableState<Pair<String, String>?>) {
    val shops by Hub.shops.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Shops) }
    val bill = remember { mutableStateMapOf<String, Int>() }

    val req = openRequest.value
    LaunchedEffect(req) {
        if (req != null) {
            screen = if (req.second == "shifts") Screen.Shifts(req.first) else Screen.Home(req.first)
            openRequest.value = null
        }
    }

    BackHandler(enabled = screen !is Screen.Shops && screen !is Screen.Me && screen !is Screen.AddComputer) {
        val s = screen
        screen = when (s) {
            is Screen.Pay -> Screen.Home(s.shopId)
            is Screen.Home -> Screen.Shops
            is Screen.ZReport -> Screen.Home(s.shopId)
            is Screen.Shifts -> Screen.Home(s.shopId)
            is Screen.Archive -> Screen.Home(s.shopId)
            is Screen.EditCats -> Screen.Home(s.shopId)
            else -> Screen.Shops
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = screen is Screen.Me, onClick = { screen = Screen.Me },
                    icon = { Text("👤", fontSize = 22.sp) }, label = { Text("أنا") }
                )
                NavigationBarItem(
                    selected = screen !is Screen.Me && screen !is Screen.AddComputer,
                    onClick = { screen = Screen.Shops },
                    icon = { Text("🏪", fontSize = 22.sp) }, label = { Text("المحلات") }
                )
                NavigationBarItem(
                    selected = screen is Screen.AddComputer, onClick = { screen = Screen.AddComputer },
                    icon = { Text("➕", fontSize = 22.sp) }, label = { Text("إضافة كمبيوتر") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val s = screen
            val shop = when (s) {
                is Screen.Home -> Hub.shop(s.shopId)
                is Screen.Pay -> Hub.shop(s.shopId)
                is Screen.ZReport -> Hub.shop(s.shopId)
                is Screen.Shifts -> Hub.shop(s.shopId)
                is Screen.Archive -> Hub.shop(s.shopId)
                is Screen.EditCats -> Hub.shop(s.shopId)
                else -> null
            }
            when {
                s is Screen.Shops -> ShopsScreen(
                    shops,
                    onAdd = { screen = Screen.AddComputer },
                    onOpen = { bill.clear(); screen = Screen.Home(it) }
                )
                s is Screen.AddComputer -> AddComputerScreen { screen = Screen.Shops }
                s is Screen.Me -> MeScreen(shops)
                shop == null -> ShopsScreen(
                    shops,
                    onAdd = { screen = Screen.AddComputer },
                    onOpen = { bill.clear(); screen = Screen.Home(it) }
                )
                s is Screen.Home -> ShopHome(shop, bill) { screen = it }
                s is Screen.Pay -> PayScreen(shop, s.method, bill) { bill.clear(); screen = Screen.Home(shop.id) }
                s is Screen.ZReport -> TitledPage("تقرير اليوم (Z)") {
                    Loader(load = { Hub.request(shop, "zreport.get") }) { OrdersView(it) }
                }
                s is Screen.Shifts -> TitledPage("الورديات") { ShiftsScreen(shop) }
                s is Screen.Archive -> TitledPage("التقارير المؤرشفة") { ArchiveScreen(shop) }
                s is Screen.EditCats -> TitledPage("تعديل أسماء الأصناف") { EditCatsScreen(shop, bill) }
            }
        }
    }
}

// ============================================================================ small building blocks
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Ink, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun TitledPage(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        content()
    }
}

@Composable
fun <T> Loader(load: suspend () -> T, content: @Composable (T) -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    LaunchedEffect(tick) {
        state = LoadState.Loading
        state = try {
            LoadState.Ok(load())
        } catch (e: RemoteError) {
            LoadState.Fail(e.message ?: "حدث خطأ")
        }
    }
    when (val s = state) {
        is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LoadState.Fail -> Column(
            Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally
        ) {
            Text(s.msg, textAlign = TextAlign.Center, color = RedBtn, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { tick++ }) { Text("إعادة المحاولة") }
        }
        is LoadState.Ok -> Column(Modifier.fillMaxSize()) {
            OutlinedButton(onClick = { tick++ }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("🔄 تحديث") }
            content(s.data)
        }
    }
}

// ============================================================================ shops list  (mock-up 2)
@Composable
fun ShopsScreen(shops: List<Shop>, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val online by Hub.online.collectAsState()
    var askDelete by remember { mutableStateOf<Shop?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedButton(
            onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(4.dp), border = BorderStroke(3.dp, Ink)
        ) { Text("➕   إضافة كمبيوتر", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
        Spacer(Modifier.height(24.dp))
        if (shops.isEmpty()) {
            Text(
                "لا توجد محلات بعد.\nاضغط «إضافة كمبيوتر» لربط أول محل.",
                textAlign = TextAlign.Center, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
            )
        }
        for (shop in shops) {
            OutlinedButton(
                onClick = { onOpen(shop.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(4.dp), border = BorderStroke(3.dp, Ink)
            ) {
                Text(shop.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.size(12.dp))
                val up = online[shop.id] == true
                Text(if (up) "● متصل" else "○ غير متصل", color = if (up) GreenBtn else Color.Gray, fontSize = 13.sp)
            }
            TextButton(onClick = { askDelete = shop }) { Text("حذف هذا المحل من الهاتف", color = RedBtn, fontSize = 12.sp) }
        }
    }
    val d = askDelete
    if (d != null) {
        AlertDialog(
            onDismissRequest = { askDelete = null },
            title = { Text("حذف المحل") },
            text = { Text("سيتم فصل هذا الهاتف عن «${d.name}». لا يتأثر الكمبيوتر ولا بياناته.") },
            confirmButton = { TextButton(onClick = { Hub.removeShop(d.id); askDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text("إلغاء") } }
        )
    }
}

// ============================================================================ one shop  (mock-up 1)
@Composable
fun ShopHome(shop: Shop, bill: SnapshotStateMap<String, Int>, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        try {
            val d = Hub.request(shop, "state.get")
            cats = d.getJSONArray("categories").objects().map { it.getString("name") }
            err = null
        } catch (e: RemoteError) {
            err = e.message
        }
    }
    fun pay(method: String) {
        if (bill.isEmpty()) Toast.makeText(ctx, "الفاتورة فارغة، اختر صنفًا أولًا", Toast.LENGTH_SHORT).show()
        else go(Screen.Pay(shop.id, method))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(shop.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("💵\nنقدًا", Modifier.weight(1f), GreenBtn) { pay("cash") }
            Pill("💳\nبطاقة", Modifier.weight(1f), RedBtn) { pay("card") }
            Pill("عرض التقارير المؤرشفة", Modifier.weight(1.2f)) { go(Screen.Archive(shop.id)) }
        }
        if (bill.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6D6))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "الفاتورة: " + bill.entries.joinToString("، ") { "${it.key} ×${it.value}" },
                        modifier = Modifier.weight(1f), fontSize = 15.sp
                    )
                    TextButton(onClick = { bill.clear() }) { Text("مسح") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("عرض تقرير اليوم (Z)", Modifier.weight(1f)) { go(Screen.ZReport(shop.id)) }
            Pill("عرض الورديات", Modifier.weight(1.3f)) { go(Screen.Shifts(shop.id)) }
        }
        Spacer(Modifier.height(16.dp))
        if (err != null) {
            Text(err ?: "", color = RedBtn, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { tick++ }) { Text("إعادة المحاولة") }
        }
        for (row in cats.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (name in row) {
                    val q = bill[name] ?: 0
                    Pill(if (q > 0) "$name  ×$q" else name, Modifier.weight(1f)) { bill[name] = q + 1 }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill("تعديل أسماء الأصناف وإضافة صنف", Modifier.fillMaxWidth()) { go(Screen.EditCats(shop.id)) }
    }
}

// ============================================================================ payment (never prints)
@Composable
fun PayScreen(shop: Shop, method: String, bill: SnapshotStateMap<String, Int>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prices = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }
    val names = bill.keys.toList()
    val total = names.sumOf { prices[it]?.toDoubleOrNull() ?: 0.0 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (method == "cash") "الدفع نقدًا 💵" else "الدفع بالبطاقة 💳",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "اكتب سعر كل صنف ثم اضغط دفع. لن تتم الطباعة، تُسجَّل العملية مباشرة في تقرير Z على الكمبيوتر $MOBILE_NOTE",
            fontSize = 13.sp, color = Color.DarkGray, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        for (name in names) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$name  ×${bill[name] ?: 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = prices[name] ?: "",
                    onValueChange = { v -> prices[name] = v.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text("السعر") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("الإجمالي: ${money(total)}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Pill(
            if (busy) "جاري الحفظ..." else "دفع",
            Modifier.fillMaxWidth(), if (method == "cash") GreenBtn else RedBtn
        ) {
            if (busy) return@Pill
            if (total <= 0.0) {
                Toast.makeText(ctx, "اكتب السعر أولًا", Toast.LENGTH_SHORT).show()
                return@Pill
            }
            busy = true
            scope.launch {
                try {
                    val items = JSONArray()
                    for (n in names) {
                        items.put(
                            JSONObject().put("name", n).put("qty", bill[n] ?: 1)
                                .put("price", prices[n]?.toDoubleOrNull() ?: 0.0)
                        )
                    }
                    Hub.request(shop, "order.create", JSONObject().put("items", items).put("method", method))
                    Toast.makeText(ctx, "تم تسجيل العملية في تقرير Z", Toast.LENGTH_LONG).show()
                    onDone()
                } catch (e: RemoteError) {
                    Toast.makeText(ctx, e.message ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                } finally {
                    busy = false
                }
            }
        }
    }
}

// ============================================================================ Z-report / archive day
@Composable
fun OrdersView(d: JSONObject) {
    val orders = d.getJSONArray("orders").objects()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))) {
            Column(Modifier.padding(14.dp)) {
                Text("نقدًا: " + money(d.optDouble("cash_total")), color = GreenBtn, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("بطاقة: " + money(d.optDouble("card_total")), color = RedBtn, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("الإجمالي: " + money(d.optDouble("total")), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("عدد العمليات: ${orders.size}", fontSize = 14.sp)
            }
        }
        if (orders.isEmpty()) Text("لا توجد عمليات.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        for (o in orders.reversed()) {
            val cash = o.optString("method") != "card"
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.LightGray),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${o.optInt("id")}   ${hhmm(o.optString("time"))}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(if (cash) "نقدًا" else "بطاقة", color = if (cash) GreenBtn else RedBtn, fontWeight = FontWeight.Bold)
                    }
                    for (line in o.getJSONArray("items").objects()) {
                        Text("• ${line.optString("name")} ×${line.optInt("qty")}  —  ${money(line.optDouble("price"))}", fontSize = 14.sp)
                    }
                    Text(money(o.optDouble("total")), fontWeight = FontWeight.Bold)
                    if (o.optBoolean("mobile")) Text(MOBILE_NOTE, color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================ shifts
@Composable
fun ShiftsScreen(shop: Shop) {
    Loader(load = { Hub.request(shop, "shifts.get") }) { d ->
        val people = d.getJSONArray("employees").objects()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (people.isEmpty()) {
                Text(
                    "لا يوجد موظفون مضافون، الرجاء إضافة موظفين عن طريق الكمبيوتر",
                    fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            } else {
                Text("اليوم: " + d.optString("date"), fontSize = 14.sp, color = Color.Gray)
                ShiftGroup("لم يبدأوا الوردية", RedBtn, people.filter { it.optString("status") == "absent" }) { "لم يبدأ بعد" }
                ShiftGroup("بدأوا الوردية (يعملون الآن)", GreenBtn, people.filter { it.optString("status") == "working" }) {
                    "بدأ الساعة " + it.optString("start")
                }
                ShiftGroup("أنهوا الوردية", Ink, people.filter { it.optString("status") == "ended" }) {
                    "من " + it.optString("start") + " إلى " + it.optString("end") +
                        (if (it.optString("worked").isNotEmpty()) "  (" + it.optString("worked") + ")" else "")
                }
            }
        }
    }
}

@Composable
fun ShiftGroup(title: String, color: Color, list: List<JSONObject>, detail: (JSONObject) -> String) {
    if (list.isEmpty()) return
    Text("$title (${list.size})", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    for (p in list) {
        Card(Modifier.fillMaxWidth(), border = BorderStroke(2.dp, color), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(12.dp)) {
                Text(p.optString("name"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(detail(p), fontSize = 14.sp, color = Color.DarkGray)
            }
        }
    }
}

// ============================================================================ archive
@Composable
fun ArchiveScreen(shop: Shop) {
    var date by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = date != null) { date = null }
    val chosen = date
    if (chosen == null) {
        Loader(load = { Hub.request(shop, "archive.dates") }) { d ->
            val dates = d.getJSONArray("dates")
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dates.length() == 0) Text("لا توجد تقارير مؤرشفة بعد.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                for (i in 0 until dates.length()) {
                    val day = dates.getString(i)
                    Pill(day, Modifier.fillMaxWidth()) { date = day }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Text(chosen, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Loader(load = { Hub.request(shop, "archive.day", JSONObject().put("date", chosen)) }) { OrdersView(it) }
        }
    }
}

// ============================================================================ edit category names / add one
@Composable
fun EditCatsScreen(shop: Shop, bill: SnapshotStateMap<String, Int>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<Int?, String>?>(null) }
    var toDelete by remember { mutableStateOf<Pair<Int, String>?>(null) }
    key(version) {
        Loader(load = { Hub.request(shop, "state.get") }) { d ->
            val cats = d.getJSONArray("categories").objects()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("➕ إضافة صنف جديد", Modifier.fillMaxWidth(), GreenBtn) { dialog = Pair(null, "") }
                for (c in cats) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.optString("name"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { dialog = Pair(c.optInt("id"), c.optString("name")) }) { Text("✏️ تعديل الاسم") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { toDelete = Pair(c.optInt("id"), c.optString("name")) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedBtn)
                        ) { Text("🗑️ حذف") }
                    }
                }
            }
        }
    }
    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف الصنف") },
            text = { Text("هل تريد حذف \"${del.second}\" من الكمبيوتر؟ لا يمكن التراجع عن هذا.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            Hub.request(shop, "category.delete", JSONObject().put("id", del.first).put("name", del.second))
                            bill.remove(del.second)
                            Toast.makeText(ctx, "تم الحذف من الكمبيوتر", Toast.LENGTH_SHORT).show()
                            toDelete = null
                            version++
                        } catch (e: RemoteError) {
                            Toast.makeText(ctx, e.message ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("حذف", color = RedBtn) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } }
        )
    }
    val dlg = dialog
    if (dlg != null) {
        var text by remember(dlg) { mutableStateOf(dlg.second) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dlg.first == null) "إضافة صنف" else "تعديل الاسم") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("اسم الصنف") }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val args = JSONObject().put("name", text)
                            val id = dlg.first
                            if (id != null) args.put("id", id)
                            Hub.request(shop, if (id == null) "category.add" else "category.rename", args)
                            Toast.makeText(ctx, "تم الحفظ على الكمبيوتر", Toast.LENGTH_SHORT).show()
                            dialog = null
                            version++
                        } catch (e: RemoteError) {
                            Toast.makeText(ctx, e.message ?: "حدث خطأ", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } }
        )
    }
}

// ============================================================================ add a computer (pairing)
@Composable
fun AddComputerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("هاتفي") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("إضافة كمبيوتر", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "على الكمبيوتر اضغط  Ctrl + Alt + P  لفتح شاشة الربط السرية، ثم انسخ رمز الهاتف والرقم السري (PIN) المعروضين هناك.",
            fontSize = 14.sp, color = Color.DarkGray
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it.trim() }, label = { Text("رمز الاتصال (يبدأ بـ m_)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("الرقم السري (6 أرقام)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) }, label = { Text("اسم هذا الهاتف") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (error != null) Text(error ?: "", color = RedBtn, fontWeight = FontWeight.Bold)
        Pill(if (busy) "جاري الربط..." else "ربط", Modifier.fillMaxWidth(), GreenBtn) {
            if (busy) return@Pill
            if (!code.startsWith("m_") || pin.length != 6 || name.isBlank()) {
                error = "تأكد من الرمز (يبدأ بـ m_) والرقم السري (6 أرقام) واسم الهاتف"
                return@Pill
            }
            error = null
            busy = true
            scope.launch {
                try {
                    val shop = Hub.pair(code, pin, name.trim())
                    Hub.addShop(shop)
                    Toast.makeText(ctx, "تم ربط «${shop.name}»", Toast.LENGTH_LONG).show()
                    code = ""
                    pin = ""
                    onDone()
                } catch (e: RemoteError) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
    }
}

// ============================================================================ me
@Composable
fun MeScreen(shops: List<Shop>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(110.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
            Text("ش", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        // the profile is always this name
        Text("شادي عبد اللطيف", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("مدير المحلات", fontSize = 15.sp, color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1F1))) {
            Column(Modifier.padding(14.dp)) {
                Text("عدد المحلات المرتبطة: ${shops.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (shops.isNotEmpty()) {
            Text("إشعارات الورديات", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            for (s in shops) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(checked = s.notify, onCheckedChange = { Hub.setNotify(s.id, it) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("مخبز الخير — التحكم عن بعد (الإصدار 1.0)", fontSize = 12.sp, color = Color.Gray)
    }
}
