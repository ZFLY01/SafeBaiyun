package cn.huacheng.safebaiyun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.LockProfile
import cn.huacheng.safebaiyun.unlock.UnlockRepo
import cn.huacheng.safebaiyun.util.showToast

/**
 * 手表（圆屏）专用开门界面：
 * 中间大按钮开门，顶部切换门禁，底部进入管理（增删改）。
 */
class WatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeBaiyunTheme {
                WatchScreen()
            }
        }
    }
}

@Composable
private fun WatchScreen() {
    var tick by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(checkBluetoothPermission()) }
    var showManage by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val profiles = remember(tick) { DataRepo.readProfiles() }
    val selected = remember(tick) { DataRepo.selectedIndex() }
    val current = profiles.getOrNull(selected)

    // 圆屏边缘不安全，内容内缩
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 26.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (current != null) {
                // 门禁切换
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = profiles.size > 1,
                        onClick = {
                            val prev = (selected - 1 + profiles.size) % profiles.size
                            DataRepo.select(prev)
                            tick++
                        },
                        modifier = Modifier.size(40.dp)
                    ) { Text(text = "‹", fontSize = 22.sp) }
                    Text(
                        text = current.name.ifBlank { "我的门禁" },
                        fontSize = 13.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(110.dp)
                    )
                    TextButton(
                        enabled = profiles.size > 1,
                        onClick = {
                            val next = (selected + 1) % profiles.size
                            DataRepo.select(next)
                            tick++
                        },
                        modifier = Modifier.size(40.dp)
                    ) { Text(text = "›", fontSize = 22.sp) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 大开门按钮
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasPermission) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable {
                            if (!hasPermission) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                                return@clickable
                            }
                            showToast("开始解锁")
                            UnlockRepo.unlock()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasPermission) "开门" else "授权",
                        color = if (hasPermission) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showManage = true }) {
                    Text(text = "管理", fontSize = 12.sp)
                }
            } else {
                Text(text = "还没有门禁", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showManage = true }) {
                    Text(text = "点此添加", fontSize = 14.sp)
                }
            }
        }
    }

    if (showManage) {
        WatchManageDialog(
            tick = tick,
            onDismiss = { showManage = false },
            onChanged = { tick++ }
        )
    }
}

private fun checkBluetoothPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val app = cn.huacheng.safebaiyun.util.ContextHolder.get()
    return app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * 手表上的门禁管理：列表选择 + 新增/编辑/删除。
 */
@Composable
private fun WatchManageDialog(
    tick: Int,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val profiles = remember(tick) { DataRepo.readProfiles() }
    val selected = remember(tick) { DataRepo.selectedIndex() }
    var editing by remember { mutableStateOf<Int?>(null) } // null=关闭；-1=新增；>=0=编辑
    var confirmDelete by remember { mutableStateOf<Int?>(null) }

    if (editing != null) {
        WatchEditDialog(
            profile = editing?.let { if (it >= 0) profiles[it] else LockProfile() }
                ?: LockProfile(),
            onDismiss = { editing = null },
            onConfirm = { profile ->
                val idx = editing!!
                if (idx >= 0) {
                    DataRepo.update(idx, profile)
                } else {
                    val newIndex = DataRepo.add(profile)
                    if (DataRepo.selectedIndex() == -1) DataRepo.select(newIndex)
                }
                editing = null
                onChanged()
            }
        )
        return
    }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(text = "删除门禁", fontSize = 14.sp) },
            text = {
                Text(
                    text = "确定删除「${profiles[confirmDelete!!].name.ifBlank { "未命名" } }」？",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DataRepo.delete(confirmDelete!!)
                    confirmDelete = null
                    onChanged()
                }) { Text(text = "删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(text = "取消") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "门禁管理", fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                profiles.forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                DataRepo.select(index)
                                onChanged()
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = (if (index == selected) "● " else "○ ") +
                                item.name.ifBlank { "未命名" },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editing = index }) {
                            Text(text = "改", fontSize = 12.sp)
                        }
                        TextButton(onClick = { confirmDelete = index }) {
                            Text(text = "删", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { editing = -1 }) { Text(text = "新增") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "完成") }
        }
    )
}

@Composable
private fun WatchEditDialog(
    profile: LockProfile,
    onDismiss: () -> Unit,
    onConfirm: (LockProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var mac by remember { mutableStateOf(profile.mac) }
    var key by remember { mutableStateOf(profile.key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "门禁信息", fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "名称", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(text = "MAC", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(text = "Key", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = mac.isNotBlank() && key.isNotBlank(),
                onClick = {
                    onConfirm(
                        LockProfile(
                            name = name.trim(),
                            mac = mac.trim(),
                            key = key.trim()
                        )
                    )
                }
            ) { Text(text = "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}
