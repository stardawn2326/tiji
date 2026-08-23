# AI 预设配置

设置页内置以下视觉 AI 预设：

| 服务商 | 默认接口 | 默认模型 |
| --- | --- | --- |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| DeepSeek | `https://api.deepseek.com` | `deepseek-v4-flash` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.6-flash` |
| 通义千问 | `https://dashscope-us.aliyuncs.com/compatible-mode/v1` | `qwen3-vl-plus` |

每个预设都可以在设置页继续修改模型名或切换到“自定义”。通义千问中国区接口需要将服务地址替换为你的 Workspace 专属地址；不要把 API Key 写入代码或提交到仓库，应用只通过 Android Keystore 加密保存在本机。

接口依据：

- [OpenAI API Quickstart](https://platform.openai.com/docs/quickstart/make-your-first-api-request)
- [DeepSeek API 文档](https://api-docs.deepseek.com/api/create-chat-completion)
- [Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)
- [Qwen VL OpenAI-compatible API](https://help.aliyun.com/en/model-studio/qwen-vl-compatible-with-openai)
