/*
 * @(#) ResourceRefTest.kt
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

import io.kjson.JSON.asInt
import io.kjson.JSONArray
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

import java.io.File
import java.net.URL

import io.kjson.JSONInt
import io.kjson.JSONNumber
import io.kjson.JSONObject
import io.kjson.JSONString
import io.kjson.JSONTypeException
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointer
import io.kjson.pointer.JSONPointerException
import io.kjson.pointer.JSONRef
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class ResourceRefTest {

    private val loader = RefResourceLoader()
    private val resource = loader.resource(File("src/test/resources/json/json1.json"))
    private val json = resource.load()
    private val resourceRef = ResourceRef(resource, JSONRef(json))

    @Test fun `should create ResourceRef`() {
        assertTrue(resourceRef.resourceURL.toString().endsWith("json/json1.json"))
        assertSame(json, resourceRef.node)
        expect(JSONPointer.root) { resourceRef.pointer }
        expect(JSONInt(123)) { resourceRef.child<JSONInt>("alpha").node }
    }

    @Test fun `should navigate to parent`() {
        val ref = ResourceRef(resource, JSONRef.of(json, JSONPointer("/substructure/one")))
        val parent = ref.parent<JSONObject>()
        expect(222) { parent.node["two"].asInt }
        expect(JSONPointer("/substructure")) { parent.pointer }
        val grandParent = parent.parent<JSONObject>()
        expect(123) { grandParent.node["alpha"].asInt }
        expect(JSONPointer.root) { grandParent.pointer }
    }

    @Test fun `should fail navigating to parent of root`() {
        assertFailsWith<JSONPointerException> { resourceRef.parent<JSONArray>() }.let {
            expect("Can't get parent of root JSON Pointer") { it.message }
        }
    }

    @Test fun `should fail navigating to parent of incorrect type`() {
        val ref = ResourceRef(resource, JSONRef.of(json, JSONPointer("/substructure/one")))
        assertFailsWith<JSONTypeException> { ref.parent<JSONArray>() }.let {
            expect("Parent") { it.nodeName }
            expect("JSONArray") { it.target }
            assertIs<JSONObject>(it.value)
            expect(resourceRef.untypedChild("substructure")) { it.key }
        }
    }

    @Test fun `should convert to ref of correct type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        val refInt = ref.asRef<JSONInt>()
        expect(123) { refInt.node.value }
    }

    @Test fun `should fail converting to ref of incorrect type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        assertFailsWith<JSONTypeException> { ref.asRef<JSONString>() }.let {
            expect("Node") { it.nodeName }
            expect("JSONString") { it.target }
            expect(JSONInt(123)) { it.value }
            expect(resourceRef.untypedChild("alpha")) { it.key }
        }
    }

    @Test fun `should convert to ref of nullable type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        val refInt = ref.asRef<JSONInt?>()
        expect(123) { refInt.node?.value }
    }

    @Test fun `should test for specified type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        assertTrue(ref.isRef<JSONInt>())
        assertTrue(ref.isRef<JSONNumber>())
        assertTrue(ref.isRef<JSONValue>())
        assertFalse(ref.isRef<JSONString>())
    }

    @Test fun `should test for specified type including nullable`() {
        val ref = resourceRef.child<JSONValue>("alpha")
        assertTrue(ref.isRef<JSONInt?>())
        assertTrue(ref.isRef<JSONNumber?>())
        assertTrue(ref.isRef<JSONValue?>())
        assertFalse(ref.isRef<JSONString?>())
    }

    @Test fun `should test for specified type with null value`() {
        val ref = resourceRef.child<JSONValue?>("phi")
        assertTrue(ref.isRef<JSONInt?>())
        assertTrue(ref.isRef<JSONNumber?>())
        assertTrue(ref.isRef<JSONValue?>())
        assertTrue(ref.isRef<JSONString?>())
    }

    @Test fun `should resolve relative reference within same resource`() {
        val ref1 = resourceRef.resolve("#/substructure")
        expect(JSONInt(222)) { ref1.node["two"] }
        val ref2 = ref1.resolve("#/complexArray/2")
        expect(JSONString("CCC")) { ref2.node["f1"] }
    }

    @Test fun `should resolve relative reference to another resource`() {
        val ref1 = resourceRef.resolve("json2.json")
        expect(JSONInt(12345)) { ref1.node["field1"] }
    }

    @Test fun `should resolve relative reference to specified node in another resource`() {
        val ref1 = resourceRef.resolve("json2.json#/field2")
        expect(JSONInt(10)) { ref1.node["sub1"] }
    }

    @Test fun `should resolve relative reference to specified node in an external resource`() {
        val ref1 = resourceRef.resolve("http://kjson.io/json/http/testhttp1.json#/properties/xxx")
        expect(JSONString("http://kjson.io/json/http/testhttp2.json#/\$defs/Def1")) { ref1.node["\$ref"] }
    }

    @Test fun `should simplify path in toString`() {
        expect("src/test/resources/json/json1.json#") { resourceRef.toString() }
        expect("src/test/resources/json/json1.json#/substructure") {
            resourceRef.child<JSONObject>("substructure").toString()
        }
        val resourceHTTP = loader.resource(URL("http://kjson.io/json/http/testhttp1.json"))
        val resourceRefHTTP = ResourceRef(resourceHTTP, JSONRef(resourceHTTP.load()))
        expect("http://kjson.io/json/http/testhttp1.json#") { resourceRefHTTP.toString() }
    }

}
