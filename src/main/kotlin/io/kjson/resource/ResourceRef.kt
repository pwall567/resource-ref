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
import io.kjson.JSONStructure
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointer
import io.kjson.pointer.JSONPointerException
import io.kjson.pointer.JSONRef
import io.kjson.pointer.JSONRef.Companion.refClassName

/**
 * A Resource Reference, combining a Resource (as described by a [URL]) and a [JSONRef] pointing to a specific location
 * within the Resource.
 *
 * The functions of this class (and the extension functions declared elsewhere) largely mirror those of [JSONRef], but
 * it was not possible for this class to use [JSONRef] as a base class (and thereby inherit the functionality) because
 * several of the functions that would need to be specialised in the derived class are inline functions with reified
 * type parameters, and Kotlin does not allow such functions to be overridden.
 *
 * @author  Peter Wall
 */
class ResourceRef<out J : JSONValue?>(
    val resource: Resource<JSONValue?>,
    val ref: JSONRef<J>,
) {

    /** The URL of the [Resource] */
    val url: URL
        get() = resource.url

    /** The `node` of the [JSONRef] */
    val node: J
        get() = ref.node

    /** The `pointer` of the [JSONRef] */
    val pointer: JSONPointer
        get() = ref.pointer

    /**
     * Get the strongly-typed parent reference of this reference.
     */
    inline fun <reified T : JSONStructure<*>> parent(): ResourceRef<T> = parent { parentNode ->
        if (parentNode !is T)
            parentNode.typeError(typeOf<T>().refClassName(), ResourceRef(resource, ref.parent()), nodeName = "Parent")
        parentNode
    }

    /**
     * Get the parent reference of this reference (using a supplied checking function to confirm the type).
     */
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
    inline fun <reified JJ : JSONValue?> resolve(relativeRef: String): ResourceRef<JJ> {
        val hashIndex = relativeRef.indexOf('#')

        val resolvedResource: Resource<JSONValue?>
        val resolvedRef: JSONRef<JJ>

        when {
            hashIndex == 0 -> {
                resolvedResource = resource
                resolvedRef = createRef<JJ>(resource, ref.base, relativeRef.substring(1))
            }
            hashIndex < 0 -> {
                resolvedResource = resource.resolve(relativeRef)
                resolvedRef = JSONRef(resolvedResource.load()).asRef<JJ>()
            }
            else -> {
                resolvedResource = resource.resolve(relativeRef.substring(0, hashIndex))
                val resolvedJSON = resolvedResource.load()
                resolvedRef = createRef<JJ>(resolvedResource, resolvedJSON, relativeRef.substring(hashIndex + 1))
            } // TODO test the above
        }
        return ResourceRef(
            resource = resolvedResource,
            ref = resolvedRef,
        )
    }

    /**
     * Create the `JSONRef`, catching any [JSONPointerException] and re-throwing it as a [ResourceRefException].
     */
    inline fun <reified JJ : JSONValue?> createRef(
        resource: Resource<JSONValue?>,
        json: JSONValue?,
        uriFragment: String,
    ): JSONRef<JJ> = try {
        JSONRef.of<JJ>(json, JSONPointer.fromURIFragment(uriFragment))
    } catch (e: JSONPointerException) {
        throw ResourceRefException(
            text = e.text,
            resourceRef = e.pointer?.let {
                ResourceRef(resource, JSONRef.of(json, it))
            },
        ).withCause(e)
    }

    /**
     * Create a new reference with the current node as the base.
     */
    fun rebase(): ResourceRef<J> = ResourceRef(
        resource = resource,
        ref = ref.rebase()
    )

    override fun equals(other: Any?): Boolean = this === other ||
            other is ResourceRef<*> && resource == other.resource && ref == other.ref

    override fun hashCode(): Int = resource.hashCode() xor ref.hashCode()

    override fun toString(): String {
        return "$resource#${ref.pointer}"
    }

}
