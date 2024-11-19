/*
 * @(#) RefResourceLoaderTest.kt
 *
 * resource-ref  Library to manage Resource references using URI and JSON Pointer
 * Copyright (c) 2024 Peter Wall
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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.expect
import java.io.File
import java.io.FileWriter
import java.net.URI
import io.kjson.JSON.asInt
import io.kjson.JSONObject
import io.kjson.resource.RefResourceLoader.Companion.looksLikeYAML
import io.kjson.resource.RefResourceLoader.Companion.withFragment

class RefResourceLoaderTest {

    @Test fun `should load using cache`() {
        val json111 = JSONObject.build {
            add("value", 111)
        }
        val json999 = JSONObject.build {
            add("value", 999)
        }
        val dirName = "temp"
        val dirFile = File(dirName)
        val fileName = "temp1.json"
        try {
            dirFile.mkdir()
            val file1 = File(dirFile, fileName)
            // write file with value 111
            FileWriter(file1).use {
                json111.appendTo(it)
            }
            // read file and check value is 111
            val refResourceLoader = RefResourceLoader()
            with(refResourceLoader.load("$dirName/$fileName")) {
                assertIs<JSONObject>(this)
                expect(111) { this["value"].asInt }
            }
            // now write file with 999
            FileWriter(file1).use {
                json999.appendTo(it)
            }
            // read file and check value is still 111 because json was cached
            with(refResourceLoader.load("$dirName/$fileName")) {
                assertIs<JSONObject>(this)
                expect(111) { this["value"].asInt }
            }
            // clear cache
            refResourceLoader.clearCache()
            // read file and check value is now 999
            with(refResourceLoader.load("$dirName/$fileName")) {
                assertIs<JSONObject>(this)
                expect(999) { this["value"].asInt }
            }
        }
        finally {
            File(dirFile, fileName).delete()
            dirFile.delete()
        }
    }

    @Test fun `should add and remove cache entries`() {
        val json111 = JSONObject.build {
            add("value", 111)
        }
        val json222 = JSONObject.build {
            add("value", 222)
        }
        val dirName = "temp"
        val dirFile = File(dirName)
        val fileName = "temp1.json"
        try {
            dirFile.mkdir()
            val file1 = File(dirFile, fileName)
            // write file with value 111
            FileWriter(file1).use {
                json111.appendTo(it)
            }
            // read file and check value is 111
            val refResourceLoader = RefResourceLoader()
            with(refResourceLoader.load("$dirName/$fileName")) {
                assertIs<JSONObject>(this)
                expect(111) { this["value"].asInt }
            }
            // add a dummy entry to the cache
            val resource = refResourceLoader.resource(File("unreal"))
            refResourceLoader.addToCache(resource.resourceURL.toString(), json222)
            // read nonexistent file and check value is 222
            with(refResourceLoader.load("unreal")) {
                assertIs<JSONObject>(this)
                expect(222) { this["value"].asInt }
            }
            // remove cache entry and check read fails
            refResourceLoader.removeFromCache(resource.resourceURL.toString())
            assertFailsWith<ResourceNotFoundException> { refResourceLoader.load("unreal") }.let {
                it.message.let { m ->
                    assertNotNull(m)
                    assertTrue(m.endsWith("unreal"))
                }
            }
        }
        finally {
            File(dirFile, fileName).delete()
            dirFile.delete()
        }
    }

    @Test fun `should identify resource as YAML from MIME type`() {
        assertTrue(looksLikeYAML("file.any", "application/yaml"))
        assertTrue(looksLikeYAML("file.any", "application/config+yaml"))
        assertTrue(looksLikeYAML("file.json", "text/yaml"))
    }

    @Test fun `should identify resource as YAML from filename extension`() {
        assertTrue(looksLikeYAML("file.yaml", "text/string"))
        assertTrue(looksLikeYAML("file.yml", null))
        assertFalse(looksLikeYAML("file.yml", "application/json"))
    }

    @Test fun `should identify resource as JSON by default`() {
        assertFalse(looksLikeYAML("file.txt", "text/string"))
        assertFalse(looksLikeYAML("file.txt", null))
        assertFalse(looksLikeYAML("file.json", "application/json"))
    }

    @Test fun `should replace or remove fragment from URI`() {
        val uri = URI("https://example.com/path#frag")
        expect("https://example.com/path#new") { uri.withFragment("new").toString() }
        expect("https://example.com/path#") { uri.withFragment("").toString() }
        expect("https://example.com/path") { uri.withFragment(null).toString() }
    }

    @Test fun `should add fragment to URI`() {
        val uri = URI("https://example.com/path")
        expect("https://example.com/path#new") { uri.withFragment("new").toString() }
        expect("https://example.com/path") { uri.withFragment(null).toString() }
    }

}
