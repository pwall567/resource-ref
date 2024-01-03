/*
 * @(#) Extension.kt
 *
 * resource-ref  Library to manage Resource references using URI and JSON Pointer
 * Copyright (c) 2023, 2024 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.kjson.resource

import java.math.BigDecimal

import io.kjson.JSON.typeError
import io.kjson.JSONArray
import io.kjson.JSONBoolean
import io.kjson.JSONDecimal
import io.kjson.JSONInt
import io.kjson.JSONLong
import io.kjson.JSONNumber
import io.kjson.JSONObject
import io.kjson.JSONPrimitive
import io.kjson.JSONString
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointerException
import io.kjson.pointer.JSONRef
import io.kjson.pointer.hasChild

/**
 * Conditionally execute if [JSONObject] referenced by `this` [ResourceRef] contains a member with the specified key and
 * the expected type.
 *
 * **NOTE:** this function will not throw an exception if the property is present but is of the wrong type, and it may
 * be removed from future releases.  To achieve the same effect with strong type checking, use:
 * ```
 *     ref.optionalChild<JSONObject>("name")?.let { doSomething(it) }
 * ```
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.ifPresent(name: String, block: ResourceRef<T>.(T) -> Unit) {
    if (ref.hasChild<T>(name))
        child<T>(name).let { it.block(it.node) }
}

/**
 * Map the values of the [JSONArray] referenced by `this` [ResourceRef] to an array of the primitive type for the
 * [JSONValue].
 */
inline fun <reified T : JSONPrimitive<R>, R : Any> ResourceRef<JSONArray>.map(): List<R> =
        List(ref.node.size) { index -> child<T>(index).ref.node.value }

/**
 * Map the values of the [JSONArray] referenced by `this` [ResourceRef] to an array of the target type, applying a
 * transformation to each item.
 */
inline fun <reified T : JSONValue?, R> ResourceRef<JSONArray>.map(transform: ResourceRef<T>.(Int) -> R): List<R> =
        List(ref.node.size) { index -> child<T>(index).transform(index) }

/**
 * Return `true` if any of the values of the [JSONArray] referenced by `this` [ResourceRef] satisfy a given predicate.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.any(predicate: ResourceRef<T>.(Int) -> Boolean) : Boolean =
        node.indices.any { child<T>(it).predicate(it) }

/**
 * Return `true` if all of the values of the [JSONArray] referenced by `this` [ResourceRef] satisfy a given predicate.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.all(predicate: ResourceRef<T>.(Int) -> Boolean) : Boolean =
        node.indices.all { child<T>(it).predicate(it) }

/**
 * Map the [JSONObject] property referenced by `this` [ResourceRef] and the specified key, using the provided mapping
 * function.
 */
@Deprecated("Confusing function name", ReplaceWith("child(name).apply(block)"))
inline fun <reified T : JSONValue?, R : Any> ResourceRef<JSONObject>.map(name: String, block: ResourceRef<T>.(T) -> R):
        R = child<T>(name).let { it.block(it.node) }

/**
 * Get a [String] property from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if the
 * property is not present (throws an exception if the property is present but is the wrong type).
 */
fun ResourceRef<JSONObject>.optionalString(name: String): String? = ref.node[name]?.let {
    when (it) {
        is JSONString -> it.value
        else -> it.typeError("String", untypedChild(name))
    }
}

/**
 * Get a [Boolean] property from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if the
 * property is not present (throws an exception if the property is present but is the wrong type).
 */
fun ResourceRef<JSONObject>.optionalBoolean(name: String): Boolean? = ref.node[name]?.let {
    when (it) {
        is JSONBoolean -> it.value
        else -> it.typeError("Boolean", untypedChild(name))
    }
}

/**
 * Get an [Int] property from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if the property
 * is not present (throws an exception if the property is present but is the wrong type).
 */
fun ResourceRef<JSONObject>.optionalInt(name: String): Int? = ref.node[name]?.let {
    when {
        it is JSONNumber && it.isInt() -> it.toInt()
        else -> it.typeError("Int", untypedChild(name))
    }
}

/**
 * Get a [Long] property from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if the property
 * is not present (throws an exception if the property is present but is not an [Int] or [Long]).
 */
fun ResourceRef<JSONObject>.optionalLong(name: String): Long? = ref.node[name]?.let {
    when {
        it is JSONNumber && it.isLong() -> it.toLong()
        else -> it.typeError("Long", untypedChild(name))
    }
}

/**
 * Get a [BigDecimal] property from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if the
 * property is not present (throws an exception if the property is present but is the wrong type).
 */
fun ResourceRef<JSONObject>.optionalDecimal(name: String): BigDecimal? = ref.node[name]?.let {
    when (it) {
        is JSONInt -> BigDecimal(it.value.toLong())
        is JSONLong -> BigDecimal(it.value)
        is JSONDecimal -> it.value
        else -> it.typeError("Decimal", untypedChild(name))
    }
}

/**
 * Get a child property [ResourceRef] from a [JSONObject] using `this` [ResourceRef] and the specified key, or `null` if
 * the property is not present (throws an exception if the property is present but is the wrong type).
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.optionalChild(name: String): ResourceRef<T>? =
    ref.node[name]?.let {
        createTypedChildRef(name, it)
    }

/**
 * Iterate over the members of the [JSONObject] referenced by `this` [ResourceRef].
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.forEachKey(block: ResourceRef<T>.(String) -> Unit) {
    ref.node.entries.forEach { createTypedChildRef<T>(it.key, it.value).block(it.key) }
}

/**
 * Iterate over the members of the [JSONArray] referenced by `this` [ResourceRef].
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.forEach(block: ResourceRef<T>.(Int) -> Unit) {
    ref.node.indices.forEach { createTypedChildRef<T>(it.toString(), node[it]).block(it) }
}

/**
 * Get the named child reference (strongly typed) from this [JSONObject] reference, using the implied child type.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.child(name: String): ResourceRef<T> {
    checkName(name)
    return createTypedChildRef(name, ref.node[name])
}

/**
 * Get the named child reference (strongly typed) from this [JSONArray] reference, using the implied child type.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.child(index: Int): ResourceRef<T> {
    checkIndex(index)
    return createTypedChildRef(index.toString(), ref.node[index])
}

/**
 * Get the named child reference (untyped) from this [JSONObject] reference.
 */
fun ResourceRef<JSONObject>.untypedChild(name: String): ResourceRef<JSONValue?> {
    checkName(name)
    return createChildRef(name, ref.node[name])
}

/**
 * Get the named child reference (untyped) from this [JSONArray] reference.
 */
fun ResourceRef<JSONArray>.untypedChild(index: Int): ResourceRef<JSONValue?> {
    checkIndex(index)
    return createChildRef(index.toString(), node[index])
}

/**
 * Test whether this [JSONObject] reference has the named child with the implied type.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.hasChild(name: String): Boolean =
    ref.node.containsKey(name) && ref.node[name] is T

/**
 * Test whether this [JSONArray] reference has a child at the given index with the implied type.
 */
inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.hasChild(index: Int): Boolean =
    index >= 0 && index < ref.node.size && node[index] is T

/**
 * Check that this [JSONObject] reference has the named child, and throw an exception if not.
 */
fun ResourceRef<JSONObject>.checkName(name: String) {
    if (!ref.node.containsKey(name))
        throw JSONPointerException("Node does not exist", "$this/$name")
}

/**
 * Check that this [JSONArray] reference has a child at the given index, and throw an exception if not.
 */
fun ResourceRef<JSONArray>.checkIndex(index: Int) {
    if (index !in ref.node.indices)
        throw JSONPointerException("Index not valid", "$this/$index")
}

/**
 * Load a [Resource] and create a reference.
 */
fun Resource<JSONObject>.ref(): ResourceRef<JSONObject> = ResourceRef(this, JSONRef(load()))

/** The value of the node. */
val <T> ResourceRef<JSONPrimitive<T>>.value: T
    get() = ref.node.value
