package com.bitsycore.konfig.types

import kotlin.reflect.KClass
import kotlin.reflect.KType

/** A serializable snapshot of a DSL type. Never retain KType/KClass in the DSL graph. */
@PublishedApi
internal data class FieldValueType(
    val name: String,
    val arguments: List<FieldValueType> = emptyList(),
    val nullable: Boolean = false,
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
                else -> klass.simpleName
            }
            require(name in supportedNames) { "konfig: unsupported field type '$type'" }
            return FieldValueType(name!!, type.arguments.map {
                it.type?.let(::from) ?: FieldValueType("Any", nullable = true)
            }, type.isMarkedNullable)
        }

        private val supportedNames = setOf(
            "String", "Boolean", "Byte", "Short", "Char", "Int", "Long", "Float", "Double", "Any",
            "List", "Map", "Array", "BooleanArray", "ByteArray", "ShortArray", "CharArray",
            "IntArray", "LongArray", "FloatArray", "DoubleArray",
        )
    }
}
