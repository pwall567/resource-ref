/*
 * @(#) ExtensionTest.kt
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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.expect
import kotlin.test.fail

import java.io.File
import java.math.BigDecimal

import io.kjson.JSONArray
import io.kjson.JSONBoolean
import io.kjson.JSONInt
import io.kjson.JSONNumber
import io.kjson.JSONObject
import io.kjson.JSONString
import io.kjson.JSONTypeException
import io.kjson.JSONValue
import io.kjson.JSON.asInt
import io.kjson.JSON.asString
import io.kjson.pointer.JSONRef

class ExtensionTest {

    private val loader = RefResourceLoader()
    private val resource = loader.resource(File("src/test/resources/json/json1.json"))
    private val resourceRef = ResourceRef(
        resource = resource,
        ref = JSONRef(resource.load()),
    )

    @Test fun `should conditionally execute code`() {
        resourceRef.ifPresent<JSONInt>("alpha") {
            expect(123) { it.value }
        }
        resourceRef.ifPresent<JSONInt>("omega") {
            fail("Should not get here")
        }
        resourceRef.ifPresent<JSONString>("alpha") {
            fail("Should not get here")
        }
    }

    @Test fun `should map contents of array to its primitive type`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        val array = ref.map<JSONInt, Int>()
        expect(listOf(1, 10, 100, 1000, 10000)) { array }
    }

    @Test fun `should fail when 'map' operation finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { ref.map<JSONInt, Int>() }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            expect(JSONString("hello")) { it.value }
            expect(ref.untypedChild(1)) { it.key }
        }
    }

    @Test fun `should map contents of array with transformation`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        val array = ref.map<JSONInt, Int> { value * (it + 1) }
        expect(listOf(1, 20, 300, 4000, 50000)) { array }
    }

    @Test fun `should fail when transforming 'map' operation finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { ref.map<JSONInt, Int> { value * (it + 1) } }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            expect(JSONString("hello")) { it.value }
            expect(ref.untypedChild(1)) { it.key }
        }
    }

    @Test fun `should map contents of array with complex transformation`() {
        val ref = resourceRef.child<JSONArray>("complexArray")
        val array = ref.map<JSONObject, String> { child<JSONString>("f1").value }
        expect(listOf("AAA", "BBB", "CCC", "DDD")) { array }
    }

    @Test fun `should test whether any array item matches predicate`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        assertTrue { ref.any<JSONInt> { value > 100 } }
        assertFalse { ref.any<JSONInt> { value < 0 } }
    }

    @Test fun `should fail when 'any' test finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { ref.any<JSONInt> { value > 3 } }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            expect(JSONString("hello")) { it.value }
            expect(ref.untypedChild(1)) { it.key }
        }
    }

    @Test fun `should test whether all array items match predicate`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        assertTrue { ref.all<JSONInt> { value > 0 } }
        assertFalse { ref.all<JSONInt> { value < 100 } }
    }

    @Test fun `should fail when 'all' test finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { ref.all<JSONInt> { value < 10 } }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            expect(JSONString("hello")) { it.value }
            expect(ref.untypedChild(1)) { it.key }
        }
    }

    @Suppress("DEPRECATION")
    @Test fun `should unconditionally map value`() {
        val beta = resourceRef.map<JSONInt, Int>("beta") {
            it.value
        }
        expect(456) { beta }
        assertFailsWith<ResourceRefException> {
            resourceRef.map<JSONInt, Int>("omega") { it.value }
        }.let {
            assertTrue(it.message.startsWith("Can't locate JSON property \"omega\""))
            assertTrue(it.message.endsWith("src/test/resources/json/json1.json#"))
            expect("Can't locate JSON property \"omega\"") { it.text }
            expect(resourceRef) { it.resourceRef }
        }
        val gamma: Int = resourceRef.map("gamma") { prop: JSONInt -> prop.value }
        expect(888) { gamma }
        // alternative, following deprecation of original function, using recommended replacement code
        val beta2 = resourceRef.child<JSONInt>("beta").value
        expect(456) { beta2 }
        assertFailsWith<ResourceRefException> {
            resourceRef.child<JSONInt>("omega").value
        }.let {
            assertTrue(it.message.startsWith("Can't locate JSON property \"omega\""))
            assertTrue(it.message.endsWith("src/test/resources/json/json1.json#"))
            expect("Can't locate JSON property \"omega\"") { it.text }
            expect(resourceRef) { it.resourceRef }
        }
    }

    @Test fun `should get optional String`() {
        expect("A string") { resourceRef.optionalString("delta") }
        assertNull(resourceRef.optionalString("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalString("alpha") }.let {
            expect("Node") { it.nodeName }
            expect("String") { it.target }
            expect(JSONInt(123)) { it.value }
            expect(resourceRef.untypedChild("alpha")) { it.key }
        }
    }

    @Test fun `should get optional Boolean`() {
        expect(true) { resourceRef.optionalBoolean("epsilon") }
        assertNull(resourceRef.optionalBoolean("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalBoolean("alpha") }.let {
            expect("Node") { it.nodeName }
            expect("Boolean") { it.target }
            expect(JSONInt(123)) { it.value }
            expect(resourceRef.untypedChild("alpha")) { it.key }
        }
    }

    @Test fun `should get optional Int`() {
        expect(123) { resourceRef.optionalInt("alpha") }
        assertNull(resourceRef.optionalInt("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalInt("delta") }.let {
            expect("Node") { it.nodeName }
            expect("Int") { it.target }
            expect(JSONString("A string")) { it.value }
            expect(resourceRef.untypedChild("delta")) { it.key }
        }
    }

    @Test fun `should get optional Long`() {
        expect(123456789123456789) { resourceRef.optionalLong("sigma") }
        expect(123) { resourceRef.optionalLong("alpha") }
        assertNull(resourceRef.optionalLong("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalLong("delta") }.let {
            expect("Node") { it.nodeName }
            expect("Long") { it.target }
            expect(JSONString("A string")) { it.value }
            expect(resourceRef.untypedChild("delta")) { it.key }
        }
    }

    @Test fun `should get optional Decimal`() {
        expect(BigDecimal("1.5")) { resourceRef.optionalDecimal("omicron") }
        expect(BigDecimal(123456789123456789)) { resourceRef.optionalDecimal("sigma") }
        expect(BigDecimal(123)) { resourceRef.optionalDecimal("alpha") }
        assertNull(resourceRef.optionalDecimal("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalDecimal("delta") }.let {
            expect("Node") { it.nodeName }
            expect("Decimal") { it.target }
            expect(JSONString("A string")) { it.value }
            expect(resourceRef.untypedChild("delta")) { it.key }
        }
    }

    @Test fun `should get optional child object`() {
        val childRef = resourceRef.optionalChild<JSONObject>("substructure")
        assertNotNull(childRef)
        expect(111) { childRef.node["one"].asInt }
        assertNull(resourceRef.optionalChild<JSONObject>("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalChild<JSONObject>("delta") }.let {
            expect("Child") { it.nodeName }
            expect("JSONObject") { it.target }
            expect(JSONString("A string")) { it.value }
            expect(resourceRef.untypedChild("delta")) { it.key }
        }
    }

    @Test fun `should get optional child array`() {
        val childRef = resourceRef.optionalChild<JSONArray>("powersOfTen")
        assertNotNull(childRef)
        expect(100) { childRef.node[2].asInt }
        assertNull(resourceRef.optionalChild<JSONArray>("omega"))
        assertFailsWith<JSONTypeException> { resourceRef.optionalChild<JSONArray>("delta") }.let {
            expect("Child") { it.nodeName }
            expect("JSONArray") { it.target }
            expect(JSONString("A string")) { it.value }
            expect(resourceRef.untypedChild("delta")) { it.key }
        }
    }

    @Test fun `should iterate over object`() {
        val ref = resourceRef.child<JSONObject>("substructure")
        val results = mutableListOf<Pair<String, Int>>()
        ref.forEachKey<JSONInt> {
            results.add(it to node.value)
        }
        expect(2) { results.size }
        expect("one" to 111) { results[0] }
        expect("two" to 222) { results[1] }
    }

    @Test fun `should iterate over array`() {
        val ref = resourceRef.child<JSONArray>("complexArray")
        val results = mutableListOf<Pair<Int, String>>()
        ref.forEach<JSONObject> {
            results.add(it to node["f1"].asString)
        }
        expect(0 to "AAA") { results[0] }
        expect(1 to "BBB") { results[1] }
        expect(2 to "CCC") { results[2] }
        expect(3 to "DDD") { results[3] }
    }

    @Test fun `should navigate to child of object`() {
        val ref = resourceRef.child<JSONInt>("alpha")
        expect(123) { ref.node.value }
        expect("$resourceRef/alpha") { ref.toString() }
    }

    @Test fun `should navigate to nullable child of object`() {
        val ref1 = resourceRef.child<JSONInt?>("alpha")
        expect(123) { ref1.node?.value }
        expect("$resourceRef/alpha") { ref1.toString() }
        val ref2 = resourceRef.child<JSONInt?>("phi")
        assertNull(ref2.node)
        expect("$resourceRef/phi") { ref2.toString() }
    }

    @Test fun `should fail navigating to child of object of incorrect type`() {
        assertFailsWith<JSONTypeException> { resourceRef.child<JSONString>("alpha") }.let {
            expect("Child") { it.nodeName }
            expect("JSONString") { it.target }
            expect(JSONInt(123)) { it.value }
            expect(resourceRef.untypedChild("alpha")) { it.key }
        }
    }

    @Test fun `should fail navigating to null child of object of non-nullable type`() {
        assertFailsWith<JSONTypeException> { resourceRef.child<JSONInt>("phi") }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            assertNull(it.value)
            expect(resourceRef.untypedChild("phi")) { it.key }
        }
    }

    @Test fun `should fail navigating to child of object of incorrect nullable type`() {
        assertFailsWith<JSONTypeException> { resourceRef.child<JSONString?>("alpha") }.let {
            expect("Child") { it.nodeName }
            expect("JSONString?") { it.target }
            expect(JSONInt(123)) { it.value }
            expect(resourceRef.untypedChild("alpha")) { it.key }
        }
    }

    @Test fun `should navigate to child of array`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        val refInt = refArray.child<JSONInt>(0)
        expect(1) { refInt.node.value }
        expect("$resourceRef/heterogeneousArray/0") { refInt.toString() }
    }

    @Test fun `should navigate to nullable child of array`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        val refInt1 = refArray.child<JSONInt?>(0)
        expect(1) { refInt1.node?.value }
        expect("$resourceRef/heterogeneousArray/0") { refInt1.toString() }
        val refInt2 = refArray.child<JSONInt?>(3)
        assertNull(refInt2.node)
        expect("$resourceRef/heterogeneousArray/3") { refInt2.toString() }
    }

    @Test fun `should fail navigating to child of array of incorrect type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { refArray.child<JSONString>(0) }.let {
            expect("Child") { it.nodeName }
            expect("JSONString") { it.target }
            expect(JSONInt(1)) { it.value }
            expect(refArray.untypedChild(0)) { it.key }
        }
    }

    @Test fun `should fail navigating to null child of array of non-nullable type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { refArray.child<JSONInt>(3) }.let {
            expect("Child") { it.nodeName }
            expect("JSONInt") { it.target }
            assertNull(it.value)
            expect(refArray.untypedChild(3)) { it.key }
        }
    }

    @Test fun `should fail navigating to child of array of incorrect nullable type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        assertFailsWith<JSONTypeException> { refArray.child<JSONString?>(0) }.let {
            expect("Child") { it.nodeName }
            expect("JSONString?") { it.target }
            expect(JSONInt(1)) { it.value }
            expect(refArray.untypedChild(0)) { it.key }
        }
    }

    @Test fun `should get untyped child of object`() {
        val child = resourceRef.untypedChild("alpha")
        assertTrue(child.isRef<JSONInt>())
    }

    @Test fun `should get untyped child of array`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        val child0 = ref.untypedChild(0)
        assertTrue(child0.isRef<JSONInt>())
        val child1 = ref.untypedChild(1)
        assertTrue(child1.isRef<JSONString>())
        val child2 = ref.untypedChild(2)
        assertTrue(child2.isRef<JSONBoolean>())
    }

    @Test fun `should correctly report hasChild for object`() {
        assertTrue(resourceRef.hasChild<JSONInt>("alpha"))
        assertTrue(resourceRef.hasChild<JSONArray>("powersOfTen"))
        assertTrue(resourceRef.hasChild<JSONObject>("substructure"))
        assertTrue(resourceRef.hasChild<JSONString>("delta"))
        assertFalse(resourceRef.hasChild<JSONString>("beta"))
        assertFalse(resourceRef.hasChild<JSONString>("omega"))
    }

    @Test fun `should correctly report nullable hasChild for object`() {
        assertTrue(resourceRef.hasChild<JSONInt>("alpha"))
        assertTrue(resourceRef.hasChild<JSONInt?>("alpha"))
        assertTrue(resourceRef.hasChild<JSONInt?>("phi"))
        assertFalse(resourceRef.hasChild<JSONString?>("beta"))
        assertFalse(resourceRef.hasChild<JSONString?>("omega"))
    }

    @Test fun `should correctly report hasChild for array`() {
        val ref1 = resourceRef.child<JSONArray>("heterogeneousArray")
        assertTrue(ref1.hasChild<JSONInt>(0))
        assertTrue(ref1.hasChild<JSONNumber>(0))
        assertTrue(ref1.hasChild<JSONString>(1))
        assertTrue(ref1.hasChild<JSONBoolean>(2))
        assertFalse(ref1.hasChild<JSONInt>(1))
        assertFalse(ref1.hasChild<JSONValue>(4))
    }

    @Test fun `should correctly report nullable hasChild for array`() {
        val ref1 = resourceRef.child<JSONArray>("heterogeneousArray")
        assertTrue(ref1.hasChild<JSONInt?>(0))
        assertTrue(ref1.hasChild<JSONString?>(1))
        assertTrue(ref1.hasChild<JSONString?>(3))
        assertFalse(ref1.hasChild<JSONInt?>(1))
        assertFalse(ref1.hasChild<JSONValue?>(4))
    }

    @Test fun `should create ResourceRef using ref`() {
        val ref = resource.ref()
        expect(123) { ref.optionalInt("alpha") }
        expect("A string") { ref.optionalString("delta") }
    }

    @Test fun `should get value of node`() {
        val refInt = resourceRef.child<JSONInt>("alpha")
        expect(123) { refInt.value }
        val refString = resourceRef.child<JSONString>("delta")
        expect("A string") { refString.value }
    }

}
