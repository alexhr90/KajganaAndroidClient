package com.alexhr90.muabet.domain

/** Reads a synthetic fixture from `src/test/resources/fixtures`. */
fun fixture(name: String): String =
    requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

private object Fixtures
