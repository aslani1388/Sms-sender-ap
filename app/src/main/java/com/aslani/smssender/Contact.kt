package com.aslani.smssender

data class Contact(
    val name: String,
    val phone: String,
    var status: String = "در انتظار"
)
