# resource-ref

[![Build Status](https://github.com/pwall567/resource-ref/actions/workflows/build.yml/badge.svg)](https://github.com/pwall567/resource-ref/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/static/v1?label=Kotlin&message=v1.9.24&color=7f52ff&logo=kotlin&logoColor=7f52ff)](https://github.com/JetBrains/kotlin/releases/tag/v1.9.24)
[![Maven Central](https://img.shields.io/maven-central/v/io.kjson/resource-ref?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.kjson%22%20AND%20a:%22resource-ref%22)

Library to manage Resource references using [URI](https://www.rfc-editor.org/info/rfc3986) and
[JSON Pointer](https://tools.ietf.org/html/rfc6901).

## Background

The `JSONRef` class in the [`kjson-pointer`](https://github.com/pwall567/kjson-pointer) library provides a means of
navigating around a JSON or YAML structure.

The [`resource-loader`](https://github.com/pwall567/resource-loader) library simplifies the process of loading resources
identified by URL, including the managing of relative URLs.

The `resource-ref` library brings these together and provides a concise API for navigating structures of related JSON or
YAML resources.

## Quick Start

### `RefResourceLoader`

First, get an instance of `RefResourceLoader` (a form of `ResourceLoader` that will load JSON or YAML files):
```kotlin
    val loader = RefResourceLoader()
```

### `Resource`

Then, you can get a `Resource` in one of a number of ways:
```kotlin
    val fileResource = loader.resource(File("path.to.file"))                             // using java.io.File
    val pathResource = loader.resource(FileSystems.getDefault().getPath("path.to.file")) // using java.nio.file.Path
    val urlResource = loader.resource(URL("https://example.com/resource"))               // using java.net.URL
    val classpathResource = loader.resource(Resource.classPathURL("path.to.file")!!)     // using classpath
```

In the case of `RefResourceLoader`, the `Resource` will be a `Resource<JSONObject>`.

The `Resource` is not the resource itself, but an object providing:
- access to the resource by means of the `load()` function
- navigation to child, parent and sibling resources using the `resolve()` function

### `ResourceRef`

The `ref()` extension function on the `Resource` will load the resource as JSON, or if it has an extension of `.yaml` or
`.yml`, or it is an HTTP(S) resource with a MIME type containing "yaml" or "yml" (there is no official MIME type for
YAML) it will load the resource as YAML, and return a `ResourceRef<JSONObject>`.
```kotlin
    val ref: ResourceRef<JSONObject> = resource.ref()
```

The `ResourceRef` has two properties (both publicly accessible):
- `resource`: the `Resource` from which the data was loaded &ndash; this allows navigation between resources using the
  rules specified by [RFC-3986](https://www.rfc-editor.org/info/rfc3986), and
- `ref`: the `JSONRef` pointing to the identified location in the structure &ndash; this allows navigation **within**
  the resource.

The `ResourceRef` object is immutable &ndash; any navigation functions such as `resolve(destination)` _etc._ will return
a new `ResourceRef`.

Most of the functions available on `JSONRef` and also available on `ResourceRef`, for example `child()`, `parent()`,
`optionalString()` _etc._ (see [`JSONRef`](https://github.com/pwall567/kjson-pointer#jsonref) for more details), but
because the `ResourceRef` also carries the URL used to locate the resource, error messages will be much more helpful.

### `resolve()`

And the `ResourceRef` also has the function that combines the two areas of functionality &ndash; the `resolve()`
function will take a relative reference of the form "resource#node" and return a new `ResourceRef` pointing to the
specified location.
```kotlin
    val targetRef = ref.resolve<JSONObject>(destination)
```

There are three possibilities for the parameter string:
1. Relative URI with no fragment (the part following the "`#`" sign) &ndash; in this case the function will attempt to
   locate the resource identified by the relative URI using [RFC-3986](https://www.rfc-editor.org/info/rfc3986), and
   return a `ResourceRef` pointing to the root of the object.
2. A relative URI with a fragment &ndash; the function will attempt to locate the resource as above, and will return a
   new `ResourceRef` with the pointer set to the node identified by the fragment, as a
   [JSON Pointer](https://tools.ietf.org/html/rfc6901).
3. A fragment (with preceding "`#`" sign) only &ndash; the function will return a new `ResourceRef` for the current
   resource, with the pointer set to the node identified by the fragment.

A `resolve()` operation with no fragment specified will always return a `ResourceRef<JSONObject>`; when a fragment is
provided the `ResourceRef` parameter type may be any type assignable to `JSONValue?`.

More documentation to follow&hellip;

## Dependency Specification

The latest version of the library is 2.3, and it may be obtained from the Maven Central repository.

### Maven
```xml
    <dependency>
      <groupId>io.kjson</groupId>
      <artifactId>resource-ref</artifactId>
      <version>2.3</version>
    </dependency>
```
### Gradle
```groovy
    implementation 'io.kjson:kjson-pointer:2.3'
```
### Gradle (kts)
```kotlin
    implementation("io.kjson:kjson-pointer:2.3")
```

Peter Wall

2024-09-04
