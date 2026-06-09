package com.rit.crossdev.jaga.minio.cleaner.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

fun Any.toJson(): String = jacksonObjectMapper().writeValueAsString(this)