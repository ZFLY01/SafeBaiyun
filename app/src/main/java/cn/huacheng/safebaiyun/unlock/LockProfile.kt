package cn.huacheng.safebaiyun.unlock

import kotlinx.serialization.Serializable

/**
 * 一套门禁配置：备注名称 + 蓝牙 MAC + 蓝牙广播名 + 加密密钥
 *
 * @param name          备注名称，如"家大门"、"单元门"
 * @param mac           门禁蓝牙模块的 MAC 地址（可留空，留空时按蓝牙名扫描匹配）
 * @param bluetoothName 门禁蓝牙广播名，如 "BY9F9970EFE"（官方小程序字段）
 * @param key           加密密钥（PRODUCT_KEY，十六进制字符串）
 */
@Serializable
data class LockProfile(
    val name: String = "",
    val mac: String = "",
    val bluetoothName: String = "",
    val key: String = ""
)
