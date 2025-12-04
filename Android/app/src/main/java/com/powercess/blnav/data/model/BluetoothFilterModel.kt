package com.powercess.blnav.data.model

/**
 * 蓝牙设备过滤规则数据模型
 *
 * 用于定义蓝牙设备的过滤条件
 *
 * @param id 过滤规则的唯一标识符
 * @param alias 规则的别名，用于UI显示
 * @param filterRule 过滤规则字符串（可以是设备名称、MAC地址等的匹配规则）
 * @param enableRegex 是否启用正则表达式匹配
 * @param filterType 过滤类型：WHITELIST(白名单) 或 BLACKLIST(黑名单)
 * @param isEnabled 是否启用当前规则
 * @param description 规则描述
 * @param createTime 规则创建时间戳
 * @param updateTime 规则最后更新时间戳
 */
data class BluetoothFilterModel(
    val id: String,
    val alias: String,
    val filterRule: String,
    val enableRegex: Boolean = false,
    val filterType: FilterType = FilterType.WHITELIST,
    val isEnabled: Boolean = true,
    val description: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) {
    /**
     * 过滤类型枚举
     */
    enum class FilterType {
        WHITELIST,  // 白名单：只允许匹配的设备
        BLACKLIST   // 黑名单：不允许匹配的设备
    }

    override fun toString(): String {
        return "过滤规则: $alias\n规则: $filterRule\n类型: ${filterType.name}\n启用正则: $enableRegex\n状态: ${if (isEnabled) "启用" else "禁用"}"
    }
}

