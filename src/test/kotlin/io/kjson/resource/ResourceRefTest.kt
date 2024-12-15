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

import kotlin.test.Test

import java.io.File
import java.net.URL

import io.kstuff.test.shouldBe
import io.kstuff.test.shouldBeSameInstance
import io.kstuff.test.shouldBeType
import io.kstuff.test.shouldEndWith
import io.kstuff.test.shouldThrow

import io.kjson.JSON.asInt
import io.kjson.JSONArray
import io.kjson.JSONInt
import io.kjson.JSONNumber
import io.kjson.JSONObject
import io.kjson.JSONString
import io.kjson.JSONTypeException
import io.kjson.JSONValue
import io.kjson.pointer.JSONPointer
import io.kjson.pointer.JSONPointerException
import io.kjson.pointer.JSONRef
import io.kjson.pointer.child

class ResourceRefTest {

    private val loader = RefResourceLoader()
    private val resource = loader.resource(File("src/test/resources/json/json1.json"))
    private val json = resource.load()
    private val resourceRef = ResourceRef(resource, JSONRef(json)).asRef<JSONObject>()

    @Test fun `should create ResourceRef`() {
        resourceRef.resourceURL.toString() shouldEndWith "json/json1.json"
        resourceRef.node shouldBeSameInstance json
        resourceRef.pointer shouldBe JSONPointer.root
        resourceRef.child<JSONInt>("alpha").node shouldBe JSONInt(123)
    }

    @Test fun `should navigate to parent`() {
        val ref = ResourceRef(resource, JSONRef.of(json, JSONPointer("/substructure/one")))
        val parent = ref.parent<JSONObject>()
        parent.node["two"].asInt shouldBe 222
        parent.pointer shouldBe JSONPointer("/substructure")
        val grandParent = parent.parent<JSONObject>()
        grandParent.node["alpha"].asInt shouldBe 123
        grandParent.pointer shouldBe JSONPointer.root
    }

    @Test fun `should fail navigating to parent of root`() {
        shouldThrow<JSONPointerException>("Can't get parent of root JSON Pointer") {
            resourceRef.parent<JSONArray>()
        }
    }

    @Test fun `should fail navigating to parent of incorrect type`() {
        val ref = ResourceRef(resource, JSONRef.of(json, JSONPointer("/substructure/one")))
        shouldThrow<JSONTypeException> {
            ref.parent<JSONArray>()
        }.let {
            it.nodeName shouldBe "Parent"
            it.expected shouldBe "JSONArray"
            it.value.shouldBeType<JSONObject>()
            it.key shouldBe resourceRef.untypedChild("substructure")
        }
    }

    @Test fun `should convert to ref of correct type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        val refInt = ref.asRef<JSONInt>()
        refInt.node.value shouldBe 123
    }

    @Test fun `should fail converting to ref of incorrect type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        shouldThrow<JSONTypeException> { ref.asRef<JSONString>() }.let {
            it.nodeName shouldBe "Node"
            it.expected shouldBe "JSONString"
            it.value shouldBe JSONInt(123)
            it.key shouldBe resourceRef.untypedChild("alpha")
        }
    }

    @Test fun `should convert to ref of nullable type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        val refInt = ref.asRef<JSONInt?>()
        refInt.node?.value shouldBe 123
    }

    @Test fun `should test for specified type`() {
        val ref = resourceRef.child<JSONValue?>("alpha")
        ref.isRef<JSONInt>() shouldBe true
        ref.isRef<JSONNumber>() shouldBe true
        ref.isRef<JSONValue>() shouldBe true
        ref.isRef<JSONString>() shouldBe false
    }

    @Test fun `should test for specified type including nullable`() {
        val ref = resourceRef.child<JSONValue>("alpha")
        ref.isRef<JSONInt?>() shouldBe true
        ref.isRef<JSONNumber?>() shouldBe true
        ref.isRef<JSONValue?>() shouldBe true
        ref.isRef<JSONString?>() shouldBe false
    }

    @Test fun `should test for specified type with null value`() {
        val ref = resourceRef.child<JSONValue?>("phi")
        ref.isRef<JSONInt?>() shouldBe true
        ref.isRef<JSONNumber?>() shouldBe true
        ref.isRef<JSONValue?>() shouldBe true
        ref.isRef<JSONString?>() shouldBe true
    }

    @Test fun `should resolve relative reference within same resource`() {
        val ref1 = resourceRef.resolve<JSONObject>("#/substructure")
        ref1.node["two"] shouldBe JSONInt(222)
        val ref2 = ref1.resolve<JSONObject>("#/complexArray/2")
        ref2.node["f1"] shouldBe JSONString("CCC")
    }

    @Test fun `should resolve relative reference to another resource`() {
        val ref1 = resourceRef.resolve<JSONObject>("json2.json")
        ref1.node["field1"] shouldBe JSONInt(12345)
    }

    @Test fun `should resolve relative reference to specified node in another resource`() {
        val ref1 = resourceRef.resolve<JSONObject>("json2.json#/field2")
        ref1.node["sub1"] shouldBe JSONInt(10)
    }

    @Test fun `should fail when trying to resolve incorrect relative reference`() {
        shouldThrow<ResourceRefException> { resourceRef.resolve<JSONObject>("json2.json#/field2/wrong") }.let {
            it.message shouldBe "Can't locate JSON property \"wrong\", at src/test/resources/json/json2.json#/field2"
            it.text shouldBe "Can't locate JSON property \"wrong\""
            val resultResource = loader.resource(File("src/test/resources/json/json2.json"))
            val resultRef = JSONRef(resultResource.load()).asRef<JSONObject>().child<JSONObject>("field2")
            it.resourceRef shouldBe ResourceRef(resultResource, resultRef)
            it.pointer shouldBe resultRef.pointer
        }
    }

    @Test fun `should resolve relative reference to specified node in an external resource`() {
        val ref1 = resourceRef.resolve<JSONObject>("http://kjson.io/json/http/testhttp1.json#/properties/xxx")
        ref1.node["\$ref"] shouldBe JSONString("http://kjson.io/json/http/testhttp2.json#/\$defs/Def1")
    }

    @Test fun `should simplify path in toString`() {
        resourceRef.toString() shouldBe "src/test/resources/json/json1.json#"
        resourceRef.child<JSONObject>("substructure").toString() shouldBe
                "src/test/resources/json/json1.json#/substructure"
        val resourceHTTP = loader.resource(URL("http://kjson.io/json/http/testhttp1.json"))
        val resourceRefHTTP = ResourceRef(resourceHTTP, JSONRef(resourceHTTP.load()))
        resourceRefHTTP.toString() shouldBe "http://kjson.io/json/http/testhttp1.json#"
    }

}
