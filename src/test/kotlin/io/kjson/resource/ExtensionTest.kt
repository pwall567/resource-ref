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
import kotlin.test.fail

import java.io.File
import java.math.BigDecimal

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeNonNull
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldStartWith
import io.kstuff.test.shouldThrow

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
    ).asRef<JSONObject>()

    @Test fun `should conditionally execute code`() {
        resourceRef.ifPresent<JSONInt>("alpha") {
            it.value shouldBe 123
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
        array shouldBe listOf(1, 10, 100, 1000, 10000)
    }

    @Test fun `should fail when 'map' operation finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { ref.map<JSONInt, Int>() }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe JSONString("hello")
            it.key shouldBe ref.untypedChild(1)
        }
    }

    @Test fun `should map contents of array with transformation`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        val array = ref.map<JSONInt, Int> { value * (it + 1) }
        array shouldBe listOf(1, 20, 300, 4000, 50000)
    }

    @Test fun `should fail when transforming 'map' operation finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { ref.map<JSONInt, Int> { value * (it + 1) } }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe JSONString("hello")
            it.key shouldBe ref.untypedChild(1)
        }
    }

    @Test fun `should map contents of array with complex transformation`() {
        val ref = resourceRef.child<JSONArray>("complexArray")
        val array = ref.map<JSONObject, String> { child<JSONString>("f1").value }
        array shouldBe listOf("AAA", "BBB", "CCC", "DDD")
    }

    @Test fun `should test whether any array item matches predicate`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        ref.any<JSONInt> { value > 100 } shouldBe true
        ref.any<JSONInt> { value < 0 } shouldBe false
    }

    @Test fun `should fail when 'any' test finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { ref.any<JSONInt> { value > 3 } }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe JSONString("hello")
            it.key shouldBe ref.untypedChild(1)
        }
    }

    @Test fun `should test whether all array items match predicate`() {
        val ref = resourceRef.child<JSONArray>("powersOfTen")
        ref.all<JSONInt> { value > 0 } shouldBe true
        ref.all<JSONInt> { value < 100 } shouldBe false
    }

    @Test fun `should fail when 'all' test finds invalid value`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { ref.all<JSONInt> { value < 10 } }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe JSONString("hello")
            it.key shouldBe ref.untypedChild(1)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun `should unconditionally map value`() {
        val beta = resourceRef.map<JSONInt, Int>("beta") {
            it.value
        }
        beta shouldBe 456
        shouldThrow<ResourceRefException> {
            resourceRef.map<JSONInt, Int>("omega") { it.value }
        }.let {
            it.message shouldStartWith "Can't locate JSON property \"omega\""
            it.message shouldEndWith "src/test/resources/json/json1.json#"
            it.text shouldBe "Can't locate JSON property \"omega\""
            it.resourceRef shouldBe resourceRef
        }
        val gamma: Int = resourceRef.map("gamma") { prop: JSONInt -> prop.value }
        gamma shouldBe 888
        // alternative, following deprecation of original function, using recommended replacement code
        val beta2 = resourceRef.child<JSONInt>("beta").value
        beta2 shouldBe 456
        shouldThrow<ResourceRefException> {
            resourceRef.child<JSONInt>("omega").value
        }.let {
            it.message shouldStartWith "Can't locate JSON property \"omega\""
            it.message shouldEndWith "src/test/resources/json/json1.json#"
            it.text shouldBe "Can't locate JSON property \"omega\""
            it.resourceRef shouldBe resourceRef
        }
    }

    @Test fun `should get optional String`() {
        resourceRef.optionalString("delta") shouldBe "A string"
        resourceRef.optionalString("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalString("alpha") }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "String"
            it.value shouldBe JSONInt(123)
            it.key shouldBe resourceRef.untypedChild("alpha")
        }
    }

    @Test fun `should get optional Boolean`() {
        resourceRef.optionalBoolean("epsilon") shouldBe true
        resourceRef.optionalBoolean("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalBoolean("alpha") }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "Boolean"
            it.value shouldBe JSONInt(123)
            it.key shouldBe resourceRef.untypedChild("alpha")
        }
    }

    @Test fun `should get optional Int`() {
        resourceRef.optionalInt("alpha") shouldBe 123
        resourceRef.optionalInt("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalInt("delta") }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "Int"
            it.value shouldBe JSONString("A string")
            it.key shouldBe resourceRef.untypedChild("delta")
        }
    }

    @Test fun `should get optional Long`() {
        resourceRef.optionalLong("sigma") shouldBe 123456789123456789
        resourceRef.optionalLong("alpha") shouldBe 123
        resourceRef.optionalLong("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalLong("delta") }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "Long"
            it.value shouldBe JSONString("A string")
            it.key shouldBe resourceRef.untypedChild("delta")
        }
    }

    @Test fun `should get optional Decimal`() {
        resourceRef.optionalDecimal("omicron") shouldBe BigDecimal("1.5")
        resourceRef.optionalDecimal("sigma") shouldBe BigDecimal(123456789123456789)
        resourceRef.optionalDecimal("alpha") shouldBe BigDecimal(123)
        resourceRef.optionalDecimal("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalDecimal("delta") }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "Decimal"
            it.value shouldBe JSONString("A string")
            it.key shouldBe resourceRef.untypedChild("delta")
        }
    }

    @Test fun `should get optional child object`() {
        val childRef = resourceRef.optionalChild<JSONObject>("substructure")
        childRef.shouldBeNonNull().node["one"].asInt shouldBe 111
        resourceRef.optionalChild<JSONObject>("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalChild<JSONObject>("delta") }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONObject"
            it.value shouldBe JSONString("A string")
            it.key shouldBe resourceRef.untypedChild("delta")
        }
    }

    @Test fun `should get optional child array`() {
        val childRef = resourceRef.optionalChild<JSONArray>("powersOfTen")
        childRef.shouldBeNonNull().node[2].asInt shouldBe 100
        resourceRef.optionalChild<JSONArray>("omega") shouldBe null
        shouldThrow<JSONTypeException> { resourceRef.optionalChild<JSONArray>("delta") }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONArray"
            it.value shouldBe JSONString("A string")
            it.key shouldBe resourceRef.untypedChild("delta")
        }
    }

    @Test fun `should iterate over object`() {
        val ref = resourceRef.child<JSONObject>("substructure")
        val results = mutableListOf<Pair<String, Int>>()
        ref.forEachKey<JSONInt> {
            results.add(it to node.value)
        }
        results.size shouldBe 2
        results[0] shouldBe ("one" to 111)
        results[1] shouldBe ("two" to 222)
    }

    @Test fun `should iterate over array`() {
        val ref = resourceRef.child<JSONArray>("complexArray")
        val results = mutableListOf<Pair<Int, String>>()
        ref.forEach<JSONObject> {
            results.add(it to node["f1"].asString)
        }
        results.size shouldBe 4
        results[0] shouldBe (0 to "AAA")
        results[1] shouldBe (1 to "BBB")
        results[2] shouldBe (2 to "CCC")
        results[3] shouldBe (3 to "DDD")
    }

    @Test fun `should navigate to child of object`() {
        val ref = resourceRef.child<JSONInt>("alpha")
        ref.node.value shouldBe 123
        ref.toString() shouldBe "$resourceRef/alpha"
    }

    @Test fun `should navigate to nullable child of object`() {
        val ref1 = resourceRef.child<JSONInt?>("alpha")
        ref1.node?.value shouldBe 123
        ref1.toString() shouldBe "$resourceRef/alpha"
        val ref2 = resourceRef.child<JSONInt?>("phi")
        ref2.node shouldBe null
        ref2.toString() shouldBe "$resourceRef/phi"
    }

    @Test fun `should fail navigating to child of object of incorrect type`() {
        shouldThrow<JSONTypeException> { resourceRef.child<JSONString>("alpha") }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONString"
            it.value shouldBe JSONInt(123)
            it.key shouldBe resourceRef.untypedChild("alpha")
        }
    }

    @Test fun `should fail navigating to null child of object of non-nullable type`() {
        shouldThrow<JSONTypeException> { resourceRef.child<JSONInt>("phi") }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe null
            it.key shouldBe resourceRef.untypedChild("phi")
        }
    }

    @Test fun `should fail navigating to child of object of incorrect nullable type`() {
        shouldThrow<JSONTypeException> { resourceRef.child<JSONString?>("alpha") }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONString?"
            it.value shouldBe JSONInt(123)
            it.key shouldBe resourceRef.untypedChild("alpha")
        }
    }

    @Test fun `should navigate to child of array`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        val refInt = refArray.child<JSONInt>(0)
        refInt.node.value shouldBe 1
        refInt.toString() shouldBe "$resourceRef/heterogeneousArray/0"
    }

    @Test fun `should navigate to nullable child of array`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        val refInt1 = refArray.child<JSONInt?>(0)
        refInt1.node?.value shouldBe 1
        refInt1.toString() shouldBe "$resourceRef/heterogeneousArray/0"
        val refInt2 = refArray.child<JSONInt?>(3)
        refInt2.node shouldBe null
        refInt2.toString() shouldBe "$resourceRef/heterogeneousArray/3"
    }

    @Test fun `should fail navigating to child of array of incorrect type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { refArray.child<JSONString>(0) }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONString"
            it.value shouldBe JSONInt(1)
            it.key shouldBe refArray.untypedChild(0)
        }
    }

    @Test fun `should fail navigating to null child of array of non-nullable type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { refArray.child<JSONInt>(3) }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONInt"
            it.value shouldBe null
            it.key shouldBe refArray.untypedChild(3)
        }
    }

    @Test fun `should fail navigating to child of array of incorrect nullable type`() {
        val refArray = resourceRef.child<JSONArray>("heterogeneousArray")
        shouldThrow<JSONTypeException> { refArray.child<JSONString?>(0) }.let {
            it.nodeName shouldBe "Child"
            it.expected shouldBe "JSONString?"
            it.value shouldBe JSONInt(1)
            it.key shouldBe refArray.untypedChild(0)
        }
    }

    @Test fun `should get untyped child of object`() {
        val child = resourceRef.untypedChild("alpha")
        child.isRef<JSONInt>() shouldBe true
    }

    @Test fun `should get untyped child of array`() {
        val ref = resourceRef.child<JSONArray>("heterogeneousArray")
        ref.untypedChild(0).isRef<JSONInt>() shouldBe true
        ref.untypedChild(1).isRef<JSONString>() shouldBe true
        ref.untypedChild(2).isRef<JSONBoolean>() shouldBe true
    }

    @Test fun `should correctly report hasChild for object`() {
        resourceRef.hasChild<JSONInt>("alpha") shouldBe true
        resourceRef.hasChild<JSONArray>("powersOfTen") shouldBe true
        resourceRef.hasChild<JSONObject>("substructure") shouldBe true
        resourceRef.hasChild<JSONString>("delta") shouldBe true
        resourceRef.hasChild<JSONString>("beta") shouldBe false
        resourceRef.hasChild<JSONString>("omega") shouldBe false
    }

    @Test fun `should correctly report nullable hasChild for object`() {
        resourceRef.hasChild<JSONInt>("alpha") shouldBe true
        resourceRef.hasChild<JSONInt?>("alpha") shouldBe true
        resourceRef.hasChild<JSONInt?>("phi") shouldBe true
        resourceRef.hasChild<JSONString?>("beta") shouldBe false
        resourceRef.hasChild<JSONString?>("omega") shouldBe false
    }

    @Test fun `should correctly report hasChild for array`() {
        val ref1 = resourceRef.child<JSONArray>("heterogeneousArray")
        ref1.hasChild<JSONInt>(0) shouldBe true
        ref1.hasChild<JSONNumber>(0) shouldBe true
        ref1.hasChild<JSONString>(1) shouldBe true
        ref1.hasChild<JSONBoolean>(2) shouldBe true
        ref1.hasChild<JSONInt>(1) shouldBe false
        ref1.hasChild<JSONValue>(4) shouldBe false
    }

    @Test fun `should correctly report nullable hasChild for array`() {
        val ref1 = resourceRef.child<JSONArray>("heterogeneousArray")
        ref1.hasChild<JSONInt?>(0) shouldBe true
        ref1.hasChild<JSONString?>(1) shouldBe true
        ref1.hasChild<JSONString?>(3) shouldBe true
        ref1.hasChild<JSONInt?>(1) shouldBe false
        ref1.hasChild<JSONValue?>(4) shouldBe false
    }

    @Test fun `should create ResourceRef using ref with explicit type`() {
        val ref = resource.ref<JSONObject>()
        ref.optionalInt("alpha") shouldBe 123
        ref.optionalString("delta") shouldBe "A string"
    }

    @Test fun `should create ResourceRef using ref with implied type`() {
        val ref: ResourceRef<JSONObject> = resource.ref()
        ref.optionalInt("alpha") shouldBe 123
        ref.optionalString("delta") shouldBe "A string"
    }

    @Test fun `should get value of node`() {
        val refInt = resourceRef.child<JSONInt>("alpha")
        refInt.value shouldBe 123
        val refString = resourceRef.child<JSONString>("delta")
        refString.value shouldBe "A string"
    }

}
