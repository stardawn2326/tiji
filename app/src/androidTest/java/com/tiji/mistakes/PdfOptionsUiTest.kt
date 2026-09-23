package com.tiji.mistakes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOff
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PdfOptionsUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun onlyQuestionPdfAndImagesDefaultOff() {
        var result: PdfExportOptions? = null
        rule.setContent { MaterialTheme {
            PdfExportOptionsDialog(1, PdfExportOptions(template = PdfTemplate.ANSWER), {}, { result = it })
        } }
        rule.onNodeWithTag("pdf_template_answer").assertDoesNotExist()
        rule.onNodeWithTag("pdf_include_source_images").assertIsOff()
        rule.onNodeWithTag("pdf_export_options_confirm").performClick()
        rule.runOnIdle {
            assertEquals(PdfTemplate.PRACTICE, result?.template)
            assertEquals(false, result?.includeSourceImages)
            assertEquals(false, result?.originalImagesOnly)
        }
    }
}
