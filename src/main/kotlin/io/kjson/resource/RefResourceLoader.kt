/*
 * @(#) RefResourceLoader.kt
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

import java.net.URI

import io.kjson.JSON
import io.kjson.JSON.asObject
import io.kjson.JSON.asStringOrNull
import io.kjson.JSONObject
import io.kjson.yaml.YAML

/**
 * A [ResourceLoader] to read structured JSON or YAML resources, allowing them to be used in conjunction with the
 * [ResourceRef] class.
 *
 * @author  Peter Wall
 */
class RefResourceLoader : ResourceLoader<JSONObject>() {

    private val resourceCache = mutableMapOf<String, JSONObject>()

    override fun load(rd: ResourceDescriptor): JSONObject {
        val urlString = rd.url.toString()
        resourceCache[urlString]?.let { return it }
        return rd.getReader().use {
            if (looksLikeYAML(urlString, rd.mimeType))
                YAML.parse(it).rootNode
            else
                JSON.parse(it.readText())
        }.asObject.also {
            resourceCache[urlString] = it
            it["\$id"].asStringOrNull?.let { id ->
                resourceCache[URI(id).withFragment(null).toString()] = it
            }
        }
    }

    private fun looksLikeYAML(urlString: String, mimeType: String?): Boolean {
        mimeType?.let {
            if (it.contains("yaml", ignoreCase = true) || it.contains("yml", ignoreCase = true))
                return true
        }
        return urlString.endsWith(".yaml", ignoreCase = true) || urlString.endsWith(".yml", ignoreCase = true)
    }

    companion object {

        fun URI.withFragment(newFragment: String?): URI = when {
            fragment == newFragment -> this
            isOpaque -> URI(scheme, schemeSpecificPart, newFragment)
            else -> URI(scheme, authority, path, query, newFragment)
        }

    }

}
