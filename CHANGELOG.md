# Change Log

The format is based on [Keep a Changelog](http://keepachangelog.com/).

## [Unreleased]
### Changed
- `pom.xml`: added missing sections (documentation only)
- tests: updated tests involving Windows file path details

## [4.0] - 2025-01-14
### Changed
- `ResourceRef`, `RefResourceLoader`: adopted new naming policy from `resource-loader` (potential breaking change)
- `pom.xml`: updated dependency version

## [3.1] - 2024-12-15
### Changed
- `pom.xml`: updated dependency version
- tests: converted to `should-test` library

## [3.0] - 2024-11-19
### Changed
- `ResourceRef`, `RefResourceLoader`, `Extension.kt`: removed restriction that `resource` always used `JSONObject`
- `RefResourceLoader`: added `baseURL` constructor parameter
- `pom.xml`: updated version of `resource-loader`

## [2.4] - 2024-11-13
### Changed
- `pom.xml`: updated version of `resource-loader`

## [2.3] - 2024-09-04
### Changed
- `pom.xml`: updated dependency versions

## [2.2] - 2024-08-19
### Changed
- `RefResourceLoader`: added default parameter to `looksLikeYAML()` function
- `pom.xml`: updated dependency versions
- tests: Minor code tidy

## [2.1] - 2024-08-06
### Changed
- `pom.xml`: updated dependency version

## [2.0] - 2024-07-24
### Added
- `build.yml`, `deploy.yml`: converted project to GitHub Actions
### Changed
- `RefResourceLoader`: added `addToCache` function
- `ResourceRef`: added comments
- `pom.xml`: updated Kotlin version to 1.9.24
### Removed
- `.travis.yml`

## [1.5] - 2024-02-25
### Changed
- `pom.xml`: updated dependency version

## [1.4] - 2024-02-22
### Changed
- `RefResourceLoader`: fixed bug in caching

## [1.3] - 2024-02-14
### Changed
- `pom.xml`: updated dependency versions

## [1.2] - 2024-02-14
### Changed
- `pom.xml`: updated dependency version

## [1.1] - 2024-01-22
### Changed
- `pom.xml`: updated dependency version

## [1.0] - 2024-01-03
### Changed
- `ResourceRef`, `Extension.kt`: added more functions
- `pom.xml`: promoted to version 1.0

## [0.1] - 2023-12-11
### Added
- all files: initial versions (work in progress)
