package com.garidoctor.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.os.SystemClock
import com.garidoctor.app.ui.theme.DevicePerf
import com.garidoctor.app.ui.theme.LocalPerf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.garidoctor.app.ui.screens.*
import com.garidoctor.app.ui.theme.Accent
import com.garidoctor.app.ui.theme.GariTheme
import com.garidoctor.app.ui.theme.UiStyle

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hold the launch screen until data is ready (never longer than 1.2 s), so the first frame is
        // real content instead of an empty list that pops in.
        val start = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !vm.state.value.loaded && SystemClock.uptimeMillis() - start < 1200 }
        // Notification taps carry a route; the older vehicle-only extra still opens the vehicle.
        val startVehicle = if (intent.hasExtra(EXTRA_ROUTE)) -1L else intent.getLongExtra(EXTRA_VEHICLE, -1L)
        if (savedInstanceState == null) vm.handleLaunch(intent)
        val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)
        setContent {
            val lang by vm.lang.collectAsStateWithLifecycle()
            val style by vm.prefs.uiStyle.collectAsStateWithLifecycle()
            val accent by vm.prefs.accent.collectAsStateWithLifecycle()
            val perfMode by vm.prefs.perfMode.collectAsStateWithLifecycle()
            // Re-check on resume: battery saver or system animation settings may have changed.
            var resumes by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
            val ctx = LocalContext.current
            val perf = remember(perfMode, resumes) { DevicePerf.policy(ctx, perfMode) }
            CompositionLocalProvider(LocalLang provides lang, LocalPerf provides perf) {
                // Light/dark comes from AppCompat's night mode (set from Settings), so isSystemInDarkTheme() is right.
                GariTheme(style = if (style == "MATERIAL") UiStyle.MATERIAL else UiStyle.SOFT, accent = Accent.of(accent)) {
                    val chosen by vm.prefs.langChosen.collectAsStateWithLifecycle()
                    if (!chosen) LanguageOnboarding(vm) else { NotificationPermission(vm); AppNav(vm, startVehicle, shortcut) }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleLaunch(intent)
    }

    // Firebase messages that arrive while the app is visible become in-app banners, not system notifications.
    override fun onStart() { super.onStart(); com.garidoctor.app.notify.Inbox.foreground = true }
    override fun onStop() { com.garidoctor.app.notify.Inbox.foreground = false; super.onStop() }

    companion object {
        /** In-app destination to open (see notify.Routes). */
        const val EXTRA_ROUTE = "route"
        const val EXTRA_INBOX_ID = "inbox_id"
        const val EXTRA_VEHICLE = "vehicle_id"
        /** Launcher shortcuts: "speak", "scan_paper", "scan_bill", "fuel". */
        const val EXTRA_SHORTCUT = "shortcut"
    }
}

@Composable
private fun NotificationPermission(vm: MainViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.prefs.askedNotifications) {
            vm.prefs.askedNotifications = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppNav(vm: MainViewModel, startVehicle: Long, shortcut: String?) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    val st by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(startVehicle) { if (startVehicle > 0) nav.navigate("vehicle/$startVehicle") }
    // Launcher shortcut: go straight to the action on the last-used vehicle (or add one first).
    var shortcutHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcut, st.loaded) {
        if (shortcut == null || shortcutHandled || !st.loaded) return@LaunchedEffect
        shortcutHandled = true
        val vehicles = st.snapshot.vehicles
        val id = vehicles.firstOrNull { it.id == vm.prefs.lastVehicleId }?.id ?: vehicles.firstOrNull()?.id
        when {
            id == null -> nav.navigate("add")
            shortcut == "fuel" -> nav.navigate("fuel/$id")
            else -> nav.navigate("vehicle/$id?action=$shortcut")
        }
    }
    // A tapped notification or in-app banner: open its screen once the data is there.
    val pending by vm.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pending, st.loaded) {
        val req = pending ?: return@LaunchedEffect
        if (!st.loaded) return@LaunchedEffect
        vm.pendingOpen.value = null
        vm.resolve(req)?.let { r -> runCatching { nav.navigate(r) { launchSingleTop = true } } }
    }
    val lite = LocalPerf.current.lite
    // Top-level tabs keep their own back stack/state, like Google apps.
    val selectTab: (Tab) -> Unit = { tab ->
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val bar: @Composable (Tab) -> Unit = { MainNavBar(it, selectTab) }
    androidx.compose.foundation.layout.Box {
    NavHost(
        nav, startDestination = "home",
        // Lite: screens switch instantly (no animation work on slow GPUs).
        enterTransition = { if (lite) EnterTransition.None else fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 12 } },
        exitTransition = { if (lite) ExitTransition.None else fadeOut(tween(140)) },
        popEnterTransition = { if (lite) EnterTransition.None else fadeIn(tween(200)) },
        popExitTransition = { if (lite) ExitTransition.None else fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { it / 12 } },
    ) {
        composable("home") { HomeScreen(vm, onOpen = { nav.navigate("vehicle/$it") }, onAdd = { nav.navigate("add") }, onNav = { nav.navigate(it) }, bottomBar = { bar(Tab.GARAGE) }) }
        composable("fleet") { FleetScreen(vm, null, onOpen = { nav.navigate("vehicle/$it") }, bottomBar = { bar(Tab.FLEET) }) }
        composable("help") { GaragesScreen(vm, null, bottomBar = { bar(Tab.HELP) }) }
        composable("settings") { SettingsScreen(vm, null, bottomBar = { bar(Tab.SETTINGS) }) }
        composable("add") { AddVehicleScreen(vm, onBack = back, onCreated = { id -> nav.popBackStack(); nav.navigate("vehicle/$id") }) }
        composable("vehicle/{id}?action={action}", listOf(navArgument("id") { type = NavType.LongType }, navArgument("action") { type = NavType.StringType; nullable = true; defaultValue = null })) {
            VehicleScreen(vm, it.arguments!!.getLong("id"), back, startAction = it.arguments?.getString("action")) { route -> nav.navigate(route) }
        }
        composable("service/{id}", idArg) { ServiceScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("fuel/{id}", idArg) { FuelScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("docs/{id}", idArg) { DocumentsScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("history/{id}", idArg) { HistoryScreen(vm, it.arguments!!.getLong("id"), back, nav = { r -> nav.navigate(r) }) }
        composable("vehicleid/{id}", idArg) { VehicleIdScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("symptoms/{id}", idArg) { SymptomScreen(vm, it.arguments!!.getLong("id"), back, onGarages = { nav.navigate("garages") }) }
        composable("checks/{id}", idArg) { ChecksScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("obd/{id}", idArg) { ObdScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("odometer/{id}", idArg) { OdometerScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("battery/{id}", idArg) { BatteryScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("lights/{id}", idArg) { WarningLightsScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("garages") { GaragesScreen(vm, back) }
        composable("inbox") { InboxScreen(vm, back) }
    }
    InAppBanner(vm)
    }
}
