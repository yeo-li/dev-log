package com.github.yeoli.devlog.domain.memo.repository

import com.github.yeoli.devlog.domain.memo.domain.Memo
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

    fun save(memo: Memo) {
        state.memos.add(memo.toState())
    }

    fun getAll(): List<Memo> {
        return state.memos.map { it.toDomain() }
    }

    private fun removeMemoById(memoId: Long) {
        state.memos.removeIf { it.id == memoId }
    }

    fun removeMemosById(memoIds: List<Long>) {
        for (memoId in memoIds) {
            removeMemoById(memoId)
        }
    }
}
