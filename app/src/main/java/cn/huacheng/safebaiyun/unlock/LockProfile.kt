package cn.huacheng.safebaiyun.unlock

import kotlinx.serialization.Serializable

/**
 * 一套门禁配置：备注名称 + 蓝牙 MAC + 加密密钥
 *
 * @param name 备注名称，如"家大门"、"单元门"
 * @param mac  门禁蓝牙模块的 MAC 地址
 * @param key  加密密钥（PRODUCT_KEY，十六进制字符串）
 */
@Serializable
data class LockProfile(
    val name: String = "",
    val mac: String = "",
    val key: String = ""
)
