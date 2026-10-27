package cn.huacheng.safebaiyun

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import cn.huacheng.safebaiyun.theme.SafeBaiyunTheme
import cn.huacheng.safebaiyun.util.showToast

/**
 * 把指定门禁的触发链接写入 NFC 标签：
 * 标签内容为 safebaiyun://unlock?mac=xx，手机解锁状态下碰一下即可唤起 App 自动开门。
 */
class NfcWriteActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MAC = "extra_mac"
        const val EXTRA_NAME = "extra_name"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var mac: String = ""
    private var doorName: String = ""

    private var status by mutableStateOf("等待靠近NFC标签…")
    private var written by mutableStateOf(false)

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        val result = writeTag(tag)
        runOnUiThread {
            if (result == null) {
                written = true
                status = "写入成功！以后用手机碰一下该标签即可开「$doorName」"
                showToast("NFC标签写入成功")
                nfcAdapter?.disableReaderMode(this)
            } else {
                status = "写入失败：$result，请重新靠近标签"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mac = intent.getStringExtra(EXTRA_MAC).orEmpty()
        doorName = intent.getStringExtra(EXTRA_NAME).orEmpty()

        if (mac.isBlank()) {
            showToast("门禁信息缺失")
            finish()
            return
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            SafeBaiyunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Text(
                            text = "写入 NFC 标签",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "目标门禁：${doorName.ifBlank { mac }}")
                        Spacer(modifier = Modifier.height(40.dp))
                        Text(text = status)
                        Spacer(modifier = Modifier.height(24.dp))
                        if (nfcAdapter == null) {
                            Text(text = "本机不支持 NFC，无法使用该功能")
                        } else {
                            Button(onClick = { finish() }) {
                                Text(text = if (written) "完成" else "取消")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            status = "NFC 未开启，请先开启 NFC 后返回"
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(this, readerCallback, flags, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /**
     * 写入 NDEF 链接；返回 null 表示成功，否则返回错误信息。
     */
    private fun writeTag(tag: Tag): String? {
        val uri = Uri.Builder()
            .scheme("safebaiyun")
            .authority("unlock")
            .appendQueryParameter("mac", mac)
            .build()
        val message = NdefMessage(arrayOf(NdefRecord.createUri(uri)))

        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return "标签只读"
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                null
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: return "标签类型不支持"
                formatable.connect()
                formatable.format(message)
                formatable.close()
                null
            }
        } catch (e: Exception) {
            e.message ?: "未知错误"
        }
    }
}
