package com.oliveyoung.ivmlite.shared.adapters.exposed

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table

/**
 * PostgreSQL JSONB 컬럼 타입
 *
 * Exposed는 JSONB 네이티브 미지원이므로 커스텀 ColumnType 사용.
 * String ↔ JSONB 자동 변환 (PGobject 기반).
 */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType() = "JSONB"

    override fun valueFromDB(value: Any): String {
        if (value is String) return value
        // PGobject에서 값 추출 (PostgreSQL JDBC)
        val clazz = value.javaClass
        return try {
            val getter = clazz.getMethod("getValue")
            getter.invoke(value) as? String ?: "{}"
        } catch (_: Exception) {
            value.toString()
        }
    }

    override fun notNullValueToDB(value: String): Any {
        // PGobject 생성 (PostgreSQL JDBC)
        val clazz = Class.forName("org.postgresql.util.PGobject")
        val pgObj = clazz.getDeclaredConstructor().newInstance()
        clazz.getMethod("setType", String::class.java).invoke(pgObj, "jsonb")
        clazz.getMethod("setValue", String::class.java).invoke(pgObj, value)
        return pgObj
    }
}

/**
 * JSONB 컬럼 정의 확장
 */
fun Table.jsonb(name: String): Column<String> =
    registerColumn(name, JsonbColumnType())

/**
 * PostgreSQL TEXT[] 배열 컬럼 타입
 */
class TextArrayColumnType : ColumnType<Array<String>>() {
    override fun sqlType() = "TEXT[]"

    override fun valueFromDB(value: Any): Array<String> = when (value) {
        is java.sql.Array -> {
            val arr = value.array
            when (arr) {
                is Array<*> -> arr.map { it?.toString() ?: "" }.toTypedArray()
                else -> emptyArray()
            }
        }
        is Array<*> -> arr(value)
        else -> emptyArray()
    }

    override fun notNullValueToDB(value: Array<String>): Any = value

    override fun nonNullValueToString(value: Array<String>): String {
        return "ARRAY[${value.joinToString(",") { "'${it.replace("'", "''")}'" }}]::text[]"
    }

    private fun arr(value: Array<*>): Array<String> =
        value.map { it?.toString() ?: "" }.toTypedArray()
}

/**
 * TEXT[] 배열 컬럼 정의 확장
 */
fun Table.textArray(name: String): Column<Array<String>> =
    registerColumn(name, TextArrayColumnType())

