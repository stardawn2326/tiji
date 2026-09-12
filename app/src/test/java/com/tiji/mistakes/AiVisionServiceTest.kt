package com.tiji.mistakes

import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.ContentBlockKind
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.QuestionContentBlock
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.QuestionSegment
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_END
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_START
import com.tiji.mistakes.service.TIJI_SOLUTION_V3_END
import com.tiji.mistakes.service.TIJI_SOLUTION_V3_START
import com.tiji.mistakes.service.extractRecognizedQuestionFromSolution
import com.tiji.mistakes.service.mathSegmentFormatIssues
import com.tiji.mistakes.service.validateOcrRecognition
import com.tiji.mistakes.service.buildSupplementalTextInstruction
import com.tiji.mistakes.service.buildRecognitionCorrectionInstruction
import com.tiji.mistakes.service.structuredSolveOutputInstruction
import com.tiji.mistakes.service.VisualEvidence
import com.tiji.mistakes.service.combineVisualEvidence
import com.tiji.mistakes.service.combineLocalOcrDocuments
import com.tiji.mistakes.service.LocalOcrDocument
import com.tiji.mistakes.service.GraphicSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionServiceTest {
    private val service = AiVisionService()

    @Test
    fun combinesMultipleSourcePagesInOrder() {
        val combined = combineVisualEvidence(
            listOf(
                VisualEvidence(questionText = "第一页", questionSegments = listOf(QuestionSegment("text", "第一页")), graphicSpecs = listOf(GraphicSpec(0, 0.1f, 0.1f, 0.4f, 0.4f))),
                VisualEvidence(questionText = "第二页", questionSegments = listOf(QuestionSegment("text", "第二页")), graphicSpecs = listOf(GraphicSpec(0, 0.2f, 0.2f, 0.5f, 0.5f)))
            )
        )

        assertEquals("第一页\n第二页", combined.questionText)
        assertEquals(listOf(0, 1), combined.graphicSpecs.map { it.sourceIndex })
        assertEquals("paragraphBreak", combined.questionSegments[1].type)
    }

    @Test
    fun combinesLocalOcrPagesWithoutDroppingText() {
        val combined = combineLocalOcrDocuments(
            listOf(LocalOcrDocument(text = "第一页", formulaCandidates = listOf("x=1")), LocalOcrDocument(text = "第二页", formulaCandidates = listOf("y=2")))
        )
        assertEquals("第一页\n第二页", combined.text)
        assertEquals(listOf("x=1", "y=2"), combined.formulaCandidates)
    }

    @Test
    fun supplementalTextIsExplicitlySeparatedFromRecognizedQuestionSource() {
        val instruction = buildSupplementalTextInstruction("图片右侧被裁掉：还要求求出最小值。")

        assertTrue(instruction.contains("<user_supplement>"))
        assertTrue(instruction.contains("还要求求出最小值"))
        assertTrue(instruction.contains("不是原题来源"))
        assertTrue(instruction.contains("不得写入内部题目标记、题目 segments 或 recognition"))
        assertEquals("", buildSupplementalTextInstruction("   "))
    }

    @Test
    fun recognitionCorrectionIsExplicitAndDoesNotBecomeProtocolText() {
        val instruction = buildRecognitionCorrectionInstruction("把 x=1 修正为 x=-1")

        assertTrue(instruction.contains("<recognition_correction>"))
        assertTrue(instruction.contains("x=-1"))
        assertTrue(instruction.contains("只作为题目文字的修正依据"))
        assertEquals("", buildRecognitionCorrectionInstruction("  "))
    }

    @Test
    fun followUpPromptRequestsSingleBodyStructureWithMarkdownFallback() {
        val prompt = service.buildFollowUpPrompt(
            context = "原题：求函数的极值。\n已有 AI 解答：极值点为 x=0。",
            prompt = "请补充为什么要先求导数。",
            textOnlyFallbackNote = "仅依据文字回答。"
        )

        assertTrue(prompt.contains("[[TIJI_FOLLOW_UP_V1_START]]"))
        assertTrue(prompt.contains("schemaVersion 为 1"))
        assertTrue(prompt.contains("segments 为数组"))
        assertFalse(prompt.contains(TIJI_SOLUTION_V2_START))
        assertFalse(prompt.contains(TIJI_SOLUTION_V2_END))
        assertFalse(prompt.contains("\"sections\""))
        assertFalse(prompt.contains("pmatrix"))
        assertFalse(prompt.contains("a&b"))
        assertTrue(prompt.contains("直接、自然地回答"))
        assertTrue(prompt.contains("请补充为什么要先求导数"))
        assertTrue(prompt.contains("仅依据文字回答"))
        assertTrue(prompt.contains("回退为普通 Markdown 自然回复"))
        assertTrue(prompt.contains("不要拆成"))
    }

    @Test
    fun solveOutputContractMatchesTheStrictV50Mode() {
        val instruction = structuredSolveOutputInstruction()

        assertTrue(instruction.contains(TIJI_SOLUTION_V2_START))
        assertTrue(instruction.contains(TIJI_SOLUTION_V2_END))
        assertTrue(instruction.contains("schemaVersion 3 是当前首选的解答协议"))
        assertTrue(instruction.contains(TIJI_SOLUTION_V3_START))
        assertTrue(instruction.contains(TIJI_SOLUTION_V3_END))
        assertTrue(instruction.contains("最终解答必须使用 schemaVersion 2 的机器可读结构"))
        assertTrue(instruction.contains("sections 必须且只能依次包含 recognition、approach、derivation、finalAnswer"))
        assertTrue(instruction.contains("禁止输出残缺 JSON"))
        assertTrue(instruction.contains("改用旧版四分区文本结构"))
        assertTrue(instruction.contains("必须返回为独立的 math segment"))
        assertTrue(instruction.contains("(1)(2)"))
        assertTrue(instruction.contains("使用 lineBreak"))
        assertTrue(instruction.contains("只在 recognition（题目识别）中表达原题自身的语义换行"))
        assertTrue(instruction.contains("不要把这条小题拆分规则套用到 approach、derivation 或 finalAnswer"))
        assertTrue(instruction.contains("不得概括、改写、补写或删减"))
        assertTrue(instruction.contains("解题部分的中文正文使用自然的中文标点"))
        assertFalse(instruction.contains("最终解答优先使用 schemaVersion 2"))
        assertFalse(instruction.contains("上述 math segment 与语义 lineBreak"))
        assertFalse(instruction.contains("TIJI_FOLLOW_UP"))
        assertFalse(instruction.contains("schemaVersion 为 1"))
    }

    @Test
    fun parsesStrictRecognitionJson() {
        val result = service.parseRecognition(
            """
            {
              "title":"二次函数",
              "question":"求函数的顶点",
              "answer":"(1, -4)",
              "explanation":"配方法",
              "subject":"数学",
              "knowledgePoints":["二次函数", "配方法"],
              "tags":["易错"],
              "difficulty":4
            }
            """.trimIndent()
        )

        assertEquals("求函数的顶点", result.question)
        assertEquals(listOf("二次函数", "配方法"), result.knowledgePoints)
        assertEquals(4, result.difficulty)
    }

    @Test
    fun prefersSourceQuestionTextOverLegacyQuestionWithDiagramEvidence() {
        val result = service.parseRecognition(
            """
            {
              "questionText":"如图，求信号的周期。",
              "visibleTextLines":["如图，求信号的周期。"],
              "question":"如图，求信号的周期。由波形图可知，f(t) 是一个三角形脉冲。",
              "diagramEvidence":{"description":"三角形脉冲，横轴为 t"},
              "answer":"T=2",
              "explanation":"根据图形计算周期。",
              "title":"周期"
            }
            """.trimIndent()
        )

        assertEquals("如图，求信号的周期。", result.question)
        assertEquals(listOf("如图，求信号的周期。"), result.visibleTextLines)
        assertTrue(result.diagramEvidence.contains("三角形脉冲"))
    }

    @Test
    fun preservesRecognitionMathSourceWithoutRewriting() {
        val result = service.parseRecognition(
            """
            {
              "questionText":"求 x_{n} 与 \\frac{1}{2}，且 √(x)≤2。",
              "answer":"x^2=4，∑_{i=1}^{n} i",
              "explanation":"代入计算。",
              "title":"计算"
            }
            """.trimIndent()
        )

        assertEquals("求 x_{n} 与 \\frac{1}{2}，且 √(x)≤2。", result.question)
        assertEquals("x^2=4，∑_{i=1}^{n} i", result.answer)
    }

    @Test
    fun preservesInlineFunctionSymbolsInRecognitionSource() {
        val result = service.parseRecognition(
            """
            {
              "questionText":"信号为 f(t)，且 F(jω)=R(ω)+jX(ω)，其中 R(ω) 为实部。",
              "answer":"F(jω)",
              "explanation":"读取题设。"
            }
            """.trimIndent()
        )

        assertEquals("信号为 f(t)，且 F(jω)=R(ω)+jX(ω)，其中 R(ω) 为实部。", result.question)
        assertEquals("F(jω)", result.answer)
    }

    @Test
    fun parsesStructuredVisualEvidenceWithoutSolvingFields() {
        val evidence = service.parseVisualEvidence(
            """
            {
              "questionText":"如图，求三角形面积。",
              "formulas":["S=ah/2"],
              "options":["A. 1", "B. 2"],
              "diagram":{"description":"三角形 ABC，AB 为底边","labels":["A","B","C"],"relations":["AB 垂直于高"]},
              "tableData":[],
              "uncertainItems":["C 点旁一个小字"],
              "graphic":{"present":true,"sourceIndex":0,"left":0.1,"top":0.2,"right":0.9,"bottom":0.8,"type":"figure","labels":["A","B","C"]}
            }
            """.trimIndent()
        )

        assertEquals("如图，求三角形面积。", evidence.questionText)
        assertEquals(listOf("S=ah/2"), evidence.formulas)
        assertEquals("三角形 ABC，AB 为底边", evidence.diagramDescription)
        assertEquals(1, evidence.graphicSpecs.size)
        assertTrue(evidence.uncertainItems.single().contains("C 点"))
    }

    @Test
    fun structuredVisualQuestionAssemblesOnlyExplicitFormulaPlaceholders() {
        val evidence = service.parseVisualEvidence(
            """
            {
              "questionText":"如图，信号为 f(t)，求周期。",
              "questionTemplate":"如图，信号为 [[FORMULA_0]]，求周期。",
              "formulas":["f(t)=t^2"],
              "diagram":{"description":"横轴为 t，图形是三角形脉冲"},
              "graphic":{"present":true,"sourceIndex":0,"left":0.1,"top":0.2,"right":0.9,"bottom":0.8}
            }
            """.trimIndent()
        )

        assertEquals(
            "如图，信号为 \\(f(t)=t^2\\)，求周期。",
            evidence.toRecognizedQuestion().question
        )
        assertFalse(evidence.toRecognizedQuestion().question.contains("横轴"))
    }

    @Test
    fun visualAssistProtocolUsesTheCommonStructuredQuestionParser() {
        val result = service.parseStructuredSolveRecognition(
            """
            [[TIJI_META:{"difficulty":3,"subject":"数学","questionType":"计算题","title":"周期","diagramEvidence":{"description":"横轴为 t，图形是三角形脉冲"},"graphic":{"present":true,"sourceIndex":0,"left":0.1,"top":0.2,"right":0.9,"bottom":0.8,"type":"figure"}}]]
            [[TIJI_QUESTION_START]]
            信号为 \(f(t)\)，求周期。
            [[TIJI_QUESTION_END]]
            """.trimIndent()
        )

        assertEquals("信号为 \\(f(t)\\)，求周期。", result.question)
        assertTrue(result.diagramEvidence.contains("横轴"))
        assertEquals(1, result.graphicSpecs.size)
        assertFalse(result.question.contains("三角形脉冲"))
    }

    @Test
    fun stripsMarkdownFenceAndClampsDifficulty() {
        val result = service.parseRecognition(
            """```json
            {"title":"题目","question":"1+1=?","answer":"2","explanation":"","subject":"数学","knowledgePoints":[],"tags":[],"difficulty":9}
            ```"""
        )

        assertEquals(5, result.difficulty)
    }

    @Test
    fun recoversLabeledMarkdownWhenGatewayIgnoresJsonInstruction() {
        val result = service.parseRecognition(
            """
            ## 题目识别：
            求函数 (f(x)=x^2) 在 (x=1) 处的值。

            ## 解题思路：
            直接代入 (x=1)。

            ## 最终答案：
            (1)
            """.trimIndent()
        )

        assertEquals("求函数 (f(x)=x^2) 在 (x=1) 处的值。", result.question)
        assertEquals("(1)", result.answer)
    }

    @Test
    fun treatsStandaloneTextbookSolutionMarkerAsExplanation() {
        val result = service.parseRecognition(
            """
            题目识别：
            【例2】讨论级数的收敛性。

            解
            先考虑绝对值级数，再按参数分类讨论。

            最终答案：按参数取值分三种情况。
            """.trimIndent()
        )

        assertEquals("【例2】讨论级数的收敛性。", result.question)
        assertTrue(result.explanation.contains("先考虑绝对值级数"))
        assertEquals("按参数取值分三种情况。", result.answer)
    }

    @Test
    fun structuredSolveKeepsSolutionOutOfQuestionWhenMarkerLeaks() {
        val result = service.parseStructuredSolveRecognition(
            """
            [[TIJI_META:{"difficulty":3,"subject":"数学","questionType":"计算题","title":"级数收敛性"}]]
            [[TIJI_QUESTION_START]]
            【例2】讨论级数的收敛性。
            解先考虑绝对值级数，再按参数分类讨论。
            [[TIJI_QUESTION_END]]
            解题思路：按比值判别法分类。
            最终答案：分三种情况。
            """.trimIndent()
        )

        assertEquals("【例2】讨论级数的收敛性。", result.question)
        assertTrue(result.explanation.contains("先考虑绝对值级数"))
        assertTrue(result.explanation.contains("按比值判别法分类"))
        assertEquals("分三种情况。", result.answer)
    }

    @Test
    fun directVisualSolveRepairsSpacedUnderlineSlashesInQuestionMarker() {
        val result = service.parseStructuredSolveRecognition(
            """
            [[TIJI_META:{"difficulty":0,"subject":"","questionType":"","title":"积分"}]]
            [[TIJI_QUESTION_START]]
            【例2】设区域 D 为 \(x^2+y^2\le R^2\)，则积分结果为 \ \ \ \。
            [[TIJI_QUESTION_END]]
            解题思路：使用极坐标计算。
            最终答案：略。
            """.trimIndent()
        )

        assertTrue(result.questionSegments.any { it.type == "blank" })
        assertEquals(
            "【例2】设区域 D 为 \\(x^2+y^2\\le R^2\\)，则积分结果为 \\(\\underline{\\hspace{2.5em}}\\)。",
            result.question
        )
        assertFalse(result.question.contains("\\ \\ \\"))
    }

    @Test
    fun directVisualSolveUsesStructuredQuestionSegmentsBeforePlainMarker() {
        val question = service.parseDirectVisualQuestionSegments(
            """
            [[TIJI_QUESTION_SEGMENTS_START]]
            {"segments":[
              {"type":"text","text":"【例2】结果为 "},
              {"type":"math","latex":"\\int_0^1 f(t)\\,dt"},
              {"type":"text","text":"，填空 "},
              {"type":"blank"},
              {"type":"text","text":"。"}
            ]}
            [[TIJI_QUESTION_SEGMENTS_END]]
            [[TIJI_QUESTION_START]]
            模型错误的普通文本 \\ \\ \\。
            [[TIJI_QUESTION_END]]
            """.trimIndent()
        )

        assertEquals(
            "【例2】结果为 \\(\\int_0^1 f(t)\\,dt\\)，填空 \\(\\underline{\\hspace{2.5em}}\\)。",
            question
        )
    }

    @Test
    fun directVisualSolvePreservesStandardMatrixRowsFromJson() {
        val question = service.parseDirectVisualQuestionSegments(
            """
            [[TIJI_QUESTION_SEGMENTS_START]]
            {"segments":[
              {"type":"text","text":"设"},
              {"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"}
            ]}
            [[TIJI_QUESTION_SEGMENTS_END]]
            """.trimIndent()
        )

        assertTrue(question!!.contains("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"))
        assertFalse(question.contains("a&b_c&d"))
    }

    @Test
    fun rejectsResponseWithoutQuestion() {
        val error = runCatching { service.parseRecognition("{\"title\":\"空结果\"}") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun detectsAndNormalizesLegacyDeepSeekConfiguration() {
        val preset = AiProviderPreset.detect("https://api.deepseek.com", "Deepseek")

        assertEquals(AiProviderPreset.DEEPSEEK, preset)
        assertEquals("deepseek-v4-flash", preset.model)
        assertEquals("deepseek-v4-pro", AiProviderPreset.DEEPSEEK.modelOrDefault("DEEPSEEK-V4-PRO"))
        assertEquals("deepseek-v4-flash", AiProviderPreset.DEEPSEEK.modelOrDefault("Deepseek"))
    }

    @Test
    fun detectsGeminiAndQwenPresets() {
        assertEquals(
            AiProviderPreset.GEMINI,
            AiProviderPreset.detect("https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.6-flash")
        )
        assertEquals(
            AiProviderPreset.QWEN,
            AiProviderPreset.detect("https://dashscope-us.aliyuncs.com/compatible-mode/v1/", "qwen3-vl-plus")
        )
    }

    @Test
    fun recognizesDeepSeekVisionExperimentalModel() {
        val preset = AiProviderPreset.detect(
            "https://api.deepseek.com/v1",
            "deepseek-v4-flash-vision-exp"
        )

        assertEquals(AiProviderPreset.DEEPSEEK, preset)
        assertTrue(preset.supportsVisionFor("deepseek-v4-flash-vision-exp"))
        assertEquals("多模态模型", preset.modelModalityLabel("deepseek-v4-flash-vision-exp"))
        assertTrue(preset.modelOptions.contains("deepseek-v4-flash-vision-exp"))
    }

    @Test
    fun allBuiltInPresetsHaveUsableDefaults() {
        AiProviderPreset.entries
            .filterNot { it == AiProviderPreset.CUSTOM }
            .forEach {
                assertTrue(it.endpoint.startsWith("https://"))
                assertTrue(it.model.isNotBlank())
                assertTrue(it.modelOptions.contains(it.model))
            }
    }

    @Test
    fun qwenPresetKeepsNamesAndUsesOfficialPerModelVisionCapabilities() {
        val qwen = AiProviderPreset.QWEN

        assertEquals("qwen3.7-max", qwen.model)
        assertEquals(
            listOf("qwen3.7-max", "qwen3.7-plus", "qwen3-vl-plus", "qwen-vl-max"),
            qwen.modelOptions
        )
        assertTrue(qwen.supportsVisionFor("qwen3.7-plus"))
        assertTrue(qwen.supportsVisionFor("qwen3-vl-plus"))
        assertTrue(!qwen.supportsVisionFor("qwen3.7-max"))
        assertEquals("多模态模型", qwen.modelModalityLabel("qwen3.7-plus"))
        assertEquals("文本模型", qwen.modelModalityLabel("qwen3.7-max"))
    }

    @Test
    fun extractsOnlyTheModelRecognizedSourceQuestion() {
        val solution = """
            [[TIJI_META:{"difficulty":3,"subject":"数学","questionType":"选择题","title":"函数极限"}]]
            题目识别：
            已知函数 \(f(x)=x^2\)，则下列结论正确的是（ ）。
            A。\(f(0)=0\)  B. \(f(1)=1\)

            解题思路：
            先代入自变量计算函数值。

            最终答案：B.
        """.trimIndent()

        assertEquals(
            "已知函数 \\(f(x)=x^2\\)，则下列结论正确的是（ ）。\nA。\\(f(0)=0\\)  B. \\(f(1)=1\\)",
            extractRecognizedQuestionFromSolution(solution)
        )
    }

    @Test
    fun extractsInlineMarkdownQuestionHeadingWithoutAnalysis() {
        val solution = """
            ## **题目：** 求 \(1+1\) 的值。
            ## **解题思路：**
            做加法。
            ## **最终答案：** 2
        """.trimIndent()

        assertEquals("求 \\(1+1\\) 的值。", extractRecognizedQuestionFromSolution(solution))
    }

    @Test
    fun strictOcrValidationAcceptsCompleteSourceOrderedResult() {
        val source = "【例2】设函数 f(t)=t^2，且 t∈[0,1]，求积分的值。"
        val result = AiRecognitionResult(
            title = "定积分计算",
            question = "【例2】设函数 \\(f(t)=t^2\\)，且 \\(t\\in[0,1]\\)，求积分的值。",
            answer = "1/3",
            explanation = "直接计算定积分。",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = listOf("定积分"),
            tags = emptyList(),
            difficulty = 3,
            visibleTextLines = listOf(source),
            questionSegments = listOf(
                QuestionSegment("text", "【例2】设函数 "),
                QuestionSegment("math", "f(t)=t^2"),
                QuestionSegment("text", "，且 "),
                QuestionSegment("math", "t\\in[0,1]"),
                QuestionSegment("text", "，求积分的值。")
            )
        )

        val check = validateOcrRecognition(source, result)

        assertTrue(check.valid)
        assertTrue(check.reasons.isEmpty())
    }

    @Test
    fun strictOcrValidationDoesNotRejectAFormulaRepresentationRepair() {
        val source = "【例2】设函数 f(t)=t^2，且 t∈[0,1]，求积分的值。"
        val result = AiRecognitionResult(
            title = "定积分计算",
            question = "【例2】设函数 \\(f(t)=t^2\\)，且 \\(t\\in[0,1]\\)，求积分的值。",
            answer = "1/3",
            explanation = "直接计算定积分。",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = listOf("定积分"),
            tags = emptyList(),
            difficulty = 3,
            visibleTextLines = listOf("【例2】设函数 f(t)=t^2，且 t∈[0,1]，求积分的值。")
        )

        assertTrue(validateOcrRecognition(source, result).valid)
    }

    @Test
    fun strictOcrValidationRejectsLongUnrelatedCorrection() {
        val source = "【例2】设函数 f(t)=t^2，且 t∈[0,1]，求积分的值。"
        val result = AiRecognitionResult(
            title = "函数题",
            question = "这是模型根据图形补充的无关长段落，描述横轴纵轴和波形关系，但并不是原图中印刷的题干。",
            answer = "无法确定",
            explanation = "根据题意进行分析。",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 3
        )

        val check = validateOcrRecognition(source, result)

        assertFalse(check.valid)
        assertTrue(check.feedback.contains("锚点") || check.feedback.contains("长度"))
    }

    @Test
    fun strictOcrValidationRejectsBareLatexFragmentsAndNoise() {
        val source = "求函数 f(x)=x^2 在区间 [0,1] 上的积分，并写出最终结果。"
        val result = AiRecognitionResult(
            title = "积分题",
            question = "求函数 f(x)=x^2 在区间 [0,1] 上的积分，并写出最终结果。\\omega\nx\nx\n-204",
            answer = "1/3",
            explanation = "计算积分。",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 3
        )

        val check = validateOcrRecognition(source, result)

        assertFalse(check.valid)
        assertTrue(check.feedback.contains("LaTeX") || check.feedback.contains("碎片") || check.feedback.contains("数字"))
    }

    @Test
    fun entryProtocolKeepsMissingPrintedAnswerAndExplanationEmpty() {
        val result = service.parseStructuredSolveRecognition(
            """
            [[TIJI_META:{"difficulty":0,"subject":"数学","questionType":"计算题","title":"录题"}]]
            [[TIJI_QUESTION_START]]
            已知 \(f(t)=t^2\)，求 \(f(1)\)。
            [[TIJI_QUESTION_END]]
            答案：
            解析：
            """.trimIndent()
        )

        assertEquals("已知 \\(f(t)=t^2\\)，求 \\(f(1)\\)。", result.question)
        assertEquals("", result.answer)
        assertEquals("", result.explanation)
    }

    @Test
    fun ocrValidationDoesNotWarnWhenSourceHasNoPrintedSolution() {
        val source = "已知函数 f(t)=t^2，求 f(1) 的值。"
        val result = AiRecognitionResult(
            title = "函数题",
            question = "已知函数 \\(f(t)=t^2\\)，求 \\(f(1)\\) 的值。",
            answer = "",
            explanation = "",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 2,
            visibleTextLines = listOf(source)
        )

        val check = validateOcrRecognition(source, result)

        assertTrue(check.valid)
        assertFalse(check.warning.contains("答案"))
        assertFalse(check.warning.contains("解析"))
    }

    @Test
    fun entryParserAcceptsTijiJsonSectionsAndPlainText() {
        val tiji = service.parseEntryRecognition(
            """
            [[TIJI_META:{"difficulty":0,"subject":"数学","questionType":"计算题","title":"录题","graphic":{"present":false}}]]
            [[TIJI_QUESTION_START]]
            求 \(1+1\) 的值。
            [[TIJI_QUESTION_END]]
            答案：
            解析：
            """.trimIndent()
        )
        assertEquals("求 \\(1+1\\) 的值。", tiji.question)
        assertEquals("", tiji.answer)

        val json = service.parseEntryRecognition(
            """{"title":"函数","questionText":"已知 f(x)=x^2，求 f(1)。","visibleTextLines":["已知 f(x)=x^2，求 f(1)。"],"answer":"","explanation":""}"""
        )
        assertEquals("已知 f(x)=x^2，求 f(1)。", json.question)

        val sections = service.parseEntryRecognition(
            """
            题目：
            求 \(x^2\) 在 \(x=1\) 处的值。
            答案：
            解析：
            """.trimIndent()
        )
        assertEquals("求 \\(x^2\\) 在 \\(x=1\\) 处的值。", sections.question)

        val plain = service.parseEntryRecognition("已知函数 f(t)=t^2，求 f(1) 的值。")
        assertEquals("已知函数 f(t)=t^2，求 f(1) 的值。", plain.question)
    }

    @Test
    fun entryParserUsesPrintedFieldsAndRepairsRepeatedBackslashesOnly() {
        val result = service.parseEntryRecognition(
            """{"questionText":"填空 \\\\)","printedAnswer":"\\(x=1\\)","printedExplanation":""}"""
        )

        assertEquals("填空 \\)", result.question)
        assertEquals("\\(x=1\\)", result.printedAnswer)
        assertEquals("", result.printedExplanation)
    }

    @Test
    fun plainJsonRecognitionTurnsTrailingUnderlineIntoBlankSegment() {
        val result = service.parseRecognition(
            """{"questionText":"计算积分，结果为 \\\\\\\\.","answer":"","explanation":""}"""
        )

        assertEquals(
            "计算积分，结果为 \\(\\underline{\\hspace{2.5em}}\\).",
            result.question
        )
        assertTrue(result.questionSegments.any { it.type == "blank" })
        assertFalse(result.question.contains("\\\\\\\\"))
    }

    @Test
    fun structuredSegmentsAssembleMathAndBlankWithoutRawQuestionText() {
        val evidence = service.parseVisualEvidence(
            """
            {
              "question":{
                "segments":[
                  {"type":"text","text":"求 "},
                  {"type":"math","latex":"\\int_0^1 f(t)\\\\,dt"},
                  {"type":"text","text":"，结果为 "},
                  {"type":"blank"}
                ]
              },
              "printedAnswer":{"segments":[]},
              "printedExplanation":{"segments":[]},
              "diagramEvidence":{"description":"波形标签只作隐藏证据"}
            }
            """.trimIndent()
        )

        val assembled = evidence.toRecognizedQuestion().question
        assertEquals(
            "求 \\(\\int_0^1 f(t)\\,dt\\)，结果为 \\(\\underline{\\hspace{2.5em}}\\)",
            assembled
        )
        assertFalse(assembled.contains("\\\\\\\\"))
        assertFalse(evidence.asTextEvidence().contains("\"questionText\""))
    }

    @Test
    fun visualStructuredTextRepairsUnderlineRunIntoBlankSegment() {
        val evidence = service.parseVisualEvidence(
            """
            {
              "question":{
                "segments":[
                  {"type":"text","text":"【例2】设区域 D 为 x^2+y^2 ≤ R^2，则积分结果为 \\\\\\\\."}
                ]
              }
            }
            """.trimIndent()
        )

        val recognized = evidence.toRecognizedQuestion()
        assertTrue(evidence.questionSegments.any { it.type == "blank" })
        assertEquals(
            "【例2】设区域 D 为 x^2+y^2 ≤ R^2，则积分结果为 \\(\\underline{\\hspace{2.5em}}\\).",
            recognized.question
        )
        assertFalse(recognized.question.contains("\\\\\\\\"))
    }

    @Test
    fun entryTitleRejectsGenericMetadataWithoutChangingPrintedFields() {
        val result = service.parseEntryRecognition(
            """{"title":"录题","question":"傅里叶变换的模平方积分。","answer":"42","explanation":"图片中的解析。"}"""
        )

        assertTrue(result.title != "录题")
        assertTrue(result.title.length <= 24)
        assertEquals("42", result.answer)
        assertEquals("图片中的解析。", result.explanation)
    }

    @Test
    fun ocrValidationChecksPrintedSolutionAgainstFullSourceSeparately() {
        val question = "已知函数 f(t)=t^2，求积分的值。"
        val fullSource = "$question\n答案：1/3\n解析：直接计算积分。"
        val result = AiRecognitionResult(
            title = "定积分计算",
            question = question,
            answer = "",
            explanation = "",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 2
        )

        val check = validateOcrRecognition(question, result, fullSource = fullSource)

        assertTrue(check.valid)
        assertTrue(check.warning.contains("答案"))
        assertTrue(check.warning.contains("解析"))
    }

    @Test
    fun parsesOcrReconstructionEvidenceWithoutChangingQuestionFields() {
        val result = service.parseRecognition(
            """{"title":"含参数级数敛散性判断","question":"判断级数是否收敛。","printedAnswer":"收敛","printedExplanation":"比较判别法。","formulas":["a_n"],"uncertainItems":["ann φ -> 无法确认参数"],"confidence":0.72}"""
        )

        assertEquals("判断级数是否收敛。", result.question)
        assertEquals("收敛", result.printedAnswer)
        assertEquals("比较判别法。", result.printedExplanation)
        assertEquals(listOf("a_n"), result.formulas)
        assertEquals(listOf("ann φ -> 无法确认参数"), result.uncertainItems)
        assertTrue(result.confidence > 0.7f)
    }

    @Test
    fun structuredOcrMathSegmentsKeepVariablesAndRelationsInMathStyle() {
        val result = service.parseRecognition(
            """
            {
              "title":"含参数级数敛散性判断",
              "question":{
                "segments":[
                  {"type":"text","text":"当"},
                  {"type":"math","latex":"a=1"},
                  {"type":"text","text":"时，且"},
                  {"type":"math","latex":"p>1"},
                  {"type":"text","text":"；"},
                  {"type":"math","latex":"0<p\\le 1"},
                  {"type":"text","text":"或"},
                  {"type":"math","latex":"p\\le 0"},
                  {"type":"text","text":"，当"},
                  {"type":"math","latex":"n"},
                  {"type":"text","text":"充分大时判断收敛性。"}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            "当\\(a=1\\)时，且\\(p>1\\)；\\(0<p\\le 1\\)或\\(p\\le 0\\)，当\\(n\\)充分大时判断收敛性。",
            result.question
        )
        assertFalse(result.question.contains("\\(当"))
        assertFalse(result.question.contains("\\)时\\("))
    }

    @Test
    fun semanticBreaksSurviveWhilePhysicalOcrWrapsAreMerged() {
        val result = service.parseRecognition(
            """
            {
              "title":"参数级数",
              "question":{
                "segments":[
                  {"type":"text","text":"普通题干因页面宽度\n产生的物理换行"},
                  {"type":"lineBreak"},
                  {"type":"text","text":"A. 选项一"},
                  {"type":"lineBreak"},
                  {"type":"text","text":"B. 选项二"},
                  {"type":"paragraphBreak"},
                  {"type":"text","text":"下一段："},
                  {"type":"block","latex":"\\sum_{n=1}^{\\infty} a_n"}
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(result.question.contains("普通题干因页面宽度产生的物理换行"))
        assertTrue(result.question.contains("A. 选项一\nB. 选项二"))
        assertTrue(result.question.contains("B. 选项二\n\n下一段："))
        assertTrue(result.question.contains("\\[\\sum_{n=1}^{\\infty} a_n\\]"))
    }

    @Test
    fun rejectsMathRelationsThatRemainInTextSegments() {
        val plainText = AiRecognitionResult(
            title = "参数题",
            question = "当a=1时且p>1",
            answer = "",
            explanation = "",
            subject = "数学",
            questionType = "计算题",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 2,
            questionSegments = listOf(QuestionSegment("text", "当a=1时且p>1"))
        )
        val structured = plainText.copy(
            question = "当\\(a=1\\)时且\\(p>1\\)",
            questionSegments = listOf(
                QuestionSegment("text", "当"),
                QuestionSegment("math", "a=1"),
                QuestionSegment("text", "时且"),
                QuestionSegment("math", "p>1")
            )
        )

        assertTrue(mathSegmentFormatIssues(plainText).isNotEmpty())
        assertTrue(mathSegmentFormatIssues(structured).isEmpty())
    }

    @Test
    fun contentBlockRemovalOnlyUnlinksTheSelectedPersistedPath() {
        val blocks = listOf(
            QuestionContentBlock(ContentBlockRole.QUESTION, ContentBlockKind.GRAPHIC, "/tmp/question-crop.png", order = 0),
            QuestionContentBlock(ContentBlockRole.EXPLANATION, ContentBlockKind.GRAPHIC, "/tmp/explanation-crop.png", order = 1)
        )
        val persisted = QuestionContentBlockCodec.encode(blocks)
        val decoded = QuestionContentBlockCodec.decode(persisted)
        val remaining = QuestionContentBlockCodec.removePath(decoded, "/tmp/question-crop.png")

        assertEquals(2, decoded.size)
        assertEquals(listOf("/tmp/explanation-crop.png"), remaining.map { it.path })
        assertEquals(1, QuestionContentBlockCodec.decode(QuestionContentBlockCodec.encode(remaining)).size)
    }
}
