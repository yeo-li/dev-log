package com.github.yeoli.devlog.domain.memo.repository

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DevLogMemoStorage",
    storages = [Storage("devlog-memos.xml")]
)
@Service(Service.Level.PROJECT)
class MemoRepository : PersistentStateComponent<MemoStorageState> {
    private var state = MemoStorageState()

    override fun getState(): MemoStorageState = state

    override fun loadState(state: MemoStorageState) {
        this.state = state
    }

    fun save(memo: MemoState) {
        state.memos.add(memo)
    }

    fun getAll(): List<MemoState> {
        return state.memos
    }
}
