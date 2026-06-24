package com.skipvox.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        // Stripe payment links for web purchase fallback
        const val STRIPE_URL_MONTHLY = "https://buy.stripe.com/14AfZjapVaFM6fZcEWfQI09"
        const val STRIPE_URL_YEARLY = "https://buy.stripe.com/5kQ7sN9lR7tAdIrbASfQI0a"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize state engine
        SkipVoxState.initialize(applicationContext)
        prefs = getSharedPreferences("skipvox_preferences", Context.MODE_PRIVATE)

        // Initialize Google Play Billing
        billingManager = BillingManager(applicationContext)
        billingManager.startConnection()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE50914), // Netflix/Streaming-inspired red accent
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if Accessibility Service is running and update active state
        val isServiceRunning = isAccessibilityServiceEnabled(this, SkipAdAccessibilityService::class.java)
        SkipVoxState.setServiceRunning(isServiceRunning)

        // Refresh purchases when app resumes
        if (::billingManager.isInitialized) {
            billingManager.restorePurchases()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingManager.isInitialized) {
            billingManager.endConnection()
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        val isServiceRunning by SkipVoxState.isServiceRunning.collectAsState()
        val isListening by SkipVoxState.isListening.collectAsState()
        val skipStatus by SkipVoxState.skipStatus.collectAsState()
        val isPremium by SkipVoxState.isPremium.collectAsState()
        val remainingSkips by SkipVoxState.freeSkipsRemaining.collectAsState()
        val isBillingConnected by billingManager.isConnected.collectAsState()

        var hasMicPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        var masterToggleEnabled by remember {
            mutableStateOf(prefs.getBoolean(SkipAdAccessibilityService.KEY_IS_ENABLED, false))
        }

        var showPremiumDialog by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasMicPermission = isGranted
            if (isGranted) {
                Toast.makeText(this@MainActivity, "Microphone access granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Microphone access denied. Voice commands will not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "SkipVox Dashboard",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),
                    actions = {
                        if (isPremium) {
                            Badge(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Text("PREMIUM", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Introduction card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Hands-Free Ad Skipper",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "SkipVox runs in the background and listens for your voice to tap \"Skip Ad\" on YouTube, Hulu, and other streaming apps hands-free.",
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Master switch card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ad-Skipper Master Switch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (masterToggleEnabled) "Listening & scanning active" else "System paused",
                                fontSize = 12.sp,
                                color = if (masterToggleEnabled) Color.Green else Color.Gray
                            )
                        }
                        Switch(
                            checked = masterToggleEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !isServiceRunning) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Please enable the Accessibility Service first!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    masterToggleEnabled = isChecked
                                    prefs.edit().putBoolean(SkipAdAccessibilityService.KEY_IS_ENABLED, isChecked).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Real-time Status Card (Terminal style)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isListening && masterToggleEnabled) Color.Green else Color.Red,
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Live Status Engine:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            skipStatus,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = if (skipStatus.contains("Triggered")) Color.Green else Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Permissions Section Header
                Text(
                    "System Integrations",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 1. Accessibility Service Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceRunning) Color(0xFF1B3B1B) else Color(0xFF331313)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isServiceRunning) Color.Green else Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accessibility Service Permission",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (isServiceRunning) "Status: Enabled & Active" else "Status: Inactive (Required to click buttons)",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                            if (!isServiceRunning) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        startActivity(intent)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Find 'SkipVox Voice Ad-Skipper' in the list and enable it",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Grant Permission", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Microphone Permission Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasMicPermission) Color(0xFF1B3B1B) else Color(0xFF331313)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasMicPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasMicPermission) Color.Green else Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Microphone Audio Permission",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (hasMicPermission) "Status: Allowed" else "Status: Rejected (Required for voice wake command)",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                            if (!hasMicPermission) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Grant Permission", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Freemium Monetization Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Freemium Subscription Plan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (isPremium) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Premium Status",
                                    tint = Color(0xFFFFD700)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isPremium) {
                            Text(
                                "Premium Activated! You have unlimited voice ad-skips, custom wake words, and continuous background detection.",
                                fontSize = 14.sp,
                                color = Color.Green
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Subscription managed by Google Play. Manage or cancel anytime in Play Store.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    // Open Google Play subscriptions page so user can manage/cancel
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_SUBSCRIPTIONS
                                        )
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Open Play Store > Subscriptions to manage",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                            ) {
                                Text("Manage Subscription")
                            }
                        } else {
                            Text(
                                "Daily Free Skips Remaining: ${if (remainingSkips >= 0) remainingSkips else 0} / $SkipVoxState.MAX_FREE_SKIPS",
                                fontWeight = FontWeight.SemiBold,
                                color = if (remainingSkips > 0) Color.White else Color.Red,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { ((if (remainingSkips >= 0) remainingSkips else 0).toFloat() / SkipVoxState.MAX_FREE_SKIPS.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showPremiumDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black)
                            ) {
                                Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upgrade to Premium (\$2.99/mo)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Help/Testing Footer instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("How to Test & Use SkipVox", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "1. Grant Accessibility and Microphone Permissions.\n" +
                            "2. Toggle the Master Switch ON.\n" +
                            "3. Open YouTube or any streaming platform in the background.\n" +
                            "4. When a \"Skip Ad\" button is displayed, say \"Skip\" or \"Skip Ad\" in a clear voice.\n" +
                            "5. The app will immediately detect the button and click it hands-free!",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Premium subscription dialog (Google Play Billing integration + Stripe fallback)
        if (showPremiumDialog) {
            PremiumSubscriptionDialog(
                monthlyPrice = billingManager.getFormattedPrice(BillingManager.SKU_MONTHLY),
                yearlyPrice = billingManager.getFormattedPrice(BillingManager.SKU_YEARLY),
                isBillingConnected = isBillingConnected,
                onSelectMonthly = {
                    showPremiumDialog = false
                    billingManager.launchPurchaseFlow(
                        this@MainActivity,
                        BillingManager.SKU_MONTHLY
                    )
                },
                onSelectYearly = {
                    showPremiumDialog = false
                    billingManager.launchPurchaseFlow(
                        this@MainActivity,
                        BillingManager.SKU_YEARLY
                    )
                },
                onRestorePurchases = {
                    billingManager.restorePurchases()
                    Toast.makeText(
                        this@MainActivity,
                        "Checking for existing subscriptions...",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onStripeMonthly = {
                    showPremiumDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STRIPE_URL_MONTHLY))
                    startActivity(intent)
                },
                onStripeYearly = {
                    showPremiumDialog = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STRIPE_URL_YEARLY))
                    startActivity(intent)
                },
                onDismiss = { showPremiumDialog = false }
            )
        }
    }

    @Composable
    fun PremiumSubscriptionDialog(
        monthlyPrice: String,
        yearlyPrice: String,
        isBillingConnected: Boolean,
        onSelectMonthly: () -> Unit,
        onSelectYearly: () -> Unit,
        onRestorePurchases: () -> Unit,
        onStripeMonthly: () -> Unit,
        onStripeYearly: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        "Unlock SkipVox Premium",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "Experience the ultimate hands-free convenience with premium features:",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BulletItem("Unlimited daily skips (Never hit a limit)")
                        BulletItem("Custom wake words (Choose your own commands)")
                        BulletItem("Lockscreen background listening mode")
                        BulletItem("Zero ads & instant voice processing")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Monthly subscription option
                    OutlinedButton(
                        onClick = onSelectMonthly,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        enabled = isBillingConnected
                    ) {
                        Text(
                            "Subscribe Monthly — $monthlyPrice/mo",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Yearly subscription option (highlighted as best value)
                    Button(
                        onClick = onSelectYearly,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        ),
                        enabled = isBillingConnected
                    ) {
                        Text(
                            "Subscribe Yearly — $yearlyPrice/yr (Best Value)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isBillingConnected) {
                        Text(
                            "Connecting to Google Play...",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Restore purchases
                    TextButton(
                        onClick = onRestorePurchases,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Purchases", color = Color.White)
                    }

                    // Divider before web purchase fallback
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color(0xFF444444)
                    )

                    Text(
                        "Or purchase via web:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        onClick = onStripeMonthly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Monthly — $monthlyPrice/mo (Web)",
                            fontSize = 12.sp,
                            color = Color(0xFF88BBFF)
                        )
                    }

                    TextButton(
                        onClick = onStripeYearly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Yearly — $yearlyPrice/yr (Web, Best Value)",
                            fontSize = 12.sp,
                            color = Color(0xFF88BBFF)
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later", color = Color.Gray)
                    }
                }
            }
        }
    }

    @Composable
    fun BulletItem(text: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, fontSize = 12.sp, color = Color.White)
        }
    }
}