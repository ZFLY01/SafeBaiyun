package cn.huacheng.safebaiyun.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.huacheng.safebaiyun.R
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast

/**
 * 主页：顶部为当前门禁切换器，中间为开门按钮，右上角进入门禁管理。
 */
@Composable
fun MainView(navController: NavHostController) {

    val context = LocalContext.current

    val hasPermission = remember {
        mutableStateOf(false)
    }

    val showManageSheet = remember {
        mutableStateOf(false)
    }

    // 数据变化计数器，变化后重新读取当前门禁
    val refreshTick = remember {
        mutableIntStateOf(0)
    }

    SideEffect {
        hasPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    Column {
        MainTopBar(onEditClick = {
            showManageSheet.value = true
        }, onHelperClick = {
            navController.navigate("helper")
        })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DoorSwitcher(
                    tick = refreshTick.intValue,
                    onChanged = { refreshTick.intValue++ },
                    onManage = { showManageSheet.value = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (hasPermission.value) {
                    UnlockView()
                } else {
                    PermissionView(hasPermission)
                }
            }
        }
        if (showManageSheet.value) {
            DoorManageSheet(state = showManageSheet) {
                refreshTick.intValue++
            }
        }
    }

}

/**
 * 当前门禁下拉切换器：点击切换到其他已保存门禁，或进入门禁管理。
 */
@Composable
private fun DoorSwitcher(tick: Int, onChanged: () -> Unit, onManage: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val profiles = remember(tick) { DataRepo.readProfiles() }
    val selected = remember(tick) { DataRepo.selectedIndex() }
    val current = profiles.getOrNull(selected)

    Box {
        TextButton(onClick = {
            if (profiles.isEmpty()) {
                onManage()
            } else {
                expanded = true
            }
        }) {
            if (current == null) {
                Text(text = "未配置门禁，点此添加")
            } else {
                val label = current.name.ifBlank { current.mac }
                Text(text = "当前：$label  ▾")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(text = item.name.ifBlank { item.mac }) },
                    onClick = {
                        DataRepo.select(index)
                        expanded = false
                        onChanged()
                    },
                    trailingIcon = {
                        if (index == selected) Text(text = "✓")
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = "管理门禁…") },
                onClick = {
                    expanded = false
                    onManage()
                }
            )
        }
    }
}

@Composable
private fun UnlockView() {
    Button(onClick = {
        showToast("开始解锁门禁")
        UnlockRepo.unlock()
    }, modifier = Modifier.size(144.dp, 56.dp)) {
        Text(text = stringResource(id = R.string.unlock_door), fontSize = 18.sp)
    }
}

@Composable
private fun PermissionView(hasPermission: MutableState<Boolean>) {
    val context = LocalContext.current
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) {
            hasPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }

    Button(modifier = Modifier.size(144.dp, 56.dp),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }

        }) {
        Text(text = stringResource(id = R.string.request_permission), fontSize = 18.sp)

    }
}
