/*
 * @(#) ResourceRef.kt
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

import io.kjson.JSON.typeError
import io.kjson.JSONObject
import io.kjson.JSONStructure
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointer
import io.kjson.pointer.JSONRef
import io.kjson.pointer.JSONRef.Companion.refClassName
import kotlin.reflect.typeOf

class ResourceRef<out J : JSONValue?>(
    val resource: Resource<JSONObject>,
    val ref: JSONRef<J>,
) {

    val node: J
        get() = ref.node

    val pointer: JSONPointer
        get() = ref.pointer

    inline fun <reified T : JSONStructure<*>> parent(): ResourceRef<T> = parent { parentNode ->
        if (parentNode !is T)
            parentNode.typeError(typeOf<T>().refClassName(), ref.pointer, nodeName = "Parent")
        parentNode
    }

    fun <T : JSONStructure<*>> parent(checkType: (JSONValue?) -> T): ResourceRef<T> = ResourceRef(
        resource = resource,
        ref = ref.parent(checkType),
    )

    inline fun <reified T : JSONValue?> createTypedChildRef(token: String, targetNode: JSONValue?): ResourceRef<T> =
        ResourceRef(
            resource = resource,
            ref = ref.createTypedChildRef(token, targetNode),
        )

    fun <T : JSONValue?> createChildRef(token: String, targetNode: T): ResourceRef<T> = ResourceRef(
        resource = resource,
        ref = ref.createChildRef(token, targetNode),
    )

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : JSONValue?> asRef(nodeName: String = "Node"): ResourceRef<T> = if (ref.node is T)
        this as ResourceRef<T>
    else
        ref.node.typeError(typeOf<T>().refClassName(), pointer, nodeName)

    inline fun <reified T : JSONValue?> isRef(): Boolean = ref.node is T

    override fun equals(other: Any?): Boolean = this === other ||
            other is ResourceRef<*> && resource == other.resource && ref == other.ref

    override fun hashCode(): Int = resource.hashCode() xor ref.hashCode()

    override fun toString(): String = "${resource.resourceURL}#${ref.pointer}"

    fun resolve(relativeRef: String): ResourceRef<JSONObject> {
        val hashIndex = relativeRef.indexOf('#')
        return when {
            hashIndex < 0 -> {
                val target = resource.resolve(relativeRef)
                val json = target.load()
                ResourceRef(
                    resource = target,
                    ref = JSONRef(json),
                )
            }
            hashIndex == 0 -> {
                ResourceRef(
                    resource = resource,
                    ref = JSONRef.of(ref.base, JSONPointer.fromURIFragment(relativeRef.substring(1))),
                )
            }
            else -> {
                val target = resource.resolve(relativeRef.substring(0, hashIndex))
                val json = target.load()
                ResourceRef(
                    resource = target,
                    ref = JSONRef.of(json, JSONPointer.fromURIFragment(relativeRef.substring(hashIndex + 1))),
                )
            }
        }

    }

}
