package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents a dynamic, administrator-configurable custom regex filter rule
 * for parsing any new bank or MFS SMS format without hardcoding.
 */
data class CustomFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val senderPattern: String, // e.g. "MIDLANDBANK, 16234, MDB"
    val bodyRegex: String,     // regex pattern with capturing groups
    val amountGroup: Int = 1,
    val trxIdGroup: Int = 2,
    val senderGroup: Int = 3,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("senderPattern", senderPattern)
            put("bodyRegex", bodyRegex)
            put("amountGroup", amountGroup)
            put("trxIdGroup", trxIdGroup)
            put("senderGroup", senderGroup)
            put("enabled", enabled)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomFilterRule {
            return CustomFilterRule(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Custom Gateway"),
                senderPattern = json.optString("senderPattern", ""),
                bodyRegex = json.optString("bodyRegex", ""),
                amountGroup = json.optInt("amountGroup", 1),
                trxIdGroup = json.optInt("trxIdGroup", 2),
                senderGroup = json.optInt("senderGroup", 3),
                enabled = json.optBoolean("enabled", true)
            )
        }

        fun fromJsonList(jsonArrayStr: String): List<CustomFilterRule> {
            if (jsonArrayStr.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonArrayStr)
                val list = mutableListOf<CustomFilterRule>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun toJsonList(list: List<CustomFilterRule>): String {
            val array = JSONArray()
            for (rule in list) {
                array.put(rule.toJson())
            }
            return array.toString()
        }
    }
}
