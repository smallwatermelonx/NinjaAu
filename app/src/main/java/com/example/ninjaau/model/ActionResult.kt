package com.example.ninjaau.model

data class ActionResult<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)
