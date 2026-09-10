package com.tiji.mistakes

import android.content.Context
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity

internal object UiTestFixtures {
    const val title = "v1.3 UI 验收测试题"

    suspend fun insert(
        context: Context,
        title: String = UiTestFixtures.title,
        subject: String = "数学",
        mastery: Int = 1,
        difficulty: Int = 3,
        tags: String = "函数,导数",
        questionText: String = "若 f(x)=x²，求 f'(1)。",
        userAnswer: String = "我写成了 1。",
        answerText: String = "f'(1)=2。",
        explanation: String = "先求导，再代入 x=1。",
        note: String = "记住幂函数求导规则。",
        errorReason: String = "概念不清,计算错误",
        nextReviewAt: Long = 0L,
        reviewCount: Int = 0,
        inReviewPlan: Boolean = true
    ): Long {
        val now = System.currentTimeMillis()
        return AppDatabase.get(context).mistakeDao().upsert(
            MistakeEntity(
                title = title,
                questionText = questionText,
                userAnswer = userAnswer,
                answerText = answerText,
                explanation = explanation,
                note = note,
                errorReason = errorReason,
                subject = subject,
                questionType = "计算题",
                tags = tags,
                difficulty = difficulty,
                mastery = mastery,
                uploadedAt = now,
                createdAt = now,
                updatedAt = now,
                nextReviewAt = nextReviewAt,
                reviewCount = reviewCount,
                inReviewPlan = inReviewPlan
            )
        )
    }

    suspend fun insertReviewable(context: Context): Long = insert(context)

    suspend fun delete(context: Context, id: Long) {
        AppDatabase.get(context).mistakeDao().deleteMany(listOf(id))
    }
}
