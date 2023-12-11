/*
 * @(#) Extension.kt
 *
 * resource-ref  Library to manage Resource references using URI and JSON Pointer
 * Copyright (c) 2023 Peter Wall
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

import io.kjson.JSONArray
import io.kjson.JSONObject
import io.kjson.JSONValue
import io.kjson.pointer.checkIndex
import io.kjson.pointer.checkName
import io.kjson.pointer.child
import io.kjson.pointer.hasChild
import io.kjson.pointer.optionalBoolean
import io.kjson.pointer.optionalChild
import io.kjson.pointer.optionalDecimal
import io.kjson.pointer.optionalInt
import io.kjson.pointer.optionalLong
import io.kjson.pointer.optionalString
import java.math.BigDecimal

inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.ifPresent(name: String, block: ResourceRef<T>.(T) -> Unit) {
    if (ref.hasChild<T>(name))
        child<T>(name).let { it.block(it.node) }
}

inline fun <reified T : JSONValue?, R : Any> ResourceRef<JSONObject>.map(name: String, block: ResourceRef<T>.(T) -> R):
        R = child<T>(name).let { it.block(it.node) }

fun ResourceRef<JSONObject>.optionalString(name: String): String? = ref.optionalString(name)

fun ResourceRef<JSONObject>.optionalBoolean(name: String): Boolean? = ref.optionalBoolean(name)

fun ResourceRef<JSONObject>.optionalInt(name: String): Int? = ref.optionalInt(name)

fun ResourceRef<JSONObject>.optionalLong(name: String): Long? = ref.optionalLong(name)

fun ResourceRef<JSONObject>.optionalDecimal(name: String): BigDecimal? = ref.optionalDecimal(name)

inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.optionalChild(name: String): ResourceRef<T>? =
    ref.optionalChild<T>(name)?.let { ResourceRef(resource, it) }

inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.child(name: String): ResourceRef<T> =
    ResourceRef(resource, ref.child<T>(name))

inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.child(index: Int): ResourceRef<T> =
    ResourceRef(resource, ref.child<T>(index))

fun ResourceRef<JSONObject>.untypedChild(name: String): ResourceRef<JSONValue?> {
    ref.checkName(name)
    return ResourceRef(
        resource = resource,
        ref = ref.createChildRef(name, node[name]),
    )
}

fun ResourceRef<JSONArray>.untypedChild(index: Int): ResourceRef<JSONValue?> {
    ref.checkIndex(index)
    return ResourceRef(
        resource = resource,
        ref = ref.createChildRef(index.toString(), node[index]),
    )
}

inline fun <reified T : JSONValue?> ResourceRef<JSONObject>.hasChild(name: String): Boolean =
    ref.node.containsKey(name) && ref.node[name] is T

inline fun <reified T : JSONValue?> ResourceRef<JSONArray>.hasChild(index: Int): Boolean =
    index >= 0 && index < ref.node.size && node[index] is T
