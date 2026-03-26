package com.zenaios.zenmobileuse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zenaios.zenmobileuse.ui.theme.ZenmobileuseTheme

class LockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZenmobileuseTheme {
                LockOverlayScreen(
                    onOpenZen = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!UsageLimitManager.isLocked(this)) {
            finish()
        }
    }
}

@Composable
private fun LockOverlayScreen(onOpenZen: () -> Unit) {
    BackHandler(enabled = true) {}
    val context = androidx.compose.ui.platform.LocalContext.current
    var usedMillis by remember { mutableLongStateOf(getDailyUsageStats(context).totalUsageTime) }
    val remaining = UsageLimitManager.getRemainingMillis(context, usedMillis)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("今日手机使用额度已超限", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(10.dp))
        Text("超出时长：${formatExceeded(remaining)}", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                usedMillis = getDailyUsageStats(context).totalUsageTime
                onOpenZen()
            }
        ) {
            Text("打开 zenA+")
        }
    }
}

private fun formatExceeded(remainingMillis: Long): String {
    val exceeded = kotlin.math.abs(remainingMillis) / 60_000
    return "${exceeded}分钟"
}
