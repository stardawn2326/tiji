package com.tiji.mistakes

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.junit4.createComposeRule
import com.tiji.mistakes.ui.common.rememberPrimaryListState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PrimaryScrollRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun secondaryReturnRestoresExactOffsetButExplicitTabVisitResets() {
        val secondary = mutableStateOf(false)
        val visitToken = mutableIntStateOf(0)
        var currentList: LazyListState? = null
        rule.setContent {
            val stateHolder = rememberSaveableStateHolder()
            if (secondary.value) {
                Text("二级页面")
            } else {
                stateHolder.SaveableStateProvider("primary") {
                    val list = rememberPrimaryListState(visitToken.intValue)
                    currentList = list
                    LazyColumn(state = list) { items((0..99).toList()) { Text("第 $it 项") } }
                }
            }
        }
        rule.runOnIdle { runBlocking { requireNotNull(currentList).scrollToItem(35, 18) } }
        rule.runOnIdle { secondary.value = true }
        rule.waitForIdle()
        rule.runOnIdle { secondary.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(35, requireNotNull(currentList).firstVisibleItemIndex)
            assertEquals(18, requireNotNull(currentList).firstVisibleItemScrollOffset)
        }
        rule.runOnIdle { visitToken.intValue += 1 }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, requireNotNull(currentList).firstVisibleItemIndex) }
    }
}
