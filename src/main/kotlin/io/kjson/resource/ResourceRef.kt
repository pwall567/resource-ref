/*
 * @(#) ResourceRef.kt
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

import kotlin.reflect.typeOf

import java.net.URL

import io.kjson.JSON.typeError
import io.kjson.JSONObject
import io.kjson.JSONStructure
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointer
import io.kjson.pointer.JSONRef
import io.kjson.pointer.JSONRef.Companion.refClassName

/**
 * A Resource Reference, combining a Resource (as described by a [URL]) and a [JSONRef] pointing to a specific location
 * within the Resource.
 *
 * @author  Peter Wall
 */
class ResourceRef<out J : JSONValue?>(
    val resource: Resource<JSONObject>,
    val ref: JSONRef<J>,
) {

    val resourceURL: URL
        get() = resource.resourceURL

    val node: J
        get() = ref.node

    val pointer: JSONPointer
        get() = ref.pointer

    inline fun <reified T : JSONStructure<*>> parent(): ResourceRef<T> = parent { parentNode ->
        if (parentNode !is T)
            parentNode.typeError(typeOf<T>().refClassName(), ResourceRef(resource, ref.parent()), nodeName = "Parent")
        parentNode
    }

    fun <T : JSONStructure<*>> parent(checkType: (JSONValue?) -> T): ResourceRef<T> = ResourceRef(
        resource = resource,
        ref = ref.parent(checkType),
    )

    /**
     * Create a child reference, checking the type of the target node.
     */
    inline fun <reified T : JSONValue?> createTypedChildRef(token: String, targetNode: JSONValue?): ResourceRef<T> =
        if (targetNode is T)
            createChildRef(token, targetNode)
        else
            targetNode.typeError(typeOf<T>().refClassName(), createChildRef(token, targetNode), "Child")

    /**
     * Create a child reference.
     */
    fun <T : JSONValue?> createChildRef(token: String, targetNode: T): ResourceRef<T> = ResourceRef(
        resource = resource,
        ref = ref.createChildRef(token, targetNode),
    )

    /**
     * "Downcast" a reference to a particular type, or throw an exception if the target is not of that type.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : JSONValue?> asRef(nodeName: String = "Node"): ResourceRef<T> = if (ref.node is T)
        this as ResourceRef<T>
    else
        ref.node.typeError(typeOf<T>().refClassName(), this, nodeName)

    /**
     * Test whether reference refers to a nominated type.
     */
    inline fun <reified T : JSONValue?> isRef(): Boolean = ref.node is T

    /**
     * Resolve a relative reference in the form "resource#node", as is commonly used in references in (for example) JSON
     * Schema files.  The relative reference may include a resource reference to be resolved as specified by
     * [RFC-3986](https://www.rfc-editor.org/info/rfc3986), followed by an optional "`#`" sign and "fragment"
     * identifier.
     *
     * There are, in effect, three cases:
     * 1. A relative URI with no fragment; in this case the function will attempt to locate the resource identified by
     *    the relative URI, and return a `ResourceRef` pointing to the root of the object.
     * 2. A relative URI with a fragment; the function will attempt to locate the resource as above, and will then set
     *    the pointer within the resource to the node identified by the fragment.
     * 3. A fragment (with preceding "`#`" sign) only; the function will set the pointer to the node identified by the
     *    fragment in the current resource.
     */
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

    override fun equals(other: Any?): Boolean = this === other ||
            other is ResourceRef<*> && resource == other.resource && ref == other.ref

    override fun hashCode(): Int = resource.hashCode() xor ref.hashCode()

    override fun toString(): String {
        return "$resource#${ref.pointer}"
    }

}
