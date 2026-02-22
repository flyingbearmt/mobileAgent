---
title: MobileAgent Practical Plan
last_updated: 2026-02-22
status: active
scope: practical_v1
---

# 目标

在已完成 MVP 闭环（Share -> Task -> Notification）的基础上，把 MobileAgent 推进到“更实用”的阶段：
- 输出更可靠的内容（接入真实 LLM：Ollama / OpenAI）
- 输出可执行的动作建议（结构化 actions）
- 用户二次确认后调用 Android 系统 Intent 完成事情（加日历/发短信/拨号等）

# 非目标（本阶段不做）
- 自动执行高风险操作（无确认直接发短信/拨号/写入日历）
- 长期在线后台监听剪贴板
- 多 Provider 全覆盖（先做 1 个，保留扩展点）

# 范围与优先级（Practical v1）

## 支持的任务类型
- Summarize：对分享的链接/文本做总结
- TODO：从文本中提取可执行的待办/下一步
- QA：通用问答（基于分享内容 + 用户指令）

## 支持的系统动作（actions）
- CREATE_CALENDAR_EVENT：打开系统日历插入事件页（用户确认后保存）
- SEND_SMS：打开短信草稿（用户确认后发送）
- DIAL：打开拨号盘（用户确认后拨号）

注意：本阶段不做 ACTION_CALL（需要权限且风险更高）。

# 关键设计：纯文本 vs 结构化

## 纯文本
LLM 返回自然语言结果，适合快速展示，但不稳定难解析。

## 结构化（本阶段采用）
LLM 返回 JSON，包含：summary/todos/answer + actions[]。
Android 直接按 actions 映射到 Intent，并做二次确认。

# 协议：Structured Result Schema（v1）

## Task result
Gateway 在 TaskResponse 的 result 中返回：
- type = "json"
- text = JSON 字符串（v1 先用 text 承载，Android 解析；后续可扩展为专门字段）

## JSON Schema（v1）

```json
{
  "version": 1,
  "summary": "string | null",
  "todos": [
    {"text": "string", "due_at": "ISO-8601 | null"}
  ],
  "answer": "string | null",
  "actions": [
    {
      "type": "CREATE_CALENDAR_EVENT|SEND_SMS|DIAL",
      "title": "string?",
      "start_at": "ISO-8601?",
      "end_at": "ISO-8601?",
      "to": "string?",
      "body": "string?",
      "number": "string?"
    }
  ]
}
```

约束：
- actions 必须是“可安全预览并由用户确认”的动作
- 任意字段缺失时，Android 端需要容错并降级为纯文本展示

# Step-by-step Guidance（里程碑）

## Milestone P0：实用化基线（0.5d）
目标：明确本阶段的 actions 列表与安全策略。
- 验收：
  - PRACTICAL_PLAN.md 中的 schema/动作定义冻结为 v1
  - 约束明确：所有动作必须二次确认

## Milestone P1：Gateway 接入真实 LLM（Ollama 优先）（1-2d）
目标：把 server 从 mock 输出升级为真实 LLM 输出（仍保持 /v1/tasks 契约）。

### Steps
1. 新增服务端目录：server/gateway/
2. 抽象 Provider：
   - OllamaProvider（本地）
   - OpenAIProvider（可选，后续开关）
3. 实现执行器：
   - 接收 instruction + context
   - 组织 prompt，要求输出符合 schema 的 JSON
   - 解析/校验 JSON（失败则降级：把原始文本放 summary/answer，actions 为空）
4. 任务状态存储：
   - v1：内存 dict + asyncio task
   - v2：sqlite/redis（后续）

### 验收
- Share 一段文本 -> 最终通知里显示真实总结/待办/回答（不再是 mock 文本）

## Milestone P2：Android 解析结构化结果 + 结果页展示（1d）
目标：Android 能解析 result JSON 并展示为结构化 UI。

### Steps
1. 新增 result model（kotlin data class）
2. 在 ResultViewerActivity：
   - 如果 result_text 是 JSON 且 version=1：展示 summary/todos/answer + actions 列表
   - 否则：按纯文本展示

### 验收
- 通知点开后可看到：summary / todos 列表 / actions 按钮

## Milestone P3：Action Confirm UI + Intent 执行（1-2d）
目标：用户点击 action 后，先二次确认，再跳系统 Intent。

### Steps
1. ActionConfirmDialog：展示将要执行的动作（可编辑字段可选）
2. Intent 映射：
   - CREATE_CALENDAR_EVENT -> ACTION_INSERT + CalendarContract
   - SEND_SMS -> ACTION_SENDTO + smsto:
   - DIAL -> ACTION_DIAL + tel:
3. 增加失败兜底：Intent 不可用时提示用户

### 验收
- actions 点击后能打开对应系统页面，并能完成操作

## Milestone P4：配置与部署（可选）（1d）
目标：为未来上云做准备，同时本地开发更顺滑。

### Steps
1. Android：提供 Debug 设置页配置 baseUrl/provider
2. Server：
   - env 管理 API Key（OpenAI）
   - dockerfile / systemd / pm2（二选一）

### 验收
- 真机与不同环境切换无需改代码

# 本地开发 Runbook

## 启动 Ollama（本地）
- 安装 Ollama 后，pull 一个模型，例如：qwen2.5:7b 或 llama3.1:8b
- 确认本地可访问 http://localhost:11434

## 启动 Gateway
- server/gateway 在 8001 端口监听（建议 8001，避免与 mock_gateway:8000 冲突）

## Android 模拟器访问
- baseUrl 使用 http://10.0.2.2:8001

# 风险与注意事项
- JSON 结构化输出稳定性：需要 prompt + 校验 + 降级策略
- 时区/时间解析：due_at/start_at 需要统一 ISO-8601 并明确 timezone
- 待办落地：Android 没统一 ToDo Provider，本阶段优先走“日历事件/提醒”或仅展示

# Decision Log
- 2026-02-22：Practical v1 引入 structured actions（必须二次确认）
- 2026-02-22：LLM provider 优先本地 Ollama，保留 OpenAI 扩展点
