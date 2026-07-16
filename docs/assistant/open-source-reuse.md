# 开源复用记录

| 仓库 | License | 可复用部分 | 适配成本 | 是否采用 |
| --- | --- | --- | --- | --- |
| [Element Plus](https://github.com/element-plus/element-plus) | MIT | 卡片、输入框、提示、按钮组件 | 已是现有前端依赖 | 是 |
| [Spring AI](https://github.com/spring-projects/spring-ai) | Apache-2.0 | Markdown 文档装载、RAG 分层设计参考 | 引入完整框架会增加依赖和配置 | 否（仅参考） |
| [LangChain4j RAG 教程](https://github.com/langchain4j/langchain4j) | Apache-2.0 | Markdown 切分和关键词/BM25 检索思路 | 引入依赖不适合当前两天交付 | 否（仅参考） |

实际复用范围为项目既有的 Element Plus 组件；没有复制第三方业务代码。检索模块依据公开教程思路在本项目中独立实现，使用字符二元组、词频和标题/分类/章节加权，不引入向量库、Docker 或第三方密钥。
