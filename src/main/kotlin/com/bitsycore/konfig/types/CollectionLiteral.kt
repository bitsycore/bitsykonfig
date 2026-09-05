package com.bitsycore.konfig.types

import java.lang.reflect.Array as JavaArray

/** Produces code only from supported values and validated type descriptors. */
internal object CollectionLiteral {
    private val primitives = setOf("Boolean", "Byte", "Short", "Char", "Int", "Long", "Float", "Double")

    fun type(type: FieldValueType): String {
        val element = type.arguments.singleOrNull()
        val base = if (type.name == "Array" && element != null && !element.nullable && element.name in primitives) {
            "${element.name}Array"
        } else {
            type.name + if (type.arguments.isEmpty()) "" else type.arguments.joinToString(", ", "<", ">", transform = ::type)
        }
        return base + if (type.nullable) "?" else ""
    }

    fun value(value: Any?, declared: FieldValueType): String {
        if (value == null) {
            require(declared.nullable) { "konfig: null is not valid for ${type(declared)}" }
            return "null"
        }
        if (declared.name == "Any") return dynamicValue(value)
        return when (declared.name) {
            "List" -> {
                require(value is List<*>) { "konfig: expected ${type(declared)}" }
                val element = declared.arguments.single()
                value.joinToString(", ", "listOf<${type(element)}>(", ")") { value(it, element) }
            }
            "Map" -> {
                require(value is Map<*, *>) { "konfig: expected ${type(declared)}" }
                val (key, item) = declared.arguments
                value.entries.joinToString(", ", "mapOf<${type(key)}, ${type(item)}>(", ")") {
                    "${value(it.key, key)} to ${value(it.value, item)}"
                }
            }
            "Array", "BooleanArray", "ByteArray", "ShortArray", "CharArray", "IntArray", "LongArray", "FloatArray", "DoubleArray" -> {
                require(value.javaClass.isArray) { "konfig: expected ${type(declared)}" }
                val element = if (declared.name == "Array") declared.arguments.single()
                    else FieldValueType(declared.name.removeSuffix("Array"))
                val primitive = !element.nullable && element.name in primitives
                val factory = if (primitive) "${element.name.replaceFirstChar { it.lowercase() }}ArrayOf"
                    else "arrayOf<${type(element)}>"
                (0 until JavaArray.getLength(value)).joinToString(", ", "$factory(", ")") {
                    value(JavaArray.get(value, it), element)
                }
            }
            else -> {
                require(value.javaClass.simpleName == declared.name ||
                    (declared.name == "Int" && value is Int) ||
                    (declared.name == "Char" && value is Char)) {
                    "konfig: expected ${type(declared)}, got ${value.javaClass.simpleName}"
                }
                scalar(value)
            }
        }
    }

    private fun dynamicValue(value: Any): String = when (value) {
        is List<*> -> value(value, FieldValueType("List", listOf(FieldValueType("Any", nullable = true))))
        is Map<*, *> -> value(value, FieldValueType("Map", List(2) { FieldValueType("Any", nullable = true) }))
        else -> if (value.javaClass.isArray) {
            val component = value.javaClass.componentType
            val element = if (component.isPrimitive) FieldValueType(when (component.name) {
                "int" -> "Int"
                "char" -> "Char"
                else -> component.name.replaceFirstChar { it.uppercase() }
            }) else FieldValueType("Any", nullable = true)
            value(value, FieldValueType("Array", listOf(element)))
        } else scalar(value)
    }

    private fun scalar(value: Any): String = when (value) {
        is String -> value.toKotlinStringLiteral()
        is Char -> "'${escape(value, charLiteral = true)}'"
        is Boolean, is Int -> value.toString()
        is Byte -> "($value).toByte()"
        is Short -> "($value).toShort()"
        is Long -> if (value == Long.MIN_VALUE) "Long.MIN_VALUE" else "${value}L"
        is Float -> when {
            value.isNaN() -> "Float.NaN"
            value == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
            value == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
            else -> "${value}f"
        }
        is Double -> when {
            value.isNaN() -> "Double.NaN"
            value == Double.POSITIVE_INFINITY -> "Double.POSITIVE_INFINITY"
            value == Double.NEGATIVE_INFINITY -> "Double.NEGATIVE_INFINITY"
            else -> value.toString()
        }
        else -> error("konfig: unsupported collection value type '${value.javaClass.name}'")
    }
}

internal fun String.toKotlinStringLiteral(): String =
    map { escape(it, charLiteral = false) }.joinToString("", "\"", "\"")

private fun escape(c: Char, charLiteral: Boolean): String = when (c) {
    '\\' -> "\\\\"
    '\"' -> if (charLiteral) "\"" else "\\\""
    '\'' -> if (charLiteral) "\\'" else "'"
    '\n' -> "\\n"
    '\r' -> "\\r"
    '\t' -> "\\t"
    '$' -> if (charLiteral) "$" else "\\$"
    else -> if (c.code < 32 || c.code == 127 || c.isSurrogate()) "\\u${c.code.toString(16).padStart(4, '0')}" else c.toString()
}
