package com.tiji.mistakes

import com.tiji.mistakes.service.splitQuestionOptionsForLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionOptionLayoutTest {
    @Test
    fun splitsFourParenthesizedChoicesIntoSeparateLines() {
        assertEquals(
            "题干\n(A)选项一\n(B)选项二\n(C)选项三\n(D)选项四",
            splitQuestionOptionsForLayout("题干 (A)选项一 (B)选项二 (C)选项三 (D)选项四")
        )
    }

    @Test
    fun splitsFullWidthAndDottedChoiceMarkers() {
        assertEquals(
            "题干\n（A）甲\n（B）乙",
            splitQuestionOptionsForLayout("题干 （A）甲  （B）乙")
        )
        assertEquals(
            "题干\nA. 甲\nB. 乙",
            splitQuestionOptionsForLayout("题干 A. 甲  B. 乙")
        )
    }

    @Test
    fun protectsFormulaLettersAndDoesNotDuplicateExistingBreaks() {
        assertEquals(
            "求 \\(A/B=C\\)，然后\nA. 甲\nB. 乙",
            splitQuestionOptionsForLayout("求 \\(A/B=C\\)，然后 A. 甲 B. 乙")
        )
        assertEquals(
            "题干\n(A)甲\n(B)乙",
            splitQuestionOptionsForLayout("题干\n(A)甲\n(B)乙")
        )
    }

    @Test
    fun keepsOpeningParenthesisWithChoiceAfterPhysicalWrap() {
        assertEquals(
            "题干则\n（A）甲\n（B）乙",
            splitQuestionOptionsForLayout("题干则（\nA）甲\n（B）乙")
        )
    }

    @Test
    fun splitsUnicodeRomanSubquestions() {
        assertEquals(
            "主干\n（Ⅰ）第一问\n（Ⅱ）第二问\n（Ⅲ）第三问",
            splitQuestionOptionsForLayout("主干 （Ⅰ）第一问 （Ⅱ）第二问 （Ⅲ）第三问")
        )
    }

    @Test
    fun splitsRomanPunctuationAndKeepsExistingBreaks() {
        assertEquals(
            "主干\nⅠ. 第一问\nⅡ、第二问\nⅢ：第三问",
            splitQuestionOptionsForLayout("主干 Ⅰ. 第一问 Ⅱ、第二问 Ⅲ：第三问")
        )
        val source = "主干\n(Ⅰ)第一问\n(Ⅱ)第二问"
        assertEquals(source, splitQuestionOptionsForLayout(source))
    }

    @Test
    fun supportsAsciiRomanNumeralsButDoesNotSplitOrdinaryLetters() {
        assertEquals(
            "主干\n(XI)第一问\n(XII)第二问",
            splitQuestionOptionsForLayout("主干 (XI)第一问 (XII)第二问")
        )
        val english = "I. went home. V. is a variable. I/V/X are ordinary letters."
        assertEquals(english, splitQuestionOptionsForLayout(english))
        val formula = "计算 \\(Ⅰ+Ⅱ\\)，再讨论 I/V/X。"
        assertEquals(formula, splitQuestionOptionsForLayout(formula))
    }

    @Test
    fun doesNotSplitSingleRomanSubquestion() {
        val source = "本题分为（Ⅰ）一部分内容。"
        assertEquals(source, splitQuestionOptionsForLayout(source))
    }

    @Test
    fun doesNotSplitOrdinaryEnglishAbbreviations() {
        val source = "This uses A. B. as ordinary English text."
        assertEquals(source, splitQuestionOptionsForLayout(source))
    }
}
