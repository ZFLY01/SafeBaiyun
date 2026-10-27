package cn.huacheng.safebaiyun.compose

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.huacheng.safebaiyun.NfcWriteActivity
import cn.huacheng.safebaiyun.unlock.DataRepo
import cn.huacheng.safebaiyun.unlock.LockProfile

/**
 * 门禁管理 BottomSheet：列出全部已保存门禁，支持切换、新增、编辑、删除、写NFC标签。
 *
 * @param state    控制 sheet 显示状态
 * @param onChanged 数据发生变化后的回调（用于刷新主页的当前门禁显示）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorManageSheet(state: MutableState<Boolean>, onChanged: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(DataRepo.readProfiles()) }

    // editingIndex：-1 = 不显示编辑框；-2 = 新增；>=0 = 编辑对应下标
    var editingIndex by remember { mutableStateOf(-1) }
    var editingProfile by remember { mutableStateOf(LockProfile()) }

    fun refresh() {
        profiles = DataRepo.readProfiles()
        onChanged()
    }

    ModalBottomSheet(onDismissRequest = { state.value = false }) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "门禁管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "还没有保存门禁，点击下方按钮添加",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(profiles) { index, item ->
                        val selected = index == DataRepo.selectedIndex()
                        Column {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        DataRepo.select(index)
                                        refresh()
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.ifBlank { "未命名" },
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = item.mac,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    val intent = Intent(context, NfcWriteActivity::class.java)
                                    intent.putExtra(NfcWriteActivity.EXTRA_MAC, item.mac)
                                    intent.putExtra(
                                        NfcWriteActivity.EXTRA_NAME,
                                        item.name.ifBlank { item.mac }
                                    )
                                    context.startActivity(intent)
                                }) { Text("写NFC") }
                                TextButton(onClick = {
                                    editingIndex = index
                                    editingProfile = item
                                }) { Text("编辑") }
                                TextButton(onClick = {
                                    DataRepo.delete(index)
                                    refresh()
                                }) { Text("删除") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Button(
                onClick = {
                    editingIndex = -2
                    editingProfile = LockProfile()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(text = "新增门禁")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (editingIndex != -1) {
        ProfileEditDialog(
            profile = editingProfile,
            isAdd = editingIndex == -2,
            onDismiss = { editingIndex = -1 },
            onConfirm = { profile ->
                if (editingIndex == -2) {
                    val newIndex = DataRepo.add(profile)
                    if (DataRepo.selectedIndex() == -1) {
                        DataRepo.select(newIndex)
                    }
                } else {
                    DataRepo.update(editingIndex, profile)
                }
                editingIndex = -1
                refresh()
            }
        )
    }
}

/**
 * 新增/编辑门禁的对话框：名称、MAC、Key 三个字段。
 */
@Composable
private fun ProfileEditDialog(
    profile: LockProfile,
    isAdd: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LockProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var mac by remember { mutableStateOf(profile.mac) }
    var key by remember { mutableStateOf(profile.key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (isAdd) "新增门禁" else "编辑门禁") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "名称（如：家大门）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(text = "MAC 地址") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(text = "Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mac.isBlank() || key.isBlank()) return@TextButton
                onConfirm(
                    LockProfile(
                        name = name.trim(),
                        mac = mac.trim(),
                        key = key.trim()
                    )
                )
            }) { Text(text = "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        }
    )
}
