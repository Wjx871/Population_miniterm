# 政策办理知识助手 RAG V1 设计

## 范围与边界

助手只提供系统内置业务指南和功能入口，不读取人口敏感信息、不修改业务数据、不执行审批。所有内置文档均标注 `PROJECT_GUIDE`；页面提示正式办理要求以主管部门最新规定为准。

## 处理流程

`POST /api/assistant/policy/query` 先做敏感请求拒绝，再从 `policy-knowledge` 中加载 Markdown 前置元数据和章节。检索采用中文字符二元组 BM25 风格评分，并给标题、分类、章节加权，默认返回 Top 4（代码限制为 1–8）。

若配置有效的 OpenAI-Compatible 服务，模型仅可按检索片段组织含 `[1]` 引用的答复；无密钥、超时或错误时自动返回 `RETRIEVAL_ONLY` 结构化摘要，不返回 500。响应含 `answer`、`mode`、`confidence`、`citations`、`suggestedActions`、`traceId`；引用仅使用逻辑路径。

## 前端

路由为 `/assistant/policy`，沿用 `population:view` 权限及现有路由守卫。推荐问题从接口取得；对话只留内存中最近 6 条，不写 LocalStorage。模型输出以 Vue 文本插值渲染，不使用 `v-html`。业务入口由后端固定路径和前端白名单双重限制。
