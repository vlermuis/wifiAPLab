package com.example.wifiaplab

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var wifi: WifiManager
    private lateinit var logStore: LogStore
    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var eventReceiver: BroadcastReceiver

    private lateinit var ssid: EditText
    private lateinit var password: EditText
    private lateinit var band: Spinner
    private lateinit var channel: EditText
    private lateinit var hidden: CheckBox
    private lateinit var maxClients: EditText

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        logStore = LogStore(this)
        setContentView(buildUi())
        registerEvents()
        requestWifiPermissions()
        append("APP_STARTED", "WiFi AP Lab opened")
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(TextView(this).apply {
            text = "WiFi AP Lab"
            textSize = 28f
            setTextColor(0xff0d47a1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Local-only Soft AP test harness"
            textSize = 15f
            setPadding(0, 0, 0, 20)
        })
        statusView = TextView(this).apply {
            text = "● STOPPED"
            textSize = 18f
            setTextColor(0xffb71c1c.toInt())
            setPadding(0, 12, 0, 12)
        }
        root.addView(statusView)

        root.addView(section("AP configuration"))
        ssid = field("Requested SSID", "WiFi-AP-Lab")
        password = field("Requested WPA2 password (8+ chars)", "aplab1234")
        password.inputType = 0x00000081
        root.addView(ssid); root.addView(password)
        band = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Auto / OS selected", "2.4 GHz", "5 GHz", "6 GHz")) }
        root.addView(label("Band (recorded intent; stock LocalOnly AP may ignore it)")); root.addView(band)
        channel = field("Channel (0 = auto)", "0"); root.addView(channel)
        hidden = CheckBox(this).apply { text = "Hidden SSID (recorded intent; not available to stock API)" }; root.addView(hidden)
        maxClients = field("Maximum clients (0 = OS default)", "0"); root.addView(maxClients)

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "START AP"; setOnClickListener { startAp() } })
        buttons.addView(Button(this).apply { text = "STOP AP"; setOnClickListener { stopAp() } })
        root.addView(buttons)

        root.addView(section("AP runtime details"))
        val details = TextView(this).apply { text = "Client visibility: limited by Android\nAP controls: LocalOnlyHotspot API"; setPadding(0, 4, 0, 10) }
        root.addView(details)
        root.addView(section("Event log"))
        logView = TextView(this).apply { textSize = 12f; setTextIsSelectable(true); setTypeface(null, 1) }
        root.addView(logView)
        root.addView(Button(this).apply { text = "CLEAR LOG"; setOnClickListener { logStore.clear(); refreshLog() } })
        refreshLog()
        return scroll
    }

    private fun startAp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (password.text.length < 8) { toast("Password must contain at least 8 characters"); return }
        logStore.put("REQUESTED_CONFIG", "ssid=${ssid.text}; band=${band.selectedItem}; channel=${channel.text}; hidden=${hidden.isChecked}; maxClients=${maxClients.text}")
        append("AP_START_REQUEST", "Starting OS-managed LocalOnlyHotspot")
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val config = r.wifiConfiguration
                    statusView.text = "● RUNNING"
                    statusView.setTextColor(0xff2e7d32.toInt())
                    append("AP_STARTED", "ssid=${config?.SSID ?: "unknown"}; security=OS-managed")
                    toast("AP started. Android supplied the final credentials.")
                }
                override fun onStopped() { reservation = null; setStopped(); append("AP_STOPPED", "OS stopped LocalOnlyHotspot") }
                override fun onFailed(reason: Int) { setStopped(); append("AP_FAILED", "reason=$reason"); toast("AP failed: $reason") }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) { append("PERMISSION_ERROR", e.message ?: "permission denied"); requestWifiPermissions() }
    }

    private fun stopAp() { reservation?.close(); reservation = null; setStopped(); append("AP_STOP_REQUEST", "Reservation closed") }
    private fun setStopped() { statusView.text = "● STOPPED"; statusView.setTextColor(0xffb71c1c.toInt()) }

    private fun registerEvents() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                append("BROADCAST", i.action ?: "unknown")
            }
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(eventReceiver, filter)
    }

    private fun requestWifiPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT < 33 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 40)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 40) append("PERMISSIONS", permissions.zip(results.toTypedArray()).joinToString { "${it.first}=${it.second == PackageManager.PERMISSION_GRANTED}" })
    }

    override fun onDestroy() {
        reservation?.close()
        if (::eventReceiver.isInitialized) unregisterReceiver(eventReceiver)
        super.onDestroy()
    }

    private fun append(type: String, detail: String) { logStore.put(type, detail); refreshLog() }
    private fun refreshLog() { if (::logView.isInitialized) logView.text = logStore.read().takeLast(120).joinToString("\n") }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun section(s: String) = TextView(this).apply { text = s; textSize = 19f; setTextColor(0xff1565c0.toInt()); setPadding(0, 20, 0, 6) }
    private fun label(s: String) = TextView(this).apply { text = s; setPadding(0, 8, 0, 2) }
    private fun field(hint: String, value: String) = EditText(this).apply { this.hint = hint; setText(value); setSingleLine(true) }
}

private class LogStore(context: Context) {
    private val file = java.io.File(context.filesDir, "wifi-ap-events.log")
    fun put(type: String, detail: String) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
        file.appendText("$now | $type | $detail\n")
    }
    fun read(): List<String> = if (file.exists()) file.readLines() else emptyList()
    fun clear() { file.writeText("") }
}
