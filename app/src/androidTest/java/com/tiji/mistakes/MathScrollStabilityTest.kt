package com.tiji.mistakes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.ui.math.LocalMathWebViewPool
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.math.MathWebViewPool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MathScrollStabilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun formulaHeightSurvivesRepeatedLazyDisposalAndReturn() {
        val pool = MathWebViewPool()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMathWebViewPool provides pool) {
                    LazyColumn(Modifier.testTag("list")) {
                        item(key = "formula") {
                            Box(Modifier.fillMaxWidth().testTag("formula")) {
                                MathText("函数求导：\\(x^2+\\frac{1}{x})\\)\n".repeat(8))
                            }
                        }
                        items(25) { Box(Modifier.fillMaxWidth().height(120.dp)) }
                    }
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onNodeWithTag("formula").fetchSemanticsNode().size.height > 180
        }
        rule.waitForIdle()
        val height = rule.onNodeWithTag("formula").fetchSemanticsNode().size.height
        repeat(5) {
            rule.onNodeWithTag("list").performScrollToIndex(20)
            rule.onNodeWithTag("list").performScrollToIndex(0)
            rule.waitForIdle()
            assertEquals(height, rule.onNodeWithTag("formula").fetchSemanticsNode().size.height)
        }
        rule.runOnIdle { pool.close() }
    }
}
