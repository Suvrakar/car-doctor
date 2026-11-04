package com.garidoctor.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.garidoctor.app.ui.screens.*
import com.garidoctor.app.ui.theme.GariTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startVehicle = intent.getLongExtra(EXTRA_VEHICLE, -1L)
        setContent {
            val lang by vm.lang.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalLang provides lang) {
                GariTheme { NotificationPermission(vm); AppNav(vm, startVehicle) }
            }
        }
    }

    companion object { const val EXTRA_VEHICLE = "vehicle_id" }
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
private fun AppNav(vm: MainViewModel, startVehicle: Long) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    LaunchedEffect(startVehicle) { if (startVehicle > 0) nav.navigate("vehicle/$startVehicle") }
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, onOpen = { nav.navigate("vehicle/$it") }, onAdd = { nav.navigate("add") }, onNav = { nav.navigate(it) }) }
        composable("add") { AddVehicleScreen(vm, onBack = back, onCreated = { id -> nav.popBackStack(); nav.navigate("vehicle/$id") }) }
        composable("vehicle/{id}", idArg) { VehicleScreen(vm, it.arguments!!.getLong("id"), back) { route -> nav.navigate(route) } }
        composable("service/{id}", idArg) { ServiceScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("fuel/{id}", idArg) { FuelScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("docs/{id}", idArg) { DocumentsScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("symptoms/{id}", idArg) { SymptomScreen(vm, it.arguments!!.getLong("id"), back, onGarages = { nav.navigate("garages") }) }
        composable("checks/{id}", idArg) { ChecksScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("obd/{id}", idArg) { ObdScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("odometer/{id}", idArg) { OdometerScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("battery/{id}", idArg) { BatteryScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("lights/{id}", idArg) { WarningLightsScreen(vm, it.arguments!!.getLong("id"), back) }
        composable("garages") { GaragesScreen(vm, back) }
        composable("fleet") { FleetScreen(vm, back, onOpen = { nav.navigate("vehicle/$it") }) }
        composable("settings") { SettingsScreen(vm, back) }
    }
}
