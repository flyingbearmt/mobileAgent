---
title: MobileAgent Project Plan
last_updated: 2026-02-16
status: active
---

# 目标与原则

## 总体目标
验证移动端 AI 助手在：
- 系统入口触发（Share / Quick Settings 等）
- 上下文收集（尽量完整、尽量低权限摩擦）
- Task ID 异步闭环（创建-执行-回馈）

的可行性。重点是用户体验与工程闭环，而不是模型能力。

## 核心原则
- Android：入口 + 上下文采集 + 轻量意图分类 + 状态展示/通知，不承担复杂推理
- Gateway/Agent：编排、工具执行、状态管理
- 两端通过 Task ID + 异步通信解耦（先 REST + 轮询）

# 架构（MVP 版本）

## 数据流（端到端闭环）
1. 系统入口触发（Share Sheet / QS Tile）
2. Android 采集 Context + 规则分类 Intent
3. 弹出 Bottom Sheet 让用户确认（确认后立即退出界面）
4. Android 调用 Gateway：POST /v1/tasks -> 返回 task_id
5. Android 后台轮询：GET /v1/tasks/{task_id}
6. 状态 DONE/FAILED 后发通知，用户可点开查看结果（可加 Retry/Cancel）

## 状态机
PENDING -> RUNNING -> SUCCEEDED | FAILED | CANCELLED

# API 契约（v1）

## 创建任务
POST /v1/tasks

Request:
- instruction: string
- context: object (sharedText/sourceApp/timestamp/locale/deviceState...)
- capabilities: object

Response:
- task_id: string
- status: PENDING|RUNNING|...
- stage: string
- progress: number(0..1)
- result: { type: "text", text: string } | null
- error: { code: string, message: string } | null

## 查询任务
GET /v1/tasks/{task_id}

Response: 同上

# 代码落地计划（Step-by-step Guidance）

## Milestone 0：开发环境与运行基线（1-2h）
- 目标：Android 工程可运行；Mock Gateway 可运行。
- 验收：
  - Mock Gateway 本地启动成功（能 curl 到 /v1/tasks）
  - Android 启动成功（现有 MainActivity）

### 运行 Mock Gateway（本地）
- 位置：server/mock_gateway/
- 依赖：requirements.txt
- 端口：默认 8000
- Android 模拟器访问：baseUrl = http://10.0.2.2:8000

## Milestone 1：Share Sheet 入口 + 确认 UI（Week 1）
- 新增组件：
  - entry/ShareEntryActivity（接收 ACTION_SEND 文本）
  - ui/BottomSheetConfirm（显示建议动作，确认/取消）
- 验收：
  - 任意 App 共享一段文本到 MobileAgent，可弹出确认 UI
  - 取消/确认均可快速退出

## Milestone 2：ContextCollector + IntentRuleEngine（Week 1）
- ContextCollector（MVP 必收）：
  - sharedText
  - timestamp
  - locale
  - deviceState.network（wifi/cell/offline）
  - sourceApp（best-effort）
- IntentRuleEngine（规则初版）：
  - contains("http") -> SUMMARIZE
  - length > 500 -> EXTRACT
  - else -> ASK_AGENT
- 验收：
  - 确认 UI 中能展示“建议动作”
  - Context JSON 打印/上报一致

## Milestone 3：任务下发（REST）+ WorkManager 轮询（Week 1）
- AgentApi：
  - createTask()
  - getTask()
- TaskPollWorker：
  - 定时轮询直到 DONE/FAILED（带超时）
- 验收：
  - 确认后能拿到 task_id
  - 后台轮询能最终拿到结果

## Milestone 4：Notification 回馈 + Result Viewer（Week 2）
- TaskNotification：
  - DONE/FAILED 发通知
  - 点击通知打开 ResultViewer
- 验收：
  - 后台完成后必定有通知
  - 可查看结果文本

## Milestone 5：可靠性增强（Week 2）
- Room 持久化（TaskRepository）：
  - tasks 表（task_id/status/stage/progress/result/error/created_at/updated_at）
- Retry/Cancel：
  - Retry：重新 POST /v1/tasks（保留原上下文）
  - Cancel：POST /v1/tasks/{id}/cancel（Gateway 支持后再启用）
- 验收：
  - App 重启后任务列表/结果不丢（至少最近 N 条）

## Milestone 6：Quick Settings Tile（Week 2 / 可选）
- entry/QSTileService：
  - 触发打开一个轻量输入/确认界面
- 验收：
  - 下拉快捷面板一键触发，能创建任务

# 约束与风险清单
- 剪贴板后台读取限制：MVP 建议走“显式粘贴/显式读取”
- 前台应用识别：若要准确需 Usage Stats 权限；MVP 可 best-effort
- Android 13+ 通知权限：需处理 POST_NOTIFICATIONS
- 网络：模拟器使用 10.0.2.2 访问本机

# 版本管理与迭代方式

## 计划维护
- 每次新增入口/上下文项/状态字段时，同步更新本文件：
  - API 契约（v1）
  - Milestone 验收标准
  - 风险清单

## Decision Log（重要决策记录）
- 2026-02-16：通信方式采用 REST + 轮询（MVP）
- 2026-02-16：Gateway 先用本地 Mock（FastAPI），暂不引入 OpenClaw
