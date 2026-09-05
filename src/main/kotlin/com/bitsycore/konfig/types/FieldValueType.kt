package com.bitsycore.konfig.types

import kotlin.reflect.KClass
import kotlin.reflect.KType

/** A serializable snapshot of a DSL type. Never retain KType/KClass in the DSL graph. */
@PublishedApi
internal data class FieldValueType(
    val name: String,
    val arguments: List<FieldValueType> = emptyList(),
    val nullable: Boolean = false,
    val enumType: Boolean = false,
) {
    companion object {
        fun from(type: KType): FieldValueType {
            val klass = type.classifier as? KClass<*>
                ?: error("konfig: unsupported field type '$type'")
            val javaType = klass.java
            val name = when {
                javaType.isArray && !javaType.componentType.isPrimitive -> "Array"
                List::class.java.isAssignableFrom(javaType) -> "List"
                Map::class.java.isAssignableFrom(javaType) -> "Map"
                Set::class.java.isAssignableFrom(javaType) -> "Set"
                javaType.isEnum -> requireNotNull(klass.qualifiedName) { "konfig: enum types must have a qualified name" }
                else -> klass.simpleName
            }
            require(javaType.isEnum || name in supportedNames) { "konfig: unsupported field type '$type'" }
            if (javaType.isEnum) require(name!!.split('.').all { it.isValidKotlinIdentifier() }) {
                "konfig: enum type '$name' must have a Kotlin-compatible qualified name"
            }
            return FieldValueType(name!!, type.arguments.map {
                it.type?.let(::from) ?: FieldValueType("Any", nullable = true)
            }, type.isMarkedNullable, javaType.isEnum)
        }

        private val supportedNames = setOf(
            "String", "Boolean", "Byte", "Short", "Char", "Int", "Long", "Float", "Double", "Any",
            "Set", "UByte", "UShort", "UInt", "ULong",
            "List", "Map", "Array", "BooleanArray", "ByteArray", "ShortArray", "CharArray",
            "IntArray", "LongArray", "FloatArray", "DoubleArray",
        )
    }
}
