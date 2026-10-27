package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cn.huacheng.safebaiyun.util.ContextHolder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 门禁配置仓库：支持保存多套门禁（名称 + MAC + Key），并记录当前选中的一套。
 *
 * 旧版本只保存单个 mac/key，首次读取时会自动迁移成名为"默认门禁"的一条记录。
 */
object DataRepo {

    private const val KEY_PROFILES = "profiles"
    private const val KEY_SELECTED = "selected"
    private const val LEGACY_MAC = "mac"
    private const val LEGACY_KEY = "key"

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        ContextHolder.get().getSharedPreferences("data", Context.MODE_PRIVATE)
    }

    /**
     * 读取全部门禁配置；首次读取时自动把旧版单条 mac/key 迁移成列表。
     */
    fun readProfiles(): List<LockProfile> {
        val raw = preferences.getString(KEY_PROFILES, null)
        if (!raw.isNullOrBlank()) {
            return runCatching { json.decodeFromString<List<LockProfile>>(raw) }
                .getOrDefault(emptyList())
        }
        // 兼容旧版本：把单条 mac/key 迁移成一条记录
        val mac = preferences.getString(LEGACY_MAC, "") ?: ""
        val key = preferences.getString(LEGACY_KEY, "") ?: ""
        if (mac.isBlank() && key.isBlank()) return emptyList()
        val migrated = listOf(LockProfile(name = "默认门禁", mac = mac, key = key))
        writeProfiles(migrated)
        return migrated
    }

    /**
     * 当前选中的门禁下标；没有任何配置时返回 -1。
     */
    fun selectedIndex(): Int {
        val profiles = readProfiles()
        if (profiles.isEmpty()) return -1
        val index = preferences.getInt(KEY_SELECTED, 0)
        return index.coerceIn(0, profiles.lastIndex)
    }

    /**
     * 当前选中的门禁；没有配置时返回 null。
     */
    fun current(): LockProfile? = readProfiles().getOrNull(selectedIndex())

    /**
     * 兼容旧接口：返回当前选中的 mac/key。
     */
    fun readData(): Pair<String, String> {
        val current = current() ?: return "" to ""
        return current.mac to current.key
    }

    /**
     * 切换当前门禁。
     */
    fun select(index: Int) {
        val profiles = readProfiles()
        if (index !in profiles.indices) return
        preferences.edit { putInt(KEY_SELECTED, index) }
    }

    /**
     * 按 MAC 查找门禁下标；找不到返回 -1。
     */
    fun findIndexByMac(mac: String): Int {
        return readProfiles().indexOfFirst { it.mac.equals(mac, ignoreCase = true) }
    }

    /**
     * 新增一套门禁，返回新增后的下标。
     */
    fun add(profile: LockProfile): Int {
        val profiles = readProfiles().toMutableList()
        profiles.add(profile)
        writeProfiles(profiles)
        return profiles.lastIndex
    }

    /**
     * 更新指定下标的门禁。
     */
    fun update(index: Int, profile: LockProfile) {
        val profiles = readProfiles().toMutableList()
        if (index !in profiles.indices) return
        profiles[index] = profile
        writeProfiles(profiles)
    }

    /**
     * 删除指定下标的门禁，并修正选中下标。
     */
    fun delete(index: Int) {
        val profiles = readProfiles().toMutableList()
        if (index !in profiles.indices) return
        profiles.removeAt(index)
        writeProfiles(profiles)
        if (profiles.isEmpty()) {
            preferences.edit { remove(KEY_SELECTED) }
        } else {
            val selected = preferences.getInt(KEY_SELECTED, 0)
            preferences.edit {
                putInt(KEY_SELECTED, selected.coerceIn(0, profiles.lastIndex))
            }
        }
    }

    private fun writeProfiles(profiles: List<LockProfile>) {
        preferences.edit {
            putString(KEY_PROFILES, json.encodeToString(profiles))
        }
    }
}
