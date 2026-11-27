package alexcmb.mytvlauncher.widget.builtin

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.compose.glass
import alexcmb.mytvlauncher.widget.WidgetShape
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface

data class SystemStats(
    val storageTotalGb: Float,
    val storageFreeGb: Float,
    val storageUsedPercent: Float,
    val ramTotalGb: Float,
    val ramAvailGb: Float,
    val ramUsedPercent: Float,
    val isWifi: Boolean,
    val isEthernet: Boolean,
    val networkName: String,
    val ipAddress: String,
)

@Composable
fun SystemMonitorWidget(
    shape: WidgetShape = WidgetShape.WIDE,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(readSystemStats(context)) }

    // Periodically refresh stats (every 10 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            stats = readSystemStats(context)
        }
    }

    val shapeRadius = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .glass(shapeRadius, opacity = 0.5f)
            .padding(14.dp),
    ) {
        when (shape) {
            WidgetShape.SQUARE -> CompactSystemLayout(stats)
            else -> WideSystemLayout(stats, showNetworkDetails = shape != WidgetShape.STANDARD)
        }
    }
}

@Composable
private fun WideSystemLayout(stats: SystemStats, showNetworkDetails: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Storage and RAM bars
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            // Storage gauge
            StatItem(
                icon = R.drawable.ic_storage,
                title = stringResource(R.string.widget_system_storage),
                valueText = "%.1f / %.1f Go".format(stats.storageTotalGb - stats.storageFreeGb, stats.storageTotalGb),
                progress = stats.storageUsedPercent,
                barColor = Color(0xFF64B5F6),
            )

            // RAM gauge
            StatItem(
                icon = R.drawable.ic_memory,
                title = stringResource(R.string.widget_system_ram),
                valueText = "%.1f / %.1f Go".format(stats.ramTotalGb - stats.ramAvailGb, stats.ramTotalGb),
                progress = stats.ramUsedPercent,
                barColor = Color(0xFF81C784),
            )
        }

        if (showNetworkDetails) {
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(90.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.width(14.dp))

            // Right Column: Network & IP address
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header: Network Type + Status dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(if (stats.isEthernet) R.drawable.ic_ethernet else R.drawable.ic_wifi),
                        contentDescription = null,
                        tint = if (stats.isWifi || stats.isEthernet) Color(0xFF81C784) else Color(0xFFE57373),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stats.networkName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // IP Address Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.widget_network_local_ip),
                            color = Color(0xFF9AA0B4),
                            fontSize = 10.sp,
                        )
                        Text(
                            text = stats.ipAddress,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Status chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (stats.isWifi || stats.isEthernet) Color(0xFF81C784) else Color(0xFFE57373),
                                CircleShape,
                            ),
                    )
                    Text(
                        text = stringResource(
                            if (stats.isWifi || stats.isEthernet) R.string.widget_network_online
                            else R.string.widget_network_offline
                        ),
                        color = Color(0xFF9AA0B4),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSystemLayout(stats: SystemStats) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(
            icon = R.drawable.ic_storage,
            title = stringResource(R.string.widget_system_storage),
            valueText = "${(stats.storageUsedPercent * 100).toInt()}%",
            progress = stats.storageUsedPercent,
            barColor = Color(0xFF64B5F6),
        )

        StatItem(
            icon = R.drawable.ic_memory,
            title = stringResource(R.string.widget_system_ram),
            valueText = "${(stats.ramUsedPercent * 100).toInt()}%",
            progress = stats.ramUsedPercent,
            barColor = Color(0xFF81C784),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painter = painterResource(if (stats.isEthernet) R.drawable.ic_ethernet else R.drawable.ic_wifi),
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(13.dp),
                )
                Text("IP", color = Color(0xFF9AA0B4), fontSize = 11.sp)
            }
            Text(stats.ipAddress, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatItem(
    icon: Int,
    title: String,
    valueText: String,
    progress: Float,
    barColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = title,
                    color = Color(0xFFD0D6E8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = valueText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
            )
        }

        Spacer(Modifier.height(5.dp))

        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(barColor),
            )
        }
    }
}

private fun readSystemStats(context: Context): SystemStats {
    // Storage
    val statFs = StatFs(Environment.getDataDirectory().path)
    val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
    val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
    val storageTotalGb = totalBytes.toFloat() / (1024f * 1024f * 1024f)
    val storageFreeGb = freeBytes.toFloat() / (1024f * 1024f * 1024f)
    val storageUsedPercent = if (storageTotalGb > 0) (storageTotalGb - storageFreeGb) / storageTotalGb else 0f

    // RAM
    val actMan = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actMan?.getMemoryInfo(memInfo)
    val ramTotalGb = memInfo.totalMem.toFloat() / (1024f * 1024f * 1024f)
    val ramAvailGb = memInfo.availMem.toFloat() / (1024f * 1024f * 1024f)
    val ramUsedPercent = if (ramTotalGb > 0) (ramTotalGb - ramAvailGb) / ramTotalGb else 0f

    // Network
    var isWifi = false
    var isEthernet = false
    var netName = context.getString(R.string.widget_network_disconnected)

    try {
        val connMan = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connMan?.activeNetwork
        val caps = connMan?.getNetworkCapabilities(activeNet)
        isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        if (isWifi) {
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps?.transportInfo as? WifiInfo)?.ssid?.replace("\"", "")
            } else {
                null
            }
            netName = if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") "Wi-Fi: $ssid" else "Wi-Fi"
        } else if (isEthernet) {
            netName = "Ethernet"
        }
    } catch (_: Exception) {}

    val ip = getLocalIpAddress() ?: "127.0.0.1"

    return SystemStats(
        storageTotalGb = storageTotalGb,
        storageFreeGb = storageFreeGb,
        storageUsedPercent = storageUsedPercent,
        ramTotalGb = ramTotalGb,
        ramAvailGb = ramAvailGb,
        ramUsedPercent = ramUsedPercent,
        isWifi = isWifi,
        isEthernet = isEthernet,
        networkName = netName,
        ipAddress = ip,
    )
}

private fun getLocalIpAddress(): String? {
    try {
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (_: Exception) {}
    return null
}
