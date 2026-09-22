package com.tiji.mistakes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tiji.mistakes.domain.KnowledgePointProgress
import com.tiji.mistakes.domain.MistakeProgressSummary
import com.tiji.mistakes.domain.SubjectProgress
import com.tiji.mistakes.ui.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeKnowledgeNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun expandedKnowledgePointOpensItsStableId() {
        var opened: String? = null
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    progressSummary = MistakeProgressSummary(total = 1, bySubject = listOf(
                        SubjectProgress("数学", 1, 0, knowledgePoints = listOf(
                            KnowledgePointProgress("math:function", "函数", 1, 0)
                        ))
                    )),
                    resetScrollToken = 0,
                    onOpenKnowledge = { opened = it }
                )
            }
        }
        composeRule.onNodeWithTag("home_subject_数学").performScrollTo().performClick()
        composeRule.onNodeWithTag("home_knowledge_math:function").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("math:function", opened) }
    }
}
