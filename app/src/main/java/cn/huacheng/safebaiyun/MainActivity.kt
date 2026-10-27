package cn.huacheng.safebaiyun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.huacheng.safebaiyun.compose.HelpView
import cn.huacheng.safebaiyun.compose.MainView
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeBaiyunTheme {
                val navController = rememberNavController()
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                    ) {
                        composable("main", enterTransition = {
                            slideIn {
                                IntOffset(-it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(-it.width, 0)
                            }
                        }) {
                            MainView(navController)
                        }

                        composable("helper", enterTransition = {
                            slideIn {
                                IntOffset(it.width, 0)
                            }
                        }, exitTransition = {
                            slideOut {
                                IntOffset(it.width, 0)
                            }
                        }) {
                            HelpView(navController)
                        }
                    }
                }
            }
        }
        handleUnlockIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUnlockIntent(intent)
    }

    /**
     * 处理 NFC 标签唤起的深链：safebaiyun://unlock?mac=xx
     * 选中对应门禁并立即触发蓝牙开门。
     */
    private fun handleUnlockIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "safebaiyun" || data.host != "unlock") return

        val mac = data.getQueryParameter("mac").orEmpty()
        if (mac.isBlank()) {
            showToast("标签门禁信息缺失")
            return
        }

        val index = DataRepo.findIndexByMac(mac)
        if (index < 0) {
            showToast("未找到该门禁，请先在App中添加")
            return
        }

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasPermission) {
            showToast("请先授予蓝牙权限")
            return
        }

        DataRepo.select(index)
        showToast("NFC触发，开始解锁门禁")
        UnlockRepo.unlock()
    }
}
