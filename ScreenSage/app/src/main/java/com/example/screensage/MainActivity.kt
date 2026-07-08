package com.example.screensage

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import java.util.Calendar
import android.graphics.Color as AndroidColor

// ─── Color Tokens (colorblind-safe) ────────────────────────────────────────
val CyanBrand   = Color(0xFF00B4E6)
val NavyBg      = Color(0xFF0A1628)
val NavyCard    = Color(0xFF111E33)
val Teal700     = Color(0xFF0F6E56)
val Teal500     = Color(0xFF1D9E75)
val Teal50      = Color(0xFFE1F5EE)
val Amber700    = Color(0xFFBA7517)
val Amber400    = Color(0xFFEF9F27)
val Amber50     = Color(0xFFFAEEDA)
val Blue700     = Color(0xFF185FA5)
val Blue400     = Color(0xFF378ADD)
val Blue50      = Color(0xFFE6F1FB)
val Red700      = Color(0xFFA32D2D)
val Red50       = Color(0xFFFCEBEB)
val Green700    = Color(0xFF3B6D11)
val Green400    = Color(0xFF639922)
val Green50     = Color(0xFFEAF3DE)
val Gray900     = Color(0xFF2C2C2A)
val Gray600     = Color(0xFF5F5E5A)
val Gray400     = Color(0xFF888780)
val Gray50      = Color(0xFFF1EFE8)
val Surface     = Color(0xFFFFFFFF)
val Surface2    = Color(0xFFF9F8F4)
val BorderColor = Color(0x1A000000)

// ─── Data classes ────────────────────────────────────────────────────────────
data class AppUsage(
    val packageName: String,
    val appName: String,
    val minutes: Long,
    val limitMinutes: Long = 60
)

data class ChildInfo(val childId: String = "", val childName: String = "")

data class DailyUsageReport(val dateKey: String, val label: String, val totalMinutes: Long)

data class RewardInfo(
    val browniePoints: Long  = 0,
    val totalEarned: Long    = 0,
    val totalRedeemed: Long  = 0,
    val todayExtraUsed: Long = 0,
    val lastExtraDate: String  = "",
    val lastRewardWeek: String = ""
)

// ─── Multilingual Support ───────────────────────────────────────────────────
enum class AppLang { EN, HI }

object T {
    var lang by mutableStateOf(AppLang.EN)

    /** Returns [en] or [hi] based on current language setting. */
    fun t(en: String, hi: String): String = if (lang == AppLang.HI) hi else en

    /** Translates well-known app names to Hindi when in Hindi mode. */
    fun appName(name: String): String {
        if (lang == AppLang.EN) return name
        return when (name.lowercase()) {
            "instagram"                          -> "इंस्टाग्राम"
            "whatsapp"                           -> "व्हाट्सऐप"
            "youtube"                            -> "यूट्यूब"
            "snapchat"                           -> "स्नैपचैट"
            "system launcher", "launcher"        -> "सिस्टम लॉन्चर"
            "swiggy", "in.swiggy.android"        -> "स्विगी"
            "com.android.camera", "camera"       -> "कैमरा"
            "disney+ hotstar", "hotstar"         -> "डिज्नी+ हॉटस्टार"
            "chrome"                             -> "क्रोम"
            "chatgpt"                            -> "चैटजीपीटी"
            "truecaller"                         -> "ट्रूकॉलर"
            "spotify"                            -> "स्पॉटिफाई"
            "discord"                            -> "डिस्कॉर्ड"
            "facebook"                           -> "फेसबुक"
            "telegram"                           -> "टेलीग्राम"
            "gmail"                              -> "जीमेल"
            "google"                             -> "गूगल"
            "phone", "dialer"                    -> "फोन"
            "messages"                           -> "मैसेज"
            "photos", "gallery"                  -> "फोटो/गैलरी"
            "maps"                               -> "मैप्स"
            "meet"                               -> "मीट"
            "classroom"                          -> "क्लासरूम"
            "settings"                           -> "सेटिंग्स"
            else                                 -> name
        }
    }

    /** Formats minutes as "Xh Ym" / "Xघं Yमि". */
    fun hm(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (lang == AppLang.HI) "${h}घं ${m}मि" else "${h}h ${m}m"
    }

    /** Formats minutes as "X min" / "X मिनट". */
    fun min(minutes: Long): String =
        if (lang == AppLang.HI) "$minutes मिनट" else "$minutes min"
}

// ─── Activity ────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPostNotificationPermissionIfNeeded(this)
        setContent { ScreenSageApp(this) }
    }
}

// ─── Root composable ─────────────────────────────────────────────────────────
@Composable
fun ScreenSageApp(context: Context) {
    var screen              by remember { mutableStateOf("home") }
    var usageList           by remember { mutableStateOf(listOf<AppUsage>()) }
    var currentParentId     by remember { mutableStateOf("") }
    var connectedParentId   by remember { mutableStateOf("") }
    var connectedChildId    by remember { mutableStateOf("") }
    var connectedChildName  by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Surface2) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    "home" -> HomeScreen(
                        onChildClick  = { screen = "child_setup" },
                        onParentClick = { screen = "parent_auth" }
                    )
                    "parent_auth" -> ParentAuthScreen(
                        onLoginClick    = { screen = "parent_login" },
                        onRegisterClick = { screen = "parent_register" },
                        onBack          = { screen = "home" }
                    )
                    "parent_login" -> ParentLoginScreen(
                        onLoginSuccess = { id -> currentParentId = id; screen = "parent" },
                        onBack         = { screen = "parent_auth" }
                    )
                    "parent_register" -> ParentRegisterScreen(
                        onRegisterSuccess = { id -> currentParentId = id; screen = "parent" },
                        onBack            = { screen = "parent_auth" }
                    )
                    "child_setup" -> ChildSetupScreen(
                        onConnect = { pId, cId, cName ->
                            connectedParentId  = pId
                            connectedChildId   = cId
                            connectedChildName = cName
                            screen = "child"
                        },
                        onBack = { screen = "home" }
                    )
                    "child" -> ChildTabScreen(
                        usageList    = usageList,
                        parentId     = connectedParentId,
                        childId      = connectedChildId,
                        childName    = connectedChildName,
                        onFetchUsage = {
                            if (connectedParentId.isBlank() || connectedChildId.isBlank()) {
                                Toast.makeText(
                                    context,
                                    T.t("Please connect to a Parent ID first", "कृपया पहले अभिभावक ID से जोड़ें"),
                                    Toast.LENGTH_SHORT
                                ).show()
                                screen = "child_setup"
                                return@ChildTabScreen
                            }
                            if (hasUsagePermission(context)) {
                                val data = getTodayUsageStats(context)
                                usageList = data
                                uploadToFirebase(data, connectedParentId, connectedChildId, connectedChildName)
                                checkAndSendLimitAlertsFromFirebase(
                                    context      = context,
                                    parentId     = connectedParentId,
                                    childId      = connectedChildId,
                                    childName    = connectedChildName,
                                    isParentPhone = false
                                )
                                Toast.makeText(
                                    context,
                                    T.t("Data sent to parent dashboard", "अभिभावक डैशबोर्ड पर डेटा भेज दिया गया"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    T.t("Please allow Usage Access permission", "उपयोग अनुमति चालू करें"),
                                    Toast.LENGTH_LONG
                                ).show()
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                        autoSync = {
                            if (hasUsagePermission(context)
                                && connectedParentId.isNotBlank()
                                && connectedChildId.isNotBlank()
                            ) {
                                val data = getTodayUsageStats(context)
                                usageList = data
                                uploadToFirebase(data, connectedParentId, connectedChildId, connectedChildName)
                                checkAndSendLimitAlertsFromFirebase(
                                    context       = context,
                                    parentId      = connectedParentId,
                                    childId       = connectedChildId,
                                    childName     = connectedChildName,
                                    isParentPhone = false
                                )
                            }
                        },
                        onBack = { screen = "home" }
                    )
                    "parent" -> ParentTabScreen(
                        parentId = currentParentId,
                        onBack   = {
                            FirebaseAuth.getInstance().signOut()
                            currentParentId = ""
                            screen = "home"
                        }
                    )
                }

                // Language toggle always visible in top-right corner
                LanguageToggle(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(onChildClick: () -> Unit, onParentClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue  = 0.30f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
    ) {
        // Glow blob — top left
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(CyanBrand.copy(alpha = glowAlpha))
                .blur(radius = 90.dp)
        )
        // Glow blob — bottom right
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 80.dp)
                .clip(CircleShape)
                .background(CyanBrand.copy(alpha = glowAlpha * 0.55f))
                .blur(radius = 70.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── S logo drawn in Canvas ────────────────────────────────────
            Canvas(modifier = Modifier.size(88.dp)) {
                val w      = size.width
                val h      = size.height
                val strokeW = w * 0.115f
                val cyan   = CyanBrand

                drawRoundRect(
                    color        = cyan,
                    topLeft      = Offset(w * 0.14f, h * 0.07f),
                    size         = Size(w * 0.72f, h * 0.33f),
                    cornerRadius = CornerRadius(h * 0.165f),
                    style        = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawRoundRect(
                    color        = cyan,
                    topLeft      = Offset(w * 0.14f, h * 0.60f),
                    size         = Size(w * 0.72f, h * 0.33f),
                    cornerRadius = CornerRadius(h * 0.165f),
                    style        = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.24f)
                    cubicTo(w * 0.50f, h * 0.24f, w * 0.14f, h * 0.38f, w * 0.14f, h * 0.50f)
                    cubicTo(w * 0.14f, h * 0.62f, w * 0.50f, h * 0.76f, w * 0.50f, h * 0.76f)
                }
                drawPath(path = path, color = cyan, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text        = "ScreenSage",
                fontSize    = 33.sp,
                fontWeight  = FontWeight.Bold,
                color       = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(10.dp))

            Box(
                Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(CyanBrand.copy(alpha = 0.13f))
                    .border(1.dp, CyanBrand.copy(alpha = 0.38f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 18.dp, vertical = 7.dp)
            ) {
                Text(
                    text       = T.t("Smart digital wellness for families", "परिवारों के लिए समझदार डिजिटल देखभाल"),
                    fontSize   = 12.sp,
                    color      = CyanBrand,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(42.dp))

            // Role card
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NavyCard)
                    .border(1.dp, CyanBrand.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = T.t("Choose your role", "अपनी भूमिका चुनें"),
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text      = T.t(
                            "Track usage, set limits, and build healthier digital habits.",
                            "उपयोग देखें, सीमाएँ सेट करें और बेहतर डिजिटल आदतें बनाएं।"
                        ),
                        fontSize  = 12.sp,
                        color     = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(26.dp))

                    Button(
                        onClick  = onChildClick,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(50.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = CyanBrand)
                    ) {
                        Text(
                            T.t("👦  Continue as Child", "👦  बच्चे के रूप में जारी रखें"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            color      = NavyBg
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick  = onParentClick,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(50.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.5.dp, CyanBrand.copy(alpha = 0.55f))
                    ) {
                        Text(
                            T.t("👨‍👩‍👧  Continue as Parent", "👨‍👩‍👧  अभिभावक के रूप में जारी रखें"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            color      = CyanBrand
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50.dp)).background(CyanBrand.copy(0.30f)))
                Box(Modifier.width(24.dp).height(8.dp).clip(RoundedCornerShape(50.dp)).background(CyanBrand.copy(0.90f)))
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50.dp)).background(CyanBrand.copy(0.30f)))
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text     = T.t("Focus better · Sleep better · Live healthier", "बेहतर ध्यान · बेहतर नींद · स्वस्थ जीवन"),
                fontSize = 11.sp,
                color    = Color.White.copy(0.30f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELP DIALOG
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HelpInfoDialog(title: String, points: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(T.t("Got it", "समझ गया"), color = Teal700, fontWeight = FontWeight.SemiBold)
            }
        },
        title = { Text(title, fontWeight = FontWeight.Bold, color = Teal700) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                points.forEach { point ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = Teal700, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(point, fontSize = 13.sp, color = Gray600, lineHeight = 18.sp)
                    }
                }
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// AUTH SCREENS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun ParentAuthScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }

    AuthScaffold(
        title    = T.t("Parent account", "अभिभावक खाता"),
        subtitle = T.t("Login if you already have an account.", "अगर आपका खाता पहले से बना है तो प्रवेश करें।")
    ) {
        SageCard(color = Blue50) {
            Text(
                T.t("Need help?", "मदद चाहिए?"),
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Blue700
            )
            Text(
                T.t(
                    "Use Help if you are confused about login, registration, or Parent ID.",
                    "Login, registration या Parent ID समझने में दिक्कत हो तो Help खोलें।"
                ),
                fontSize = 12.sp,
                color    = Gray600
            )
            Spacer(Modifier.height(8.dp))
            SageOutlineButton(T.t("Open help", "मदद खोलें"), onClick = { showHelp = true })
        }

        Spacer(Modifier.height(10.dp))
        SageButton(T.t("Login", "प्रवेश"), onClick = onLoginClick)
        Spacer(Modifier.height(10.dp))
        SageButton(
            T.t("Register new parent", "नया अभिभावक खाता बनाएँ"),
            onClick        = onRegisterClick,
            containerColor = Blue700
        )
        Spacer(Modifier.height(10.dp))
        SageOutlineButton(T.t("Back", "वापस"), onClick = onBack)
    }

    if (showHelp) {
        HelpInfoDialog(
            title  = T.t("Parent login help", "अभिभावक प्रवेश मदद"),
            points = listOf(
                T.t(
                    "If you already created a parent account, tap Login and enter the same email and password.",
                    "अगर आपने अभिभावक खाता बना लिया है, तो प्रवेश दबाकर वही ईमेल और पासवर्ड डालें।"
                ),
                T.t(
                    "If you are new, tap Register new parent. A short Parent ID like AB-123 will be created.",
                    "अगर आप नए हैं, तो नया अभिभावक खाता बनाएँ दबाएँ। AB-123 जैसी छोटी अभिभावक ID बनेगी।"
                ),
                T.t(
                    "Give this Parent ID to the child. The child must enter it from the child side to connect.",
                    "यह अभिभावक ID बच्चे को दें। बच्चा अपने भाग में यही ID डालकर जुड़ेगा।"
                ),
                T.t(
                    "Do not register again with the same email. Use Login after registration.",
                    "एक ही ईमेल से बार-बार खाता न बनाएँ। खाता बनने के बाद प्रवेश करें।"
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
fun ParentLoginScreen(onLoginSuccess: (String) -> Unit, onBack: () -> Unit) {
    val auth    = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    AuthScaffold(
        title    = T.t("Parent login", "अभिभावक प्रवेश"),
        subtitle = T.t("Sign in with your registered email.", "अपने पंजीकृत ईमेल से प्रवेश करें।")
    ) {
        SageTextField(value = email,    onValueChange = { email    = it.trim() }, label = T.t("Email", "ईमेल"))
        Spacer(Modifier.height(8.dp))
        SageTextField(value = password, onValueChange = { password = it       }, label = T.t("Password", "पासवर्ड"))
        Spacer(Modifier.height(16.dp))

        SageButton(
            text    = if (loading) T.t("Logging in…", "प्रवेश हो रहा है…") else T.t("Login", "प्रवेश"),
            enabled = !loading,
            onClick = {
                if (email.isBlank() || !email.contains("@")) {
                    Toast.makeText(context, T.t("Enter a valid email", "सही ईमेल दर्ज करें"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                if (password.length < 6) {
                    Toast.makeText(context, T.t("Password must be at least 6 characters", "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                loading = true
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: ""
                        if (uid.isBlank()) { loading = false; return@addOnSuccessListener }
                        FirebaseDatabase.getInstance().reference
                            .child("parents").child(uid).child("parentId").get()
                            .addOnSuccessListener { snap ->
                                loading = false
                                val pid = snap.getValue(String::class.java) ?: ""
                                if (pid.isNotBlank()) onLoginSuccess(pid)
                                else Toast.makeText(context, T.t("Parent ID not found", "अभिभावक ID नहीं मिली"), Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { loading = false; Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                    .addOnFailureListener {
                        loading = false
                        Toast.makeText(context, T.t("Account not found. Please register first.", "खाता नहीं मिला। पहले नया खाता बनाएँ।"), Toast.LENGTH_LONG).show()
                    }
            }
        )
        Spacer(Modifier.height(8.dp))
        SageOutlineButton(T.t("Login help", "प्रवेश मदद"), enabled = !loading, onClick = { showHelp = true })
        Spacer(Modifier.height(8.dp))
        SageOutlineButton(T.t("Back", "वापस"), enabled = !loading, onClick = onBack)
    }

    if (showHelp) {
        HelpInfoDialog(
            title  = T.t("How to login", "प्रवेश कैसे करें"),
            points = listOf(
                T.t(
                    "Use the email and password you entered during parent registration.",
                    "अभिभावक खाता बनाते समय जो ईमेल और पासवर्ड डाला था वही इस्तेमाल करें।"
                ),
                T.t(
                    "If it says account not found, go back and register first.",
                    "अगर खाता नहीं मिला दिखे, तो वापस जाकर पहले नया खाता बनाएँ।"
                ),
                T.t(
                    "After login, your Parent ID dashboard will open automatically.",
                    "प्रवेश के बाद आपकी अभिभावक ID अपने आप खुल जाएगी।"
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
fun ParentRegisterScreen(onRegisterSuccess: (String) -> Unit, onBack: () -> Unit) {
    val auth     = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference
    val context  = LocalContext.current
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    AuthScaffold(
        title    = T.t("Register parent", "अभिभावक खाता बनाएँ"),
        subtitle = T.t("A short Parent ID like AB-123 will be created.", "AB-123 जैसी छोटी अभिभावक ID बनाई जाएगी।")
    ) {
        SageTextField(value = email,    onValueChange = { email    = it.trim() }, label = T.t("New email", "नया ईमेल"))
        Spacer(Modifier.height(8.dp))
        SageTextField(value = password, onValueChange = { password = it       }, label = T.t("Password (min 6 characters)", "पासवर्ड (कम से कम 6 अक्षर)"))
        Spacer(Modifier.height(16.dp))

        SageButton(
            text    = if (loading) T.t("Registering…", "खाता बन रहा है…") else T.t("Register", "खाता बनाएँ"),
            enabled = !loading,
            onClick = {
                if (email.isBlank() || !email.contains("@")) {
                    Toast.makeText(context, T.t("Enter a valid email", "सही ईमेल दर्ज करें"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                if (password.length < 6) {
                    Toast.makeText(context, T.t("Password must be at least 6 characters", "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                loading = true
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: ""
                        if (uid.isBlank()) { loading = false; return@addOnSuccessListener }
                        createUniqueParentCode { smallId ->
                            val data = mapOf(
                                "email"     to email,
                                "uid"       to uid,
                                "parentId"  to smallId,
                                "createdAt" to System.currentTimeMillis()
                            )
                            database.child("parents").child(uid).setValue(data)
                                .addOnSuccessListener {
                                    database.child("parentCodes").child(smallId).setValue(uid)
                                        .addOnSuccessListener {
                                            loading = false
                                            Toast.makeText(
                                                context,
                                                T.t("Registered! Your ID: $smallId", "खाता बन गया! आपकी ID: $smallId"),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onRegisterSuccess(smallId)
                                        }
                                        .addOnFailureListener { loading = false; Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                                }
                                .addOnFailureListener { loading = false; Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        Toast.makeText(context, T.t("Email already used. Please login instead.", "यह ईमेल पहले से इस्तेमाल हो चुका है। प्रवेश करें।"), Toast.LENGTH_LONG).show()
                    }
            }
        )
        Spacer(Modifier.height(8.dp))
        SageOutlineButton(T.t("Registration help", "खाता बनाने में मदद"), enabled = !loading, onClick = { showHelp = true })
        Spacer(Modifier.height(8.dp))
        SageOutlineButton(T.t("Back", "वापस"), enabled = !loading, onClick = onBack)
    }

    if (showHelp) {
        HelpInfoDialog(
            title  = T.t("How to register", "खाता कैसे बनाएँ"),
            points = listOf(
                T.t(
                    "Enter a new parent email and a password of at least 6 characters.",
                    "नया अभिभावक ईमेल डालें और कम से कम 6 अक्षरों का पासवर्ड रखें।"
                ),
                T.t(
                    "After registration, the app creates a short Parent ID like AB-123.",
                    "खाता बनने के बाद ऐप AB-123 जैसी छोटी अभिभावक ID बनाएगा।"
                ),
                T.t(
                    "Share this Parent ID with the child. Do not register again with the same email.",
                    "यह अभिभावक ID बच्चे के साथ साझा करें। उसी ईमेल से दोबारा खाता न बनाएँ।"
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
fun ChildSetupScreen(onConnect: (String, String, String) -> Unit, onBack: () -> Unit) {
    val context  = LocalContext.current
    val database = FirebaseDatabase.getInstance().reference
    var parentId  by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var loading   by remember { mutableStateOf(false) }

    AuthScaffold(
        title    = T.t("Connect to parent", "अभिभावक से जोड़ें"),
        subtitle = T.t("Enter the Parent ID shared by your parent.", "अभिभावक द्वारा दी गई Parent ID और अपना नाम दर्ज करें।")
    ) {
        SageTextField(
            value         = parentId,
            onValueChange = { parentId = it.trim().uppercase() },
            label         = T.t("Parent ID (e.g. AB-123)", "अभिभावक ID (जैसे AB-123)")
        )
        Spacer(Modifier.height(8.dp))
        SageTextField(
            value         = childName,
            onValueChange = { childName = it },
            label         = T.t("Child name (e.g. Ankit)", "बच्चे का नाम (जैसे Ankit)")
        )
        Spacer(Modifier.height(16.dp))

        SageButton(
            text    = if (loading) T.t("Checking…", "जाँच हो रही है…") else T.t("Connect", "जोड़ें"),
            enabled = !loading,
            onClick = {
                val fPid  = parentId.trim().uppercase()
                val fName = childName.trim()
                if (!isValidSmallParentId(fPid)) {
                    Toast.makeText(context, T.t("Invalid ID. Example: AB-123", "गलत ID। उदाहरण: AB-123"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                if (fName.length < 2) {
                    Toast.makeText(context, T.t("Enter child name (min 2 characters)", "बच्चे का नाम दर्ज करें"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                val childId = makeChildId(fName)
                loading = true
                database.child("parentCodes").child(fPid).get()
                    .addOnSuccessListener { snap ->
                        if (snap.exists()) {
                            val d = mapOf(
                                "childId"     to childId,
                                "childName"   to fName,
                                "connectedAt" to System.currentTimeMillis()
                            )
                            database.child("children").child(fPid).child(childId).setValue(d)
                                .addOnSuccessListener {
                                    loading = false
                                    Toast.makeText(context, T.t("Connected successfully!", "सफलतापूर्वक जुड़ गया!"), Toast.LENGTH_SHORT).show()
                                    onConnect(fPid, childId, fName)
                                }
                                .addOnFailureListener { loading = false; Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        } else {
                            loading = false
                            Toast.makeText(context, T.t("Parent ID not found. Check and try again.", "अभिभावक ID नहीं मिली। जाँचकर फिर कोशिश करें।"), Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { loading = false; Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
        )
        Spacer(Modifier.height(8.dp))
        SageOutlineButton(T.t("Back", "वापस"), enabled = !loading, onClick = onBack)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CHILD TAB SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun ChildTabScreen(
    usageList   : List<AppUsage>,
    parentId    : String,
    childId     : String,
    childName   : String,
    onFetchUsage: () -> Unit,
    autoSync    : () -> Unit,
    onBack      : () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs     = listOf(T.t("Dashboard", "डैशबोर्ड"), T.t("Apps", "ऐप्स"), T.t("Health", "स्वास्थ्य"), T.t("Rewards", "रिवार्ड्स"))
    val tabIcons = listOf("◈", "◉", "♡", "★")

    // Auto-sync every 30 seconds
    LaunchedEffect(parentId, childId) {
        while (true) {
            autoSync()
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxSize().background(Surface2)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Teal700)
                .padding(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 0.dp)
        ) {
            Text("ScreenSage", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("$childName · ${T.t("Child", "बच्चा")}", fontSize = 12.sp, color = Color.White.copy(0.8f))
            Spacer(Modifier.height(12.dp))
            SlidingTabBar(tabs, tabIcons, selectedTab) { selectedTab = it }
        }

        when (selectedTab) {
            0 -> ChildDashboardTab(usageList, childName, parentId, onBack)
            1 -> ChildAppsTab(usageList, onFetchUsage)
            2 -> HealthImpactTab(usageList)
            3 -> BrowniePointTab(parentId, childId, childName, usageList)
        }
    }
}

@Composable
fun ChildDashboardTab(
    usageList : List<AppUsage>,
    childName : String,
    parentId  : String,
    onBack    : () -> Unit
) {
    val totalMinutes = usageList.sumOf { it.minutes }
    val exceeded     = usageList.filter { it.minutes > it.limitMinutes }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SageCard(color = Teal700) {
            Text(T.t("Today's screen time", "आज का स्क्रीन समय"), fontSize = 12.sp, color = Color.White.copy(0.8f))
            Spacer(Modifier.height(2.dp))
            Text(T.hm(totalMinutes), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "${usageList.size} ${T.t("apps monitored · syncing every 30s", "ऐप्स देखे जा रहे हैं · हर 30 सेकंड में डेटा भेजा जाता है")}",
                fontSize = 12.sp,
                color    = Color.White.copy(0.75f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(T.t("Apps used", "इस्तेमाल हुए ऐप्स"), "${usageList.size}", modifier = Modifier.weight(1f))
            StatBox(
                T.t("Limits hit", "सीमा पार"),
                "${exceeded.size}",
                valueColor = if (exceeded.isNotEmpty()) Red700 else Green700,
                modifier   = Modifier.weight(1f)
            )
        }

        if (usageList.isNotEmpty()) DailyUsagePieChart(usageList)

        if (exceeded.isNotEmpty()) {
            SageCard {
                Text(T.t("Alerts", "चेतावनी"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                Spacer(Modifier.height(8.dp))
                exceeded.forEach { app ->
                    AlertRow(
                        icon     = "!",
                        iconBg   = Red50,
                        title    = "${T.appName(app.appName)} ${T.t("limit exceeded", "सीमा पार")}",
                        subtitle = "${T.t("Used", "इस्तेमाल")} ${T.min(app.minutes)} · ${T.t("Limit", "सीमा")} ${T.min(app.limitMinutes)}"
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        SageOutlineButton(T.t("Back to home", "मुख्य पेज पर वापस"), onClick = onBack)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ChildAppsTab(usageList: List<AppUsage>, onFetchUsage: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SageCard(color = Blue700) {
            Text(T.t("App usage monitor", "ऐप उपयोग निगरानी"), fontSize = 12.sp, color = Color.White.copy(0.8f))
            Text(
                "${usageList.size} ${T.t("apps", "ऐप्स")} · ${T.min(usageList.sumOf { it.minutes })} ${T.t("total", "कुल")}",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }

        if (usageList.isEmpty()) {
            SageCard {
                Text(T.t("No usage data yet", "अभी उपयोग डेटा नहीं है"), fontWeight = FontWeight.SemiBold, color = Gray900)
                Text(
                    T.t("Data syncs automatically every 30 seconds.", "डेटा हर 30 सेकंड में अपने आप भेजा जाता है।"),
                    fontSize = 13.sp,
                    color    = Gray600
                )
                Spacer(Modifier.height(10.dp))
                SageButton(T.t("Refresh now  🔄", "अभी ताज़ा करें  🔄"), onClick = onFetchUsage)
            }
        } else {
            SageCard {
                Text(T.t("Usage breakdown", "उपयोग विवरण"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                Spacer(Modifier.height(8.dp))
                usageList.forEach { app ->
                    AppUsageRow(app)
                    Spacer(Modifier.height(4.dp))
                }
            }
            SageButton(T.t("Refresh now  🔄", "अभी ताज़ा करें  🔄"), onClick = onFetchUsage)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PARENT TAB SCREEN
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun ParentTabScreen(parentId: String, onBack: () -> Unit) {
    var selectedTab     by remember { mutableStateOf(0) }
    var usageList       by remember { mutableStateOf(listOf<AppUsage>()) }
    var childrenList    by remember { mutableStateOf(listOf<ChildInfo>()) }
    var selectedChild   by remember { mutableStateOf<ChildInfo?>(null) }
    val context = LocalContext.current

    val tabs     = listOf(T.t("Dashboard", "डैशबोर्ड"), T.t("Apps", "ऐप्स"), T.t("Health", "स्वास्थ्य"), T.t("Weekly", "साप्ताहिक"), T.t("Settings", "सेटिंग्स"))
    val tabIcons = listOf("◈", "◉", "♡", "◫", "⊕")

    LaunchedEffect(parentId) {
        readChildrenList(parentId) { children ->
            childrenList = children
            if (selectedChild == null && children.isNotEmpty()) selectedChild = children.first()
        }
    }
    LaunchedEffect(parentId, selectedChild?.childId) {
        val cId = selectedChild?.childId ?: ""
        if (cId.isNotBlank()) readFromFirebase(parentId, cId) { usageList = it }
        else usageList = emptyList()
    }
    LaunchedEffect(usageList, selectedChild?.childId) {
        val child = selectedChild
        if (child != null) {
            notifyExceededApps(
                context       = context,
                parentId      = parentId,
                childId       = child.childId,
                childName     = child.childName,
                usageList     = usageList,
                isParentPhone = true
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Surface2)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Teal700)
                .padding(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 0.dp)
        ) {
            Text("ScreenSage", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${T.t("Parent", "अभिभावक")} · $parentId", fontSize = 12.sp, color = Color.White.copy(0.8f))
            Spacer(Modifier.height(12.dp))
            SlidingTabBar(tabs, tabIcons, selectedTab) { selectedTab = it }
        }

        when (selectedTab) {
            0 -> ParentDashboardTab(
                parentId, childrenList, selectedChild, usageList,
                onSelectChild = { selectedChild = it; usageList = emptyList() }
            )
            1 -> ParentAppsTab(parentId, selectedChild, usageList)
            2 -> HealthImpactTab(usageList)
            3 -> WeeklyReportTab(parentId, selectedChild?.childId ?: "")
            4 -> ParentSettingsTab(parentId, onBack)
        }
    }
}

@Composable
fun ParentDashboardTab(
    parentId     : String,
    childrenList : List<ChildInfo>,
    selectedChild: ChildInfo?,
    usageList    : List<AppUsage>,
    onSelectChild: (ChildInfo) -> Unit
) {
    val totalMinutes = usageList.sumOf { it.minutes }
    val exceeded     = usageList.filter { it.minutes > it.limitMinutes }
    var showHelp     by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Help banner
        SageCard(color = Blue50) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(T.t("Parent dashboard help", "अभिभावक डैशबोर्ड मदद"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Blue700)
                    Text(T.t("Understand daily usage, app limits, health and weekly report.", "आज का उपयोग, ऐप सीमा, स्वास्थ्य और साप्ताहिक रिपोर्ट समझें।"), fontSize = 12.sp, color = Gray600)
                }
                Text(
                    T.t("Help", "मदद"),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Blue700,
                    modifier   = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .clickable { showHelp = true }
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Parent ID card
        SageCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(T.t("Your Parent ID", "आपकी अभिभावक ID"), fontSize = 11.sp, color = Teal700.copy(0.8f))
                    Text(parentId, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Teal700, letterSpacing = 2.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Teal50)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(T.t("Share", "शेयर"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Teal700)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(T.t("Share this ID with each child to connect.", "जोड़ने के लिए यह ID बच्चे के साथ साझा करें।"), fontSize = 12.sp, color = Gray600)
        }

        // Child selector
        Text(T.t("Children", "बच्चे"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
        if (childrenList.isEmpty()) {
            SageCard {
                Text(T.t("No child connected yet", "अभी कोई बच्चा जुड़ा नहीं है"), fontWeight = FontWeight.SemiBold, color = Gray900)
                Text(
                    T.t("Open the child side, enter your Parent ID, and add a child name.", "बच्चे वाले भाग में जाएँ, अभिभावक ID डालें और बच्चे का नाम जोड़ें।"),
                    fontSize = 12.sp,
                    color    = Gray600
                )
            }
        } else {
            childrenList.forEach { child ->
                ChildChip(child, selected = selectedChild?.childId == child.childId) { onSelectChild(child) }
            }
        }

        // Summary hero
        SageCard(color = if (exceeded.isNotEmpty()) Amber700 else Teal700) {
            Text(
                "${selectedChild?.childName ?: T.t("No child selected", "कोई बच्चा चुना नहीं गया")} · ${T.t("today", "आज")}",
                fontSize = 12.sp,
                color    = Color.White.copy(0.8f)
            )
            Text(T.hm(totalMinutes), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "${usageList.size} ${T.t("apps", "ऐप्स")} · ${exceeded.size} ${T.t("limits exceeded", "सीमाएँ पार")}",
                fontSize = 12.sp,
                color    = Color.White.copy(0.75f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(T.t("Apps", "ऐप्स"), "${usageList.size}", modifier = Modifier.weight(1f))
            StatBox(
                T.t("Over limit", "सीमा से ज्यादा"),
                "${exceeded.size}",
                valueColor = if (exceeded.isNotEmpty()) Red700 else Green700,
                modifier   = Modifier.weight(1f)
            )
        }

        if (usageList.isNotEmpty()) DailyUsagePieChart(usageList)

        if (exceeded.isNotEmpty()) {
            SageCard {
                Text(T.t("Alerts", "चेतावनी"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                Spacer(Modifier.height(8.dp))
                exceeded.take(3).forEach { app ->
                    AlertRow(
                        icon     = "!",
                        iconBg   = Red50,
                        title    = "${T.appName(app.appName)} ${T.t("exceeded limit", "सीमा पार")}",
                        subtitle = "${T.t("Used", "इस्तेमाल")} ${T.min(app.minutes)} · ${T.t("Limit", "सीमा")} ${T.min(app.limitMinutes)}"
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showHelp) {
        HelpInfoDialog(
            title  = T.t("Parent dashboard features", "अभिभावक डैशबोर्ड की सुविधाएँ"),
            points = listOf(
                T.t(
                    "Dashboard shows total daily screen time, connected children and a daily app pie chart.",
                    "डैशबोर्ड में आज का स्क्रीन समय, जुड़े बच्चे और ऐप्स का गोल चार्ट दिखता है।"
                ),
                T.t(
                    "Apps tab lets you set custom time limits for each app.",
                    "ऐप्स भाग में आप हर ऐप के लिए अलग समय सीमा रख सकते हैं।"
                ),
                T.t(
                    "Health tab explains the mental and physical impact of screen time.",
                    "स्वास्थ्य भाग स्क्रीन समय के मानसिक और शारीरिक असर समझाता है।"
                ),
                T.t(
                    "Weekly tab compares this week with last week and lets you award brownie points.",
                    "साप्ताहिक भाग इस सप्ताह की पिछले सप्ताह से तुलना करता है और ब्राउनी अंक देता है।"
                ),
                T.t(
                    "Settings tab contains account details and the logout option.",
                    "सेटिंग्स भाग में खाते की जानकारी और बाहर निकलने का विकल्प है।"
                )
            ),
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
fun ParentAppsTab(parentId: String, selectedChild: ChildInfo?, usageList: List<AppUsage>) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedChild == null) {
            SageCard {
                Text(T.t("Select a child from Dashboard first.", "पहले डैशबोर्ड से बच्चा चुनें।"), color = Gray600, fontSize = 13.sp)
            }
            return@Column
        }

        SageCard(color = Blue700) {
            Text(
                "${selectedChild.childName} · ${T.t("App limits", "ऐप सीमा")}",
                fontSize = 12.sp,
                color    = Color.White.copy(0.8f)
            )
            Text(
                "${usageList.size} ${T.t("apps tracked", "ऐप्स देखे जा रहे हैं")}",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }

        usageList.forEach { app ->
            var customLimit by remember(selectedChild.childId, app.packageName) {
                mutableStateOf(app.limitMinutes.toString())
            }
            val isExceeded = app.minutes > app.limitMinutes
            val pct        = if (app.limitMinutes > 0) (app.minutes.toFloat() / app.limitMinutes).coerceAtMost(1f) else 0f

            SageCard(color = if (isExceeded) Red50 else Surface) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(T.appName(app.appName), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                    StatusBadge(app.minutes, app.limitMinutes)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${T.t("Used", "इस्तेमाल")}: ${T.min(app.minutes)}  ·  ${T.t("Limit", "सीमा")}: ${T.min(app.limitMinutes)}",
                    fontSize = 12.sp,
                    color    = Gray600
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress  = pct,
                    modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color     = when { isExceeded -> Red700; pct >= 0.8f -> Amber400; else -> Green400 },
                    trackColor = Gray50
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = customLimit,
                    onValueChange = { customLimit = it },
                    label         = { Text(T.t("New limit (minutes)", "नई सीमा (मिनट)"), fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    singleLine    = true
                )
                Spacer(Modifier.height(8.dp))
                SageButton(T.t("Save limit", "सीमा सेव करें"), onClick = {
                    val lim = customLimit.toLongOrNull()
                    if (lim != null && lim > 0)
                        updateLimitInFirebase(parentId, selectedChild.childId, app.packageName, lim)
                    else
                        Toast.makeText(context, T.t("Enter a valid number", "सही संख्या दर्ज करें"), Toast.LENGTH_SHORT).show()
                })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ParentSettingsTab(parentId: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SageCard {
            Text(T.t("Account", "अकाउंट"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(T.t("Parent ID", "अभिभावक ID"), fontSize = 13.sp, color = Gray600)
                Text(parentId, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Teal700)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(T.t("Email", "ईमेल"), fontSize = 13.sp, color = Gray600)
                Text(FirebaseAuth.getInstance().currentUser?.email ?: "—", fontSize = 13.sp, color = Gray900)
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Red50)
        ) {
            Text(T.t("Logout", "बाहर निकलें"), color = Red700, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HEALTH TAB
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HealthImpactTab(usageList: List<AppUsage>) {
    val totalMinutes = usageList.sumOf { it.minutes }
    val hours        = totalMinutes / 60

    val healthData = when {
        totalMinutes < 120 -> HealthData(
            label       = T.t("Low risk", "कम जोखिम"),
            color       = Green700,
            mental      = listOf(
                T.t("Normal screen use if balanced with study and sleep.", "पढ़ाई और नींद के साथ संतुलन रहे तो स्क्रीन उपयोग सामान्य है।"),
                T.t("Low chance of focus disturbance.", "ध्यान भटकने की संभावना कम है।")
            ),
            physical    = listOf(
                T.t("Low eye strain risk.", "आँखों पर दबाव कम है।"),
                T.t("Low sedentary impact.", "लंबे समय तक बैठे रहने का असर कम है।")
            ),
            suggestions = listOf(
                T.t("Take a 5-min break after long sessions.", "लंबे उपयोग के बाद 5 मिनट का ब्रेक लें।"),
                T.t("Avoid screens before sleep.", "सोने से पहले स्क्रीन से बचें।")
            )
        )
        totalMinutes < 240 -> HealthData(
            label       = T.t("Moderate risk", "मध्यम जोखिम"),
            color       = Amber700,
            mental      = listOf(
                T.t("Focus may reduce from frequent app switching.", "बार-बार ऐप बदलने से ध्यान कम हो सकता है।"),
                T.t("Sleep quality may reduce with late-night use.", "रात में उपयोग से नींद की गुणवत्ता घट सकती है।")
            ),
            physical    = listOf(
                T.t("Possible eye strain or headache.", "आँखों में थकान या सिरदर्द हो सकता है।"),
                T.t("Less physical movement during the day.", "दिन में शारीरिक गतिविधि कम हो सकती है।")
            ),
            suggestions = listOf(
                T.t("Use the 20-20-20 rule for eyes.", "आँखों के लिए 20-20-20 नियम अपनाएँ।"),
                T.t("Set limits for social media apps.", "सोशल मीडिया ऐप्स की सीमा तय करें।"),
                T.t("Keep your phone away 1 hour before sleep.", "सोने से 1 घंटा पहले फोन दूर रखें।")
            )
        )
        totalMinutes < 360 -> HealthData(
            label       = T.t("High risk", "ज्यादा जोखिम"),
            color       = Color(0xFF993C1D),
            mental      = listOf(
                T.t("Higher distraction and reduced productivity.", "ध्यान ज्यादा भटक सकता है और उत्पादकता घट सकती है।"),
                T.t("May affect mood and sleep routine.", "मूड और नींद की दिनचर्या प्रभावित हो सकती है।")
            ),
            physical    = listOf(
                T.t("Eye strain or headache may increase.", "आँखों की थकान या सिरदर्द बढ़ सकता है।"),
                T.t("Long sitting reduces physical fitness.", "लंबे समय तक बैठना फिटनेस घटा सकता है।")
            ),
            suggestions = listOf(
                T.t("Take breaks every 30–45 minutes.", "हर 30–45 मिनट में ब्रेक लें।"),
                T.t("Use focus mode during study.", "पढ़ाई के समय फोकस मोड इस्तेमाल करें।"),
                T.t("Add outdoor physical activity.", "बाहर की शारीरिक गतिविधि जोड़ें।")
            )
        )
        else -> HealthData(
            label       = T.t("Very high risk", "बहुत ज्यादा जोखिम"),
            color       = Red700,
            mental      = listOf(
                T.t("Strong chance of poor focus and digital fatigue.", "ध्यान कम होने और डिजिटल थकान की संभावना ज्यादा है।"),
                T.t("Sleep disturbance risk is high.", "नींद खराब होने का जोखिम ज्यादा है।"),
                T.t("May increase stress or irritability.", "तनाव या चिड़चिड़ापन बढ़ सकता है।")
            ),
            physical    = listOf(
                T.t("High risk of eye strain and headache.", "आँखों की थकान और सिरदर्द का जोखिम ज्यादा है।"),
                T.t("Sedentary behavior may affect weight and posture.", "ज्यादा बैठे रहने से वजन और बैठने का ढंग प्रभावित हो सकता है।"),
                T.t("Neck and back discomfort likely.", "गर्दन और पीठ में परेशानी हो सकती है।")
            ),
            suggestions = listOf(
                T.t("Reduce entertainment screen time immediately.", "मनोरंजन वाला स्क्रीन समय तुरंत कम करें।"),
                T.t("Set strict app limits.", "ऐप्स पर सख्त सीमा लगाएँ।"),
                T.t("Avoid screens before bed.", "सोने से पहले स्क्रीन से बचें।"),
                T.t("Take breaks, do stretching and outdoor activity.", "ब्रेक लें, स्ट्रेचिंग करें और बाहर जाएँ।")
            )
        )
    }

    val riskIndex = when {
        totalMinutes < 120 -> 0
        totalMinutes < 240 -> 1
        totalMinutes < 360 -> 2
        else               -> 3
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SageCard(color = healthData.color) {
            Text(T.t("Today's screen time", "आज का स्क्रीन समय"), fontSize = 12.sp, color = Color.White.copy(0.8f))
            Text("${hours}h ${totalMinutes % 60}m", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${T.t("Risk level", "जोखिम स्तर")}: ${healthData.label}", fontSize = 13.sp, color = Color.White.copy(0.9f))
        }

        SageCard {
            Text(T.t("Risk meter", "जोखिम माप"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Green400, Amber400, Color(0xFFD85A30), Red700).forEachIndexed { i, col ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(col.copy(alpha = if (i <= riskIndex) 1f else 0.25f))
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(T.t("Low", "कम"),           fontSize = 10.sp, color = Green700)
                Text(T.t("Very high", "बहुत ज्यादा"), fontSize = 10.sp, color = Red700)
            }
        }

        HealthSection(T.t("Mental health impact", "मानसिक स्वास्थ्य असर"), healthData.mental,      Teal700)
        HealthSection(T.t("Physical health impact", "शारीरिक स्वास्थ्य असर"), healthData.physical, Amber700)
        HealthSection(T.t("Recommendations", "सुझाव"),                           healthData.suggestions, Blue700)
        Spacer(Modifier.height(8.dp))
    }
}

data class HealthData(
    val label      : String,
    val color      : Color,
    val mental     : List<String>,
    val physical   : List<String>,
    val suggestions: List<String>
)

@Composable
fun HealthSection(title: String, items: List<String>, dotColor: Color) {
    SageCard {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor).padding(top = 4.dp))
                Spacer(Modifier.width(10.dp))
                Text(item, fontSize = 13.sp, color = Gray600, lineHeight = 18.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WEEKLY REPORT TAB
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun WeeklyReportTab(parentId: String, childId: String) {
    var currentWeek  by remember { mutableStateOf(listOf<DailyUsageReport>()) }
    var previousWeek by remember { mutableStateOf(listOf<DailyUsageReport>()) }
    var weeklyTopApps by remember { mutableStateOf(listOf<AppUsage>()) }
    var limitViolations by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(parentId, childId) {
        if (parentId.isNotBlank() && childId.isNotBlank()) {
            readWeeklyReportData(parentId, childId) { cur, prev, top, viol ->
                currentWeek     = cur
                previousWeek    = prev
                weeklyTopApps   = top
                limitViolations = viol
            }
        }
    }

    val currentTotal  = currentWeek.sumOf { it.totalMinutes }
    val previousTotal = previousWeek.sumOf { it.totalMinutes }
    val averageDaily  = if (currentWeek.isNotEmpty()) currentTotal / 7 else 0L
    val trendPct      = calculateTrendPercent(currentTotal, previousTotal)
    val screenScore   = calculateScreenScore(averageDaily, limitViolations)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (childId.isBlank()) {
            SageCard { Text(T.t("Select a child from Dashboard first.", "पहले डैशबोर्ड से बच्चा चुनें।"), color = Gray600, fontSize = 13.sp) }
            return@Column
        }

        SageCard(color = Teal700) {
            Text(T.t("This week's total", "इस सप्ताह का कुल समय"), fontSize = 12.sp, color = Color.White.copy(0.8f))
            Text("${currentTotal / 60}h ${currentTotal % 60}m", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "${T.t("Avg", "औसत")}: $averageDaily ${T.t("min/day", "मिनट/दिन")}  ·  ${T.t("Score", "स्कोर")}: $screenScore/100",
                fontSize = 12.sp,
                color    = Color.White.copy(0.75f)
            )
        }

        val trendColor = if (trendPct > 0) Amber700 else if (trendPct < 0) Teal700 else Gray600
        SageCard {
            Text(T.t("Trend vs last week", "पिछले सप्ताह से तुलना"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(T.t("Last week", "पिछला सप्ताह"), fontSize = 11.sp, color = Gray600)
                    Text(T.min(previousTotal), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                }
                Text(if (trendPct > 0) "▲" else if (trendPct < 0) "▼" else "—", fontSize = 22.sp, color = trendColor)
                Column(horizontalAlignment = Alignment.End) {
                    Text(T.t("This week", "यह सप्ताह"), fontSize = 11.sp, color = Gray600)
                    Text(T.min(currentTotal), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = trendColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (trendPct > 0) Amber50 else Teal50)
                    .padding(10.dp)
            ) {
                val msg = when {
                    previousTotal == 0L && currentTotal > 0L -> T.t("First weekly data collected", "पहला साप्ताहिक डेटा मिल गया")
                    trendPct > 0 -> T.t("Screen time increased by $trendPct% this week", "इस सप्ताह स्क्रीन समय $trendPct% बढ़ा")
                    trendPct < 0 -> T.t("Screen time reduced by ${kotlin.math.abs(trendPct)}% this week", "इस सप्ताह स्क्रीन समय ${kotlin.math.abs(trendPct)}% कम हुआ")
                    else -> T.t("Screen time is the same as last week", "स्क्रीन समय पिछले सप्ताह जैसा ही है")
                }
                Text(msg, fontSize = 12.sp, color = trendColor)
            }
        }

        SageCard {
            Text(T.t("Daily usage (minutes)", "दिन के हिसाब से उपयोग (मिनट)"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            AndroidView(
                factory = { ctx ->
                    BarChart(ctx).apply {
                        description.isEnabled = false
                        axisRight.isEnabled   = false
                        xAxis.setDrawLabels(false)
                        legend.isEnabled      = true
                    }
                },
                update  = { chart ->
                    val entries = currentWeek.mapIndexed { i, d -> BarEntry(i.toFloat(), d.totalMinutes.toFloat()) }
                    val ds = BarDataSet(entries, T.t("This week (minutes)", "यह सप्ताह (मिनट)")).also {
                        it.color         = AndroidColor.rgb(15, 110, 86)
                        it.valueTextSize = 11f
                    }
                    chart.data = BarData(ds)
                    chart.invalidate()
                    chart.animateY(800)
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(Modifier.height(6.dp))
            currentWeek.forEach {
                Text("${it.label}: ${T.min(it.totalMinutes)}", fontSize = 12.sp, color = Gray600)
            }
        }

        SageCard {
            Text(T.t("Top apps this week", "इस सप्ताह के मुख्य ऐप्स"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(6.dp))
            if (weeklyTopApps.isEmpty()) {
                Text(
                    T.t("No weekly app data yet. Refresh from child's phone.", "अभी साप्ताहिक ऐप डेटा नहीं है। बच्चे के फोन से डेटा ताज़ा करें।"),
                    fontSize = 12.sp,
                    color    = Gray600
                )
            } else {
                weeklyTopApps.take(5).forEachIndexed { i, app ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${T.appName(app.appName)}", fontSize = 13.sp, color = Gray900)
                        Text(T.min(app.minutes),                      fontSize = 13.sp, color = Gray600)
                    }
                }
            }
        }

        val rewardPts = calculateRewardPoints(screenScore)
        SageCard {
            Text(T.t("Weekly brownie reward ★", "साप्ताहिक ब्राउनी इनाम ★"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(4.dp))
            Text(
                T.t(
                    "Based on screen score $screenScore/100 → $rewardPts points available.",
                    "स्क्रीन स्कोर $screenScore/100 के आधार पर $rewardPts अंक मिल सकते हैं।"
                ),
                fontSize = 12.sp,
                color    = Gray600
            )
            Spacer(Modifier.height(10.dp))
            SageButton(
                text           = T.t("Claim $rewardPts brownie points", "$rewardPts ब्राउनी अंक लें"),
                containerColor = Amber700,
                onClick        = {
                    claimWeeklyReward(parentId, childId, screenScore) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BROWNIE POINTS TAB
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun BrowniePointTab(
    parentId : String,
    childId  : String,
    childName: String,
    usageList: List<AppUsage>
) {
    val context = LocalContext.current
    var rewardInfo       by remember { mutableStateOf(RewardInfo()) }
    var selectedPackage  by remember { mutableStateOf("") }
    var selectedAppName  by remember { mutableStateOf("") }
    var selectedMinutes  by remember { mutableStateOf(5L) }

    LaunchedEffect(parentId, childId) {
        readRewardInfo(parentId, childId) { rewardInfo = it }
    }

    val todayUsed      = if (rewardInfo.lastExtraDate == getDateKey(0)) rewardInfo.todayExtraUsed else 0L
    val remainingExtra = (30L - todayUsed).coerceAtLeast(0L)
    val redeemOptions  = listOf(5L to 10L, 10L to 20L, 20L to 40L)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Points hero
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Teal700)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(T.t("Available brownie points", "उपलब्ध ब्राउनी अंक"), fontSize = 12.sp, color = Color.White.copy(0.8f))
                Text("${rewardInfo.browniePoints}", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PointStat("${rewardInfo.totalEarned}",   T.t("Earned", "कमाए"))
                    PointStat("${rewardInfo.totalRedeemed}", T.t("Used", "इस्तेमाल किए"))
                    PointStat("$remainingExtra ${T.t("min", "मिनट")}", T.t("left today", "आज बचे"))
                }
            }
        }

        SageCard {
            Text(T.t("How redemption works", "अंक इस्तेमाल कैसे करें"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Spacer(Modifier.height(6.dp))
            listOf(
                T.t("10 pts = 5 extra minutes",       "10 अंक = 5 अतिरिक्त मिनट"),
                T.t("20 pts = 10 extra minutes",      "20 अंक = 10 अतिरिक्त मिनट"),
                T.t("40 pts = 20 extra minutes",      "40 अंक = 20 अतिरिक्त मिनट"),
                T.t("Max extra time per day = 30 min","अधिकतम अतिरिक्त समय = 30 मिनट प्रति दिन")
            ).forEach {
                Text("· $it", fontSize = 13.sp, color = Gray600, modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        Text(T.t("Choose app to boost", "अतिरिक्त समय के लिए ऐप चुनें"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)

        if (usageList.isEmpty()) {
            SageCard {
                Text(
                    T.t("No usage data found. Data syncs automatically every 30 seconds.", "उपयोग डेटा नहीं मिला। डेटा हर 30 सेकंड में अपने आप भेजा जाता है।"),
                    fontSize = 13.sp,
                    color    = Gray600
                )
            }
        } else {
            usageList.take(8).forEach { app ->
                val sel = selectedPackage == app.packageName
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Teal50 else Surface)
                        .border(1.5.dp, if (sel) Teal700 else BorderColor, RoundedCornerShape(10.dp))
                        .clickable { selectedPackage = app.packageName; selectedAppName = app.appName }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Teal50), contentAlignment = Alignment.Center) {
                        Text(T.appName(app.appName).take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Teal700)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(T.appName(app.appName), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                        Text("${T.min(app.minutes)} ${T.t("used today", "आज इस्तेमाल")}", fontSize = 11.sp, color = Gray600)
                    }
                    if (sel) Text("✓", color = Teal700, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Text(T.t("Choose extra time", "अतिरिक्त समय चुनें"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
        redeemOptions.forEach { (mins, cost) ->
            val sel = selectedMinutes == mins
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Teal50 else Surface)
                    .border(1.5.dp, if (sel) Teal700 else BorderColor, RoundedCornerShape(10.dp))
                    .clickable { selectedMinutes = mins }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("+$mins ${T.t("minutes", "मिनट")}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                Text(T.t("$cost points", "$cost अंक"),   fontSize = 13.sp, color = Gray600)
            }
            Spacer(Modifier.height(4.dp))
        }

        SageButton(
            text    = T.t("Redeem for ${selectedAppName.ifBlank { "selected app" }}", "${selectedAppName.ifBlank { T.t("selected app", "चुना हुआ ऐप") }} के लिए भुनाएँ"),
            onClick = {
                if (selectedPackage.isBlank()) {
                    Toast.makeText(context, T.t("Please select an app first", "पहले ऐप चुनें"), Toast.LENGTH_SHORT).show()
                    return@SageButton
                }
                redeemBrowniePoints(parentId, childId, selectedPackage, selectedMinutes) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIE CHART
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DailyUsagePieChart(usageList: List<AppUsage>) {
    val topApps = usageList.sortedByDescending { it.minutes }.take(6)
    if (topApps.isEmpty()) return

    SageCard {
        Text(T.t("Today's app usage chart", "आज का ऐप उपयोग चार्ट"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
        Text(T.t("Colorblind-friendly pie chart of today's app usage.", "आज के ऐप उपयोग का रंग-अंधता अनुकूल गोल चार्ट।"), fontSize = 12.sp, color = Gray600)
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { ctx ->
                PieChart(ctx).apply {
                    description.isEnabled  = false
                    legend.isEnabled       = true
                    setUsePercentValues(false)
                    setDrawEntryLabels(false)
                    isRotationEnabled      = true
                    holeRadius             = 45f
                    transparentCircleRadius = 50f
                    setNoDataText(T.t("No usage data", "उपयोग डेटा नहीं है"))
                }
            },
            update  = { chart ->
                val entries = topApps.map { PieEntry(it.minutes.toFloat(), T.appName(it.appName)) }
                val palette = listOf(
                    AndroidColor.rgb(0, 114, 178),
                    AndroidColor.rgb(230, 159, 0),
                    AndroidColor.rgb(0, 158, 115),
                    AndroidColor.rgb(204, 121, 167),
                    AndroidColor.rgb(86, 180, 233),
                    AndroidColor.rgb(213, 94, 0)
                )
                val ds = PieDataSet(entries, T.t("Usage (minutes)", "उपयोग (मिनट)")).also {
                    it.colors        = palette.toMutableList()
                    it.valueTextSize = 12f
                    it.valueTextColor = AndroidColor.WHITE
                    it.sliceSpace    = 2f
                }
                chart.data       = PieData(ds)
                chart.centerText = T.t("Today", "आज")
                chart.setCenterTextSize(13f)
                chart.invalidate()
                chart.animateY(800)
            },
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )
        Spacer(Modifier.height(6.dp))
        topApps.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(T.appName(app.appName), fontSize = 12.sp, color = Gray900, modifier = Modifier.weight(1f))
                Text(T.min(app.minutes),     fontSize = 12.sp, color = Gray600)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun SlidingTabBar(
    tabs    : List<String>,
    icons   : List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        tabs.forEachIndexed { index, label ->
            val isActive = selected == index
            Column(
                modifier              = Modifier.clickable { onSelect(index) }.padding(horizontal = 14.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(icons[index], fontSize = 13.sp, color = if (isActive) Color.White else Color.White.copy(0.55f))
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, color = if (isActive) Color.White else Color.White.copy(0.55f), maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (isActive) Color.White else Color.Transparent))
            }
        }
    }
}

@Composable
fun SageCard(
    modifier: Modifier = Modifier,
    color   : Color    = Surface,
    content : @Composable ColumnScope.() -> Unit
) {
    val isColored = color != Surface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = color),
        border   = if (isColored) null else androidx.compose.foundation.BorderStroke(0.5.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SageButton(
    text          : String,
    onClick       : () -> Unit,
    containerColor: Color   = Teal700,
    enabled       : Boolean = true
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = containerColor,
            disabledContainerColor = containerColor.copy(0.5f)
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun SageOutlineButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(12.dp),
        border   = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray600)
    }
}

@Composable
fun SageTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, fontSize = 13.sp) },
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(10.dp),
        singleLine    = true
    )
}

@Composable
fun AuthScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Surface2)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title,    fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Teal700)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = Gray600)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
fun StatBox(label: String, value: String, valueColor: Color = Gray900, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(0.5.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = Gray600)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
    }
}

@Composable
fun AlertRow(icon: String, iconBg: Color, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(iconBg)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Red700),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title,    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Text(subtitle, fontSize = 11.sp, color = Gray600)
        }
    }
}

@Composable
fun ChildChip(child: ChildInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) Teal700 else BorderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Teal50), contentAlignment = Alignment.Center) {
            Text(child.childName.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Teal700)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(child.childName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
            Text(child.childId,   fontSize = 11.sp, color = Gray600)
        }
        if (selected) Text("✓", color = Teal700, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppUsageRow(app: AppUsage) {
    val isOver = app.minutes > app.limitMinutes
    val isWarn = !isOver && app.limitMinutes > 0 && app.minutes >= app.limitMinutes * 0.8
    val pct    = if (app.limitMinutes > 0) (app.minutes.toFloat() / app.limitMinutes).coerceAtMost(1f) else 0f

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(T.appName(app.appName), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
            StatusBadge(app.minutes, app.limitMinutes)
        }
        Text("${T.min(app.minutes)} ${T.t("used", "इस्तेमाल")} · ${T.min(app.limitMinutes)} ${T.t("limit", "सीमा")}", fontSize = 11.sp, color = Gray600)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = pct,
            modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color      = when { isOver -> Red700; isWarn -> Amber400; else -> Green400 },
            trackColor = Gray50
        )
        Divider(Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = BorderColor)
    }
}

@Composable
fun StatusBadge(minutes: Long, limit: Long) {
    val isOver = minutes > limit
    val isWarn = !isOver && limit > 0 && minutes >= limit * 0.8
    val (bg, fg, label) = when {
        isOver -> Triple(Red50,   Red700,   T.t("Over limit", "सीमा पार"))
        isWarn -> Triple(Amber50, Amber700, "80%")
        else   -> Triple(Green50, Green700, T.t("OK", "ठीक"))
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun PointStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.7f))
    }
}

@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(1.dp, BorderColor, RoundedCornerShape(50.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = "EN",
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (T.lang == AppLang.EN) Teal700 else Gray600,
            modifier   = Modifier.clickable { T.lang = AppLang.EN }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text("|", color = Gray400, fontSize = 12.sp)
        Text(
            text       = "हिंदी",
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (T.lang == AppLang.HI) Teal700 else Gray600,
            modifier   = Modifier.clickable { T.lang = AppLang.HI }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════
fun hasUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode   = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getTodayUsageStats(context: Context): List<AppUsage> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val cal = Calendar.getInstance()
    val endTime = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
    val startTime = cal.timeInMillis

    val events   = usm.queryEvents(startTime, endTime)
    val startMap = mutableMapOf<String, Long>()
    val totalMap = mutableMapOf<String, Long>()
    val event    = android.app.usage.UsageEvents.Event()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        if (shouldIgnoreApp(context, pkg)) continue
        when (event.eventType) {
            android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> startMap[pkg] = event.timeStamp
            android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val s = startMap[pkg]
                if (s != null && event.timeStamp > s) {
                    totalMap[pkg] = totalMap.getOrDefault(pkg, 0L) + (event.timeStamp - s)
                    startMap.remove(pkg)
                }
            }
        }
    }
    for ((pkg, s) in startMap) {
        if (!shouldIgnoreApp(context, pkg) && endTime > s)
            totalMap[pkg] = totalMap.getOrDefault(pkg, 0L) + (endTime - s)
    }
    return totalMap.mapNotNull { (pkg, ms) ->
        val mins = ms / 1000 / 60
        if (mins > 0) AppUsage(pkg, getAppName(context, pkg), mins, 60) else null
    }.sortedByDescending { it.minutes }
}

fun getAppName(context: Context, packageName: String): String {
    val known = mapOf(
        "com.instagram.android"               to "Instagram",
        "com.whatsapp"                        to "WhatsApp",
        "com.google.android.youtube"          to "YouTube",
        "com.snapchat.android"                to "Snapchat",
        "in.startv.hotstar"                   to "Disney+ Hotstar",
        "com.android.chrome"                  to "Chrome",
        "com.openai.chatgpt"                  to "ChatGPT",
        "com.truecaller"                      to "Truecaller",
        "com.spotify.music"                   to "Spotify",
        "com.discord"                         to "Discord",
        "in.swiggy.android"                   to "Swiggy",
        "com.facebook.katana"                 to "Facebook",
        "org.telegram.messenger"              to "Telegram",
        "com.google.android.gm"               to "Gmail",
        "com.google.android.apps.maps"        to "Maps",
        "com.google.android.apps.meet"        to "Meet",
        "com.google.android.apps.classroom"   to "Classroom",
        "com.google.android.apps.nexuslauncher" to "System Launcher",
        "com.android.launcher3"               to "System Launcher"
    )
    return known[packageName] ?: try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}

fun shouldIgnoreApp(context: Context, packageName: String): Boolean =
    listOf(
        context.packageName,
        "com.google.android.apps.photos",
        "com.oneplus.gallery",
        "com.coloros.gallery3d",
        "com.android.gallery3d",
        "com.android.providers.media",
        "com.google.android.documentsui"
    ).contains(packageName)

fun safeKey(name: String) = name
    .replace(".", "_").replace("#", "_").replace("$", "_")
    .replace("[", "_").replace("]", "_").replace("/", "_")

fun makeChildId(childName: String): String {
    val c = childName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return "child_${c.ifBlank { "unknown" }}"
}

fun isValidSmallParentId(parentId: String) = Regex("^[A-Z]{2}-[0-9]{3}$").matches(parentId)

fun generateSmallParentId(): String {
    val l = ('A'..'Z').toList()
    val d = (0..9).toList()
    return "${l.random()}${l.random()}-${d.random()}${d.random()}${d.random()}"
}

fun createUniqueParentCode(onCodeReady: (String) -> Unit) {
    val db = FirebaseDatabase.getInstance().reference
    fun tryCode() {
        val c = generateSmallParentId()
        db.child("parentCodes").child(c).get()
            .addOnSuccessListener { if (it.exists()) tryCode() else onCodeReady(c) }
    }
    tryCode()
}

fun uploadToFirebase(appList: List<AppUsage>, parentId: String, childId: String, childName: String) {
    if (parentId.isBlank() || childId.isBlank()) return
    val db = FirebaseDatabase.getInstance().reference
    db.child("children").child(parentId).child(childId).updateChildren(
        mapOf("childId" to childId, "childName" to childName, "lastSync" to System.currentTimeMillis())
    )
    val ref      = db.child("usage").child(parentId).child(childId)
    val todayKey = getDateKey(0)
    val dailyRef = db.child("dailyUsage").child(parentId).child(childId).child(todayKey)
    dailyRef.child("dateKey").setValue(todayKey)
    dailyRef.child("totalMinutes").setValue(appList.sumOf { it.minutes })
    dailyRef.child("updatedAt").setValue(System.currentTimeMillis())

    for (app in appList) {
        val appRef = ref.child(safeKey(app.packageName))
        appRef.child("packageName").setValue(app.packageName)
        appRef.child("appName").setValue(app.appName)
        appRef.child("minutes").setValue(app.minutes)
        appRef.child("extraDate").get().addOnSuccessListener { snap ->
            val ed = snap.getValue(String::class.java) ?: ""
            if (ed.isNotBlank() && ed != todayKey) {
                appRef.child("extraMinutes").setValue(0)
                appRef.child("extraDate").setValue(todayKey)
            }
        }
        dailyRef.child("apps").child(safeKey(app.packageName)).also { dApp ->
            dApp.child("packageName").setValue(app.packageName)
            dApp.child("appName").setValue(app.appName)
            dApp.child("minutes").setValue(app.minutes)
            dApp.child("limitMinutes").setValue(app.limitMinutes)
        }
        appRef.child("limitMinutes").get()
            .addOnSuccessListener { if (!it.exists()) appRef.child("limitMinutes").setValue(60) }
    }
}

fun readChildrenList(parentId: String, onChildrenReceived: (List<ChildInfo>) -> Unit) {
    if (parentId.isBlank()) { onChildrenReceived(emptyList()); return }
    FirebaseDatabase.getInstance().reference.child("children").child(parentId)
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChildInfo>()
                for (child in snapshot.children) {
                    val cId   = child.child("childId").getValue(String::class.java) ?: child.key ?: ""
                    val cName = child.child("childName").getValue(String::class.java) ?: cId
                    if (cId.isNotBlank()) list.add(ChildInfo(cId, cName))
                }
                onChildrenReceived(list.sortedBy { it.childName.lowercase() })
            }
            override fun onCancelled(error: DatabaseError) { onChildrenReceived(emptyList()) }
        })
}

fun readFromFirebase(parentId: String, childId: String, onDataReceived: (List<AppUsage>) -> Unit) {
    if (parentId.isBlank() || childId.isBlank()) { onDataReceived(emptyList()); return }
    FirebaseDatabase.getInstance().reference
        .child("usage").child(parentId).child(childId)
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AppUsage>()
                for (child in snapshot.children) {
                    val pkg   = child.child("packageName").getValue(String::class.java) ?: ""
                    val name  = child.child("appName").getValue(String::class.java) ?: pkg
                    val mins  = (child.child("minutes").value as? Number)?.toLong() ?: 0L
                    val base  = (child.child("limitMinutes").value as? Number)?.toLong() ?: 60L
                    val extra = (child.child("extraMinutes").value as? Number)?.toLong() ?: 0L
                    if (pkg.isNotEmpty()) list.add(AppUsage(pkg, name, mins, base + extra))
                }
                onDataReceived(list.sortedByDescending { it.minutes })
            }
            override fun onCancelled(error: DatabaseError) { onDataReceived(emptyList()) }
        })
}

fun updateLimitInFirebase(parentId: String, childId: String, packageName: String, newLimit: Long) {
    if (parentId.isBlank() || childId.isBlank()) return
    FirebaseDatabase.getInstance().reference
        .child("usage").child(parentId).child(childId)
        .child(safeKey(packageName)).child("limitMinutes").setValue(newLimit)
}

fun getDateKey(daysAgo: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return "%04d_%02d_%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun getDayLabel(daysAgo: Int): String {
    val c  = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    val en = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val hi = listOf("रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि")
    return if (T.lang == AppLang.HI) hi[c.get(Calendar.DAY_OF_WEEK) - 1] else en[c.get(Calendar.DAY_OF_WEEK) - 1]
}

fun calculateTrendPercent(current: Long, previous: Long): Int {
    if (previous <= 0L) return if (current > 0L) 100 else 0
    return (((current - previous).toDouble() / previous.toDouble()) * 100.0).toInt()
}

fun calculateScreenScore(avgDailyMinutes: Long, violations: Int): Int {
    val sp = when {
        avgDailyMinutes < 120 -> 0
        avgDailyMinutes < 240 -> 10
        avgDailyMinutes < 360 -> 25
        else                  -> 40
    }
    return (100 - sp - (violations * 5).coerceAtMost(25)).coerceIn(0, 100)
}

fun readWeeklyReportData(
    parentId: String,
    childId : String,
    onReportReady: (List<DailyUsageReport>, List<DailyUsageReport>, List<AppUsage>, Int) -> Unit
) {
    if (parentId.isBlank() || childId.isBlank()) { onReportReady(emptyList(), emptyList(), emptyList(), 0); return }
    FirebaseDatabase.getInstance().reference
        .child("dailyUsage").child(parentId).child(childId)
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val curWeek   = mutableListOf<DailyUsageReport>()
                val prevWeek  = mutableListOf<DailyUsageReport>()
                val appTotals = mutableMapOf<String, AppUsage>()
                var violations = 0

                for (daysAgo in 6 downTo 0) {
                    val key   = getDateKey(daysAgo)
                    val total = (snapshot.child(key).child("totalMinutes").value as? Number)?.toLong() ?: 0L
                    curWeek.add(DailyUsageReport(key, getDayLabel(daysAgo), total))
                    for (appNode in snapshot.child(key).child("apps").children) {
                        val pkg   = appNode.child("packageName").getValue(String::class.java) ?: ""
                        val aName = appNode.child("appName").getValue(String::class.java) ?: pkg
                        val mins  = (appNode.child("minutes").value as? Number)?.toLong() ?: 0L
                        val limit = (appNode.child("limitMinutes").value as? Number)?.toLong() ?: 60L
                        if (pkg.isNotBlank()) {
                            if (mins > limit) violations++
                            val old = appTotals[pkg]
                            appTotals[pkg] = if (old == null) AppUsage(pkg, aName, mins, limit)
                            else old.copy(minutes = old.minutes + mins)
                        }
                    }
                }
                for (daysAgo in 13 downTo 7) {
                    val key   = getDateKey(daysAgo)
                    val total = (snapshot.child(key).child("totalMinutes").value as? Number)?.toLong() ?: 0L
                    prevWeek.add(DailyUsageReport(key, getDayLabel(daysAgo), total))
                }
                onReportReady(curWeek, prevWeek, appTotals.values.sortedByDescending { it.minutes }, violations)
            }
            override fun onCancelled(error: DatabaseError) { onReportReady(emptyList(), emptyList(), emptyList(), 0) }
        })
}

fun calculateRewardPoints(screenScore: Int): Long = when {
    screenScore >= 90 -> 50L
    screenScore >= 75 -> 30L
    screenScore >= 60 -> 15L
    else              -> 0L
}

fun getWeekKey(): String {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    return "%04d_week_%02d".format(c.get(Calendar.YEAR), c.get(Calendar.WEEK_OF_YEAR))
}

fun readRewardInfo(parentId: String, childId: String, onRewardReady: (RewardInfo) -> Unit) {
    if (parentId.isBlank() || childId.isBlank()) { onRewardReady(RewardInfo()); return }
    FirebaseDatabase.getInstance().reference
        .child("rewards").child(parentId).child(childId)
        .addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onRewardReady(RewardInfo(
                    browniePoints  = (snapshot.child("browniePoints").value  as? Number)?.toLong() ?: 0L,
                    totalEarned    = (snapshot.child("totalEarned").value    as? Number)?.toLong() ?: 0L,
                    totalRedeemed  = (snapshot.child("totalRedeemed").value  as? Number)?.toLong() ?: 0L,
                    todayExtraUsed = (snapshot.child("todayExtraUsed").value as? Number)?.toLong() ?: 0L,
                    lastExtraDate  = snapshot.child("lastExtraDate").getValue(String::class.java)  ?: "",
                    lastRewardWeek = snapshot.child("lastRewardWeek").getValue(String::class.java) ?: ""
                ))
            }
            override fun onCancelled(error: DatabaseError) { onRewardReady(RewardInfo()) }
        })
}

fun claimWeeklyReward(
    parentId   : String,
    childId    : String,
    screenScore: Int,
    onComplete : (Boolean, String) -> Unit
) {
    if (parentId.isBlank() || childId.isBlank()) { onComplete(false, "Child not selected"); return }
    val pts     = calculateRewardPoints(screenScore)
    val weekKey = getWeekKey()
    val ref     = FirebaseDatabase.getInstance().reference.child("rewards").child(parentId).child(childId)
    ref.get().addOnSuccessListener { snap ->
        if (snap.child("lastRewardWeek").getValue(String::class.java) == weekKey) {
            onComplete(false, "Reward already claimed this week"); return@addOnSuccessListener
        }
        val old  = (snap.child("browniePoints").value as? Number)?.toLong() ?: 0L
        val oldE = (snap.child("totalEarned").value   as? Number)?.toLong() ?: 0L
        ref.updateChildren(mapOf(
            "browniePoints"    to old + pts,
            "totalEarned"      to oldE + pts,
            "lastRewardWeek"   to weekKey,
            "lastRewardScore"  to screenScore,
            "lastRewardPoints" to pts,
            "lastRewardAt"     to System.currentTimeMillis()
        ))
            .addOnSuccessListener {
                onComplete(true, if (pts > 0) "$pts brownie points added!" else "Score below 60 — no points earned this week")
            }
            .addOnFailureListener { onComplete(false, it.message ?: "Update failed") }
    }.addOnFailureListener { onComplete(false, it.message ?: "Read failed") }
}

fun redeemBrowniePoints(
    parentId    : String,
    childId     : String,
    packageName : String,
    extraMinutes: Long,
    onComplete  : (Boolean, String) -> Unit
) {
    if (parentId.isBlank() || childId.isBlank() || packageName.isBlank()) { onComplete(false, "Missing data"); return }
    val cost      = when (extraMinutes) { 5L -> 10L; 10L -> 20L; 20L -> 40L; else -> extraMinutes * 2L }
    val todayKey  = getDateKey(0)
    val db        = FirebaseDatabase.getInstance().reference
    val rewardRef = db.child("rewards").child(parentId).child(childId)
    val appRef    = db.child("usage").child(parentId).child(childId).child(safeKey(packageName))

    rewardRef.get().addOnSuccessListener { rSnap ->
        val avail    = (rSnap.child("browniePoints").value  as? Number)?.toLong() ?: 0L
        val totalR   = (rSnap.child("totalRedeemed").value  as? Number)?.toLong() ?: 0L
        val lastDate = rSnap.child("lastExtraDate").getValue(String::class.java) ?: ""
        val oldToday = (rSnap.child("todayExtraUsed").value as? Number)?.toLong() ?: 0L
        val todayUsed = if (lastDate == todayKey) oldToday else 0L

        if (avail < cost) { onComplete(false, "Need $cost points, have $avail"); return@addOnSuccessListener }
        if (todayUsed + extraMinutes > 30L) { onComplete(false, "Daily extra-time limit is 30 minutes"); return@addOnSuccessListener }

        appRef.get().addOnSuccessListener { aSnap ->
            if (!aSnap.exists()) { onComplete(false, "App data not found. Please refresh usage first"); return@addOnSuccessListener }
            val oldExtra = (aSnap.child("extraMinutes").value as? Number)?.toLong() ?: 0L
            rewardRef.updateChildren(mapOf(
                "browniePoints"  to avail - cost,
                "totalRedeemed"  to totalR + cost,
                "todayExtraUsed" to todayUsed + extraMinutes,
                "lastExtraDate"  to todayKey,
                "lastRedeemedAt" to System.currentTimeMillis()
            )).addOnSuccessListener {
                appRef.updateChildren(mapOf(
                    "extraMinutes"   to oldExtra + extraMinutes,
                    "extraDate"      to todayKey,
                    "lastRedeemedAt" to System.currentTimeMillis()
                )).addOnSuccessListener {
                    onComplete(true, "+$extraMinutes minutes added. $cost points used.")
                }.addOnFailureListener { onComplete(false, it.message ?: "App update failed") }
            }.addOnFailureListener { onComplete(false, it.message ?: "Reward update failed") }
        }.addOnFailureListener { onComplete(false, it.message ?: "App read failed") }
    }.addOnFailureListener { onComplete(false, it.message ?: "Reward read failed") }
}

// ─── Notification helpers ────────────────────────────────────────────────
fun requestPostNotificationPermissionIfNeeded(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}

fun notifyExceededApps(
    context      : Context,
    parentId     : String,
    childId      : String,
    childName    : String,
    usageList    : List<AppUsage>,
    isParentPhone: Boolean
) {
    if (parentId.isBlank() || childId.isBlank()) return
    usageList.filter { it.limitMinutes > 0 && it.minutes >= it.limitMinutes }.forEach { app ->
        val alreadySent = wasLimitAlertSentToday(context, parentId, childId, app.packageName, app.minutes, app.limitMinutes, isParentPhone)
        if (!alreadySent) {
            sendLimitNotification(context, childName, app.appName, app.minutes, app.limitMinutes, isParentPhone)
            markLimitAlertSentToday(context, parentId, childId, app.packageName, app.minutes, app.limitMinutes, isParentPhone)
            saveLimitAlertToFirebase(parentId, childId, childName, app)
        }
    }
}

fun checkAndSendLimitAlertsFromFirebase(
    context      : Context,
    parentId     : String,
    childId      : String,
    childName    : String,
    isParentPhone: Boolean
) {
    if (parentId.isBlank() || childId.isBlank()) return
    FirebaseDatabase.getInstance().reference.child("usage").child(parentId).child(childId).get()
        .addOnSuccessListener { snapshot ->
            val list = mutableListOf<AppUsage>()
            for (child in snapshot.children) {
                val pkg   = child.child("packageName").getValue(String::class.java) ?: ""
                val name  = child.child("appName").getValue(String::class.java) ?: pkg
                val mins  = (child.child("minutes").value as? Number)?.toLong() ?: 0L
                val base  = (child.child("limitMinutes").value as? Number)?.toLong() ?: 60L
                val extra = (child.child("extraMinutes").value as? Number)?.toLong() ?: 0L
                if (pkg.isNotBlank()) list.add(AppUsage(pkg, name, mins, base + extra))
            }
            notifyExceededApps(context, parentId, childId, childName, list, isParentPhone)
        }
}

fun wasLimitAlertSentToday(
    context      : Context,
    parentId     : String,
    childId      : String,
    packageName  : String,
    usedMinutes  : Long,
    limitMinutes : Long,
    isParentPhone: Boolean
): Boolean {
    val prefs = context.getSharedPreferences("limit_alert_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(limitAlertKey(parentId, childId, packageName, usedMinutes, limitMinutes, isParentPhone), false)
}

fun markLimitAlertSentToday(
    context      : Context,
    parentId     : String,
    childId      : String,
    packageName  : String,
    usedMinutes  : Long,
    limitMinutes : Long,
    isParentPhone: Boolean
) {
    val prefs = context.getSharedPreferences("limit_alert_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(limitAlertKey(parentId, childId, packageName, usedMinutes, limitMinutes, isParentPhone), true).apply()
}

fun limitAlertKey(
    parentId     : String,
    childId      : String,
    packageName  : String,
    usedMinutes  : Long,
    limitMinutes : Long,
    isParentPhone: Boolean
): String {
    val phone = if (isParentPhone) "parent" else "child"
    return "${getDateKey(0)}_${phone}_${parentId}_${childId}_${safeKey(packageName)}_${limitMinutes}_${usedMinutes / 5}"
}

fun saveLimitAlertToFirebase(parentId: String, childId: String, childName: String, app: AppUsage) {
    if (parentId.isBlank() || childId.isBlank()) return
    val todayKey = getDateKey(0)
    val alertId  = "${safeKey(app.packageName)}_${System.currentTimeMillis()}"
    FirebaseDatabase.getInstance().reference
        .child("limitAlerts").child(parentId).child(childId).child(todayKey).child(alertId)
        .setValue(mapOf(
            "childId"      to childId,
            "childName"    to childName,
            "packageName"  to app.packageName,
            "appName"      to app.appName,
            "usedMinutes"  to app.minutes,
            "limitMinutes" to app.limitMinutes,
            "dateKey"      to todayKey,
            "createdAt"    to System.currentTimeMillis()
        ))
}

fun sendLimitNotification(
    context      : Context,
    childName    : String,
    appName      : String,
    usedMinutes  : Long,
    limitMinutes : Long,
    isParentPhone: Boolean
) {
    val channelId = "soft_limit_alert_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            T.t("Screen time alert", "स्क्रीन समय चेतावनी"),
            NotificationManager.IMPORTANCE_DEFAULT
        ).also {
            it.description = T.t(
                "Soft notifications when an app reaches its screen-time limit",
                "जब ऐप स्क्रीन समय सीमा तक पहुँचता है तो सौम्य सूचना"
            )
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    val hApp  = T.appName(appName)
    val title = if (isParentPhone)
        T.t("Alert: $childName reached a limit", "चेतावनी: $childName ने सीमा पार की")
    else
        T.t("Screen time limit reached", "स्क्रीन समय सीमा पूरी हुई")

    val message = if (isParentPhone)
        T.t(
            "$childName has used $appName for $usedMinutes min (limit: $limitMinutes min).",
            "$childName ने $hApp का उपयोग ${T.min(usedMinutes)} किया (सीमा: ${T.min(limitMinutes)})।"
        )
    else
        T.t(
            "$appName: $usedMinutes min used, limit is $limitMinutes min.",
            "$hApp: ${T.min(usedMinutes)} इस्तेमाल, सीमा ${T.min(limitMinutes)}।"
        )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        val notifId = "$childName-$appName-${getDateKey(0)}-${if (isParentPhone) "parent" else "child"}".hashCode()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}