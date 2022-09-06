package com.jiaoay.rime.core

interface RimeListener {
    fun handleRimeNotification(messageType: String, messageValue: String)
}