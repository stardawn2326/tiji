# Keep Room's schema-facing types stable while R8 shrinks unused app/dependency code.
-keep class com.tiji.mistakes.data.MistakeEntity { *; }
-keep class com.tiji.mistakes.data.AppDatabase { *; }
-keep interface com.tiji.mistakes.data.MistakeDao { *; }
-keep class ai.onnxruntime.** { *; }
