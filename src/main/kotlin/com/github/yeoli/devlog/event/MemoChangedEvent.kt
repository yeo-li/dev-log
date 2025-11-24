package com.github.yeoli.devlog.event

import com.intellij.util.messages.Topic

fun interface MemoListener {
    fun onChanged()
}

object MemoChangedEvent {
    val TOPIC: Topic<MemoListener> =
        Topic.create("MemoChanged", MemoListener::class.java)
}