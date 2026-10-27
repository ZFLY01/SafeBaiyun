package cn.huacheng.safebaiyun.unlock

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cn.huacheng.safebaiyun.util.ContextHolder
import cn.huacheng.safebaiyun.util.LockBiz
import cn.huacheng.safebaiyun.util.showToast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
object UnlockRepo {

    private const val MAGIC_SERVICE = "14839ac4-7d7e-415c-9a42-167340cf2339"

    private const val SCAN_TIMEOUT_MS = 10_000L

    private lateinit var gatt: BluetoothGatt

    private lateinit var readableCharacteristic: BluetoothGattCharacteristic

    private lateinit var writeableCharacteristic: BluetoothGattCharacteristic

    /** 当前正在开的门禁配置 */
    private lateinit var activeProfile: LockProfile

    /** 实际用于加密的 MAC（扫描匹配后会更新为设备真实地址） */
    private lateinit var actualMac: String

    private var autoDisconnectJob: Job? = null

    private var scanTimeoutJob: Job? = null

    private val logList = mutableListOf("Hello World")
    val logFlow: MutableStateFlow<List<String>> = MutableStateFlow(logList.toList())

    fun unlock() {
        val bluetoothAdapter =
            (ContextHolder.get()
                .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        val profile = DataRepo.current()
        if (profile == null) {
            showToast("请先添加门禁")
            return
        }
        if (profile.key.isBlank()) {
            showToast("请填写密钥 Key")
            return
        }
        activeProfile = profile

        when {
            // 1) 有完整 MAC，直接连接（旧逻辑）
            BluetoothAdapter.checkBluetoothAddress(profile.mac) -> {
                actualMac = profile.mac
                connect(bluetoothAdapter.getRemoteDevice(profile.mac))
                scheduleAutoDisconnect()
            }
            // 2) 没有 MAC 但有蓝牙名，扫描匹配
            profile.bluetoothName.isNotBlank() -> {
                if (!hasScanPermission()) {
                    showToast("请先授予蓝牙扫描和定位权限")
                    return
                }
                scanAndConnect(bluetoothAdapter, profile)
            }
            else -> showToast("请填写 MAC 或蓝牙名")
        }
    }

    /**
     * 扫描 BLE 设备，按蓝牙广播名（bluetoothName）匹配，找到后用真实地址连接。
     */
    private fun scanAndConnect(bluetoothAdapter: BluetoothAdapter, profile: LockProfile) {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            showToast("无法启动蓝牙扫描，请打开蓝牙")
            return
        }

        val target = profile.bluetoothName.trim().uppercase()
        log("开始扫描蓝牙名 $target")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = device.name
                    ?: result.scanRecord?.deviceName
                    ?: return
                if (name.uppercase() == target) {
                    finishScan()
                    actualMac = device.address
                    log("扫描匹配成功，真实地址 $actualMac")
                    showToast("找到门禁，开始连接")
                    connect(device)
                    scheduleAutoDisconnect()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                finishScan()
                showToast("蓝牙扫描失败，请打开定位后重试")
            }
        }

        scanTimeoutJob = GlobalScope.launch {
            delay(SCAN_TIMEOUT_MS)
            runCatching { scanner.stopScan(callback) }
            if (isActive) {
                showToast("未找到门禁，请靠近门锁后重试")
            }
        }

        runCatching { scanner.startScan(callback) }
            .onFailure { showToast("启动扫描失败，请检查定位权限") }
    }

    private fun finishScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
    }

    private fun connect(remoteDevice: BluetoothDevice) {
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            remoteDevice.connectGatt(
                ContextHolder.get(),
                false,
                gattCallback
            )
        }

        log("尝试连接蓝牙 ${this::gatt.isInitialized}")
    }

    private fun scheduleAutoDisconnect() {
        autoDisconnectJob?.cancel()
        autoDisconnectJob = GlobalScope.launch {
            delay(10000)
            if (isActive) {
                log("10s超时，自动断开链接")
                if (this@UnlockRepo::gatt.isInitialized) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            log("连接状态改变 status$status,newState$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                autoDisconnectJob?.cancel()
                log("开始搜索服务")
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            log("搜索服务成功 $status")
            log("搜索到以下服务：${gatt?.services?.map { it.uuid }?.joinToString(",")}")
            handleService(gatt?.services?.find { it.uuid.toString() == MAGIC_SERVICE })
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            //android13以上走这里
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            //android12及以下走这里
            val value = characteristic?.value ?: return
            log("特征码读取回调 $status,${value.size}")
            handleCharacteristicWrite(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            log("特征写入回调")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                showToast("开门成功")
            } else {
                showToast("密钥写入失败")
            }
            gatt?.close()
        }
    }

    /**
     * 找到对应的characteristic
     */
    private fun handleService(service: BluetoothGattService?) {

        if (service == null) {
            return
        }

        log("开始处理服务，共${service.characteristics.size}个特征")
        val propCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        service.characteristics?.forEach {
            log("特征${it.uuid},prop:${it.properties}")
            val properties = it.properties
            if ((properties and 2) != 0) {
                readableCharacteristic = it
            }
            if ((properties and 8) != 0) {
                writeableCharacteristic = it
            }
            if ((properties and 16) != 0) {
                propCharacteristics.add(it)
            }
            if ((properties and 32) != 0) {
                propCharacteristics.add(it)
            }
        }

        handleCharacteristics(propCharacteristics)
    }

    private fun handleCharacteristics(propCharacteristics: MutableList<BluetoothGattCharacteristic>) {
        log("开始处理特征,写入对应数据")
        propCharacteristics.forEach { characteristic ->
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.descriptors.forEach {
                if ((characteristic.properties and 16) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else if ((characteristic.properties and 32) != 0) {
                    characteristic.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }

                gatt.writeDescriptor(it)
            }
        }

        val result = gatt.readCharacteristic(readableCharacteristic)
        log("特征写入结果 $result")
    }

    private fun handleCharacteristicWrite(value: ByteArray) {
        log("开始写入密钥")
        val key = LockBiz.encryptData(
            value,
            LockBiz.hexToByteArray(actualMac),
            activeProfile.key
        )
        log(key.joinToString())
        writeableCharacteristic.setValue(key)
        writeableCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = gatt.writeCharacteristic(writeableCharacteristic)
        log("密钥写入结果 $result")

    }

    /**
     * 扫描所需权限：Android 12+ 用 BLUETOOTH_SCAN；低版本用定位权限。
     */
    private fun hasScanPermission() {
        val context = ContextHolder.get()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun log(msg: String) {
        println(msg)
    }
}
