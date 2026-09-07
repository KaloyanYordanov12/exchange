# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Maven + Spring Boot 4.1.1 project scaffold on Java 25 (Temurin), with the
  Maven wrapper and empty feature packages (`book`, `engine`, `ledger`, `api`,
  `realtime`, `persistence`, `sim`, `invariant`, `config`, `error`).
- Project meta: this changelog, README skeleton, MIT license, and a Conventional
  Commits `.gitmessage` template. `.gitattributes` forces LF on the Maven
  wrapper so CI on Linux runs it.
- `/actuator/health` endpoint with a MockMvc smoke test asserting `200`/`UP`
  (no database, no Testcontainers). Pulls in the `spring-boot-webmvc-test`
  module, which Spring Boot 4 split out of `starter-test`.
