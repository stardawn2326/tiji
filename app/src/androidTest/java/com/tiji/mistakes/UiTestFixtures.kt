package com.tiji.mistakes

import android.content.Context
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity

internal object UiTestFixtures {
    const val title = "v1.3 UI 验收测试题"

    suspend fun insertReviewable(context: Context): Long {
        val now = System.currentTimeMillis()
        return AppDatabase.get(context).mistakeDao().upsert(
            MistakeEntity(
                title = title,
                questionText = "若 f(x)=x²，求 f'(1)。",
                userAnswer = "我写成了 1。",
                answerText = "f'(1)=2。",
                explanation = "先求导，再代入 x=1。",
                note = "记住幂函数求导规则。",
                errorReason = "概念不清,计算错误",
                subject = "数学",
                questionType = "计算题",
                tags = "函数,导数",
                difficulty = 3,
                mastery = 1,
                uploadedAt = now,
                createdAt = now,
                updatedAt = now,
                nextReviewAt = 0L,
                inReviewPlan = true
            )
        )
    }

    suspend fun delete(context: Context, id: Long) {
        AppDatabase.get(context).mistakeDao().deleteMany(listOf(id))
    }
}
