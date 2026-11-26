<div align="center">

# nway-system

**统一入口整合业务系统，共享中台消除重复建设，自然语言驱动业务办理。**

一体化微前端操作平台（micro-app + wujie 双内核）· 全局搜索入口 · 统一身份认证（WebAuthn）· 办公智能体

新系统三步即可接入，无需重复实现登录、权限与组织能力；遗留系统无需改造即可纳入同一门户；用户可通过搜索与智能体完成日常操作，无需依赖菜单结构。

**2** 套微前端内核 · **6** 种渲染方式 · **1** 个全局搜索入口 · **3** 种登录方式 · **4** 维授权模型 · **9** 大中台能力域

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square" alt="Java" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=flat-square" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Vue-3.5-42B883?style=flat-square" alt="Vue" />
  <img src="https://img.shields.io/badge/micro--app-1.0-1E90FF?style=flat-square" alt="micro-app" />
  <img src="https://img.shields.io/badge/wujie-2.1-00BEBE?style=flat-square" alt="wujie" />
  <img src="https://img.shields.io/badge/WebAuthn-FIDO2-4B32C3?style=flat-square" alt="WebAuthn" />
  <img src="https://img.shields.io/badge/AgentScope-2.1-7C3AED?style=flat-square" alt="AgentScope" />
  <img src="https://img.shields.io/badge/License-Apache--2.0%20%2F%20MIT-blue?style=flat-square" alt="License" />
</p>

[核心设计](#核心设计概览) · [关键技术底座](#关键技术底座) · [微前端](#一微前端双内核并存) · [全局搜索](#二全局搜索统一的平台级入口) · [办公智能体](#四办公智能体从操作软件到表达意图) · [快速开始](#快速开始) · [使用指南](#使用指南) · [贡献指南](#贡献指南)

</div>

---

## 目录

- [项目概述](#项目概述)
- [核心设计概览](#核心设计概览)
- [关键技术底座](#关键技术底座)
- [核心亮点](#核心亮点)
  - [一、微前端：双内核并存](#一微前端双内核并存)
  - [二、全局搜索：统一的平台级入口](#二全局搜索统一的平台级入口)
  - [三、统一身份认证：从密码到本机生物识别](#三统一身份认证从密码到本机生物识别)
  - [四、办公智能体：从操作软件到表达意图](#四办公智能体从操作软件到表达意图)
  - [五、企业级中台能力](#五企业级中台能力)
- [技术架构](#技术架构)
- [快速开始](#快速开始)
- [使用指南](#使用指南)
- [项目结构](#项目结构)
- [配置说明](#配置说明)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

---

## 项目概述

**nway-system 是一个面向企业内网的一体化操作平台**：一个门户整合所有系统，一套中台提供全部公共能力；一个悬浮球将"菜单检索、AI 问答、业务办理"集中在手边；一个办公智能体将"查系统、填表单、走流程"简化为一句自然语言指令。

| 组成 | 定位 | 提供的能力 |
| --- | --- | --- |
| **统一门户**（`nway-system-web`） | 微前端宿主 | 统一入口、统一菜单页签、一次登录，各系统呈现为同一产品形态 |
| **能力中台**（`nway-system-service`） | 企业能力中台 | 开箱提供身份、组织、权限、租户、字典、消息、文件、审计、开放平台能力 |

### 核心设计概览

| 设计 | 解决的问题 | 设计价值 |
| --- | --- | --- |
| **双微前端内核** | 新系统、老系统、外部 SaaS 使用同一套菜单与页签 | 多数平台仅支持单一内核，业务系统不得不迁就框架限制 |
| **GlobalSearch 悬浮球**（`GlobalSearch.vue`） | 无需离开当前页面，即可完成菜单检索、AI 问询与业务办理 | 常见搜索框多仅支持菜单跳转，较少将菜单检索、页面上下文与智能体整合为统一入口 |
| **接口元数据驱动 Agent**（`chat.vue` + 后端 `ai` 模块） | 新业务能力只需配置元数据，无需发版、无需编写 Function Calling | 多数 AI 助手仅提供对话能力，实际办理需为每个接口单独开发专用工具 |

具体说明：

1. **微前端并非二选一。** iframe 的隔离性、single-spa 的组件化、wujie 的原生沙箱、micro-app 的轻量注入各有适用范围。nway-system 在宿主中同时集成两套内核，由每个菜单自行选择运行时。
2. **搜索不应止于跳转。** 悬浮球可识别当前页面，将页面文本或截图传递给 AI；简单问题在浮层内解决，复杂任务可一键转交完整对话。
3. **AI 不是外挂组件。** 智能体通过接口元数据理解业务系统，按当前用户身份与权限调用接口；写操作必须经用户确认，全程审计留痕。

### 接入前后对比

```mermaid
flowchart LR
    subgraph BEFORE["接入前：N 个系统对应 N 套登录、权限与组织"]
        direction TB
        U1(["员工"]) --> S1["OA"]
        U1 --> S2["ERP"]
        U1 --> S3["工单"]
        U1 --> S4["遗留 JSP"]
        U1 --> S5["外部 SaaS"]
        S1 -.-> R["登录 / 权限 / 组织 / 消息 / 文件<br/>各系统重复实现"]
        S2 -.-> R
        S3 -.-> R
        S4 -.-> R
        S5 -.-> R
    end
    subgraph AFTER["接入后：1 个门户 + 1 套中台"]
        direction TB
        U2(["员工"]) --> P["统一门户<br/>一次登录 · 统一菜单与页签"]
        P --> T1["OA"]
        P --> T2["ERP"]
        P --> T3["工单"]
        P --> T4["遗留 JSP"]
        P --> T5["外部 SaaS"]
        P --> AG["办公智能体"]
        P --> C["能力中台<br/>一份实现，全局复用"]
    end
```

### 四类工程债务

| 工程债务 | 具体表现 | nway-system 的解决方式 |
| --- | --- | --- |
| **入口分散** | 员工需在多个系统间重复登录、维护多套密码、查找菜单 | 统一门户、一次登录、统一菜单与页签 |
| **能力重复建设** | 登录、权限、组织、消息、文件由各团队分别实现，标准不一致 | 中台统一提供，子应用按需调用 |
| **集成方式不兼容** | 新建 Vue / React 应用、遗留 JSP、外部厂商 SaaS 难以采用同一方案接入 | 双微前端内核 + 6 种渲染方式，按系统现状选择 |
| **AI 落地缺乏切入点** | 对话机器人仅能问答，无法完成业务办理 | 接口元数据驱动智能体，按用户身份实际调用业务系统 |

### 与常见方案的定位差异

| 常见方案 | 可解决 | 无法解决 |
| --- | --- | --- |
| 单系统后台框架 | 单个系统的 CRUD、权限与代码生成 | 多系统聚合、遗留系统接入、微前端保活 |
| 单一微前端内核 | 现代子应用的集成 | 强隔离与轻量注入需求共存、老系统统一收口 |
| 纯 iframe 门户 | 接入成本低 | 页签保活、路由同步、跨域登录态、体验割裂 |
| 独立搜索框 / 命令面板 | 菜单跳转 | 无法感知当前页面、无法办理业务、跨微前端难以统一 |
| 通用 AI 助手 | 问答、总结、文档分析 | 按用户权限调用业务系统并全程留痕 |

### 子应用无需重复建设的能力

| 子应用原本需要自行实现 | 接入平台后 |
| --- | --- |
| 登录页、Token 存取与续期 | 宿主统一注入 Token，续期 / 登出 / 租户切换统一广播 |
| 菜单、路由、页签 | 平台统一菜单与页签模型，支持页签打开、关闭、重新激活刷新 |
| 按钮级权限、数据范围 | 授权中心 + 鉴权 SDK，前端 `v-permission` 指令直接使用 |
| 组织、人员、岗位查询 | 统一组织接口 |
| 消息、文件、字典 | 中台能力直接调用 |
| 工作台入口、全局搜索、收藏、AI 助手 | 门户统一提供；悬浮球跨页面可用，可附带当前页面上下文 |

### 三步接入新系统

| 步骤 | 操作 | 结果 |
| --- | --- | --- |
| ① 创建应用 | 填写 `appCode`、应用名称、前端地址、租户 | 平台识别该系统 |
| ② 配置菜单 | 填写页面地址，选择渲染方式（6 选 1） | 系统出现在门户菜单中 |
| ③ 完成接入 | 子应用读取宿主注入的 Token | 登录态、权限、页签自动生效 |

> 遗留系统无需执行第 ③ 步：选择 `iFrame` 渲染方式即可零改造纳入统一菜单。

### 适用场景

- **系统多、团队多、入口分散**的企业：统一收口，长期收益明确
- 需要以较低成本改造并统一收口**遗留系统**的 IT 部门
- 计划将**身份、权限、组织、消息、文件**作为标准能力输出的中台团队
- 希望将 **AI 真正接入业务流程**，而非仅提供问答框的团队

**不适用**：仅有一两个系统、无统一身份诉求的小型团队；无法提供 MariaDB / Redis / Nginx 基础运行环境的场景。

### 生产级默认值

- Token 仅接受 `Authorization: Bearer`，**不支持 URL 参数**；仅对可信代理采信 `X-Forwarded-For`。
- 权限采用 **fail-closed** 策略：无租户上下文、无授权或缺少内部服务令牌时一律拒绝，而非放行。
- AI 写操作**强制人工确认**；模型不能传入目标 URL；关键操作全程写入审计日志。
- 不绑定单一技术路线：两套微前端内核、三种登录方式、模块级独立许可证，可按需选择。

---

## 关键技术底座

| 技术 | 官方文档 | 定位 | 在本项目中的角色 |
| --- | --- | --- | --- |
| **micro-app** 1.0 | [jd-opensource.github.io/micro-app](https://jd-opensource.github.io/micro-app/docs.html) | 京东开源的类 WebComponent 微前端框架 | `<micro-app>` 单标签接入；JS 沙箱、样式 / 元素 / 路由隔离；`keep-alive` 保活、`keep-router-state` 路由恢复；默认 with 沙箱，可切换 iframe 沙箱 |
| **wujie** 2.1 | [wujie-micro.github.io/doc](https://wujie-micro.github.io/doc/) | 腾讯开源的 WebComponent + iframe 微前端框架 | 子应用 DOM 进入 shadowRoot、JS 进入同源 iframe；原生隔离、无 `with` 性能损耗；预加载 / 预执行、`alive` 保活、路由同步、EventBus、自动降级 |
| **WebAuthn** | [w3.org/TR/webauthn-3](https://www.w3.org/TR/webauthn-3/) | W3C 无密码认证标准（FIDO2） | 调用操作系统平台认证器完成指纹 / 面容验证，私钥与生物特征不出本机 |
| **AgentScope** 2.1 | [github.com/agentscope-ai/agentscope](https://github.com/agentscope-ai/agentscope) | 阿里开源 Agent 框架 | HarnessAgent 运行时、工具调用、HITL 人工确认、Redis 会话状态与上下文压缩 |

> 简要概括：**micro-app 将微前端接入封装为单个标签，wujie 在 iframe 之上提供受控的隔离沙箱。**

选型原则并非追求最新技术，而是**避免单一技术路线限制业务接入**：

- 微前端同时支持 `micro-app` 与 `wujie` 两套内核，子应用按自身技术栈与隔离需求选择渲染方式；
- 身份认证同时支持本地密码、外部统一身份源与 WebAuthn 生物识别，互不绑定；
- AI 通过接口元数据适配任意业务系统，而非为每个接口编写专用工具。

---

## 核心亮点

### 一、微前端：双内核并存

结论：**微前端不存在通用最优解，只有取舍。nway-system 不替业务选择技术路线，而是将两套成熟的隔离模型同时集成到宿主中，由每个菜单自行选择。**

多数微前端方案要求所有子应用统一技术选型、统一构建工具、统一路由模式。nway-system 不预设这一前提。

> 若对 wujie / micro-app 的原理尚不熟悉，建议先阅读本节末尾的官方文档，后文的取舍分析会更易理解。

#### 微前端方案演进

| 方案 | 核心原理 | 优势 | 固有代价 |
| --- | --- | --- | --- |
| **原生 iframe**（iframe 方案） | 独立浏览上下文 | JS / CSS / DOM 物理隔离；接入零成本；可同时运行多个应用 | 刷新丢失路由；弹窗受限于 iframe 边界；通信仅支持 `postMessage`；SPA 首屏白屏时间长 |
| **single-spa 系**（single-spa 方案；qiankun 等在其上补充沙箱） | 同一 window 下劫持路由 + Proxy / `with` 沙箱 | 子应用可像本地组件使用；路由统一；独立部署 | 子应用需改造；`with` 作用域链存在性能成本；隔离为逻辑级；样式仍可能冲突 |
| **wujie**（无界方案） | WebComponent 容器 + 同源 iframe 沙箱 | 原生 JS / CSS 隔离；不依赖 `with`；弹窗可覆盖全局；支持路由同步；通过共享内存通信 | 子应用需允许跨域；需处理 iframe 与 shadowRoot 的副作用 |
| **micro-app**（类 WebComponent 方案） | 类 WebComponent 自定义元素 + JS / 样式 / 元素 / 路由隔离 | 单标签接入；技术栈无关；`keep-alive`、`keep-router-state`、预加载能力完整 | 默认 `with` 沙箱存在性能成本；无 Proxy 环境需切换 iframe 沙箱 |

> iframe 本身并非问题，直接使用原生 iframe 才带来上述限制。wujie 的核心思路是**保留 iframe 的隔离优势，并逐一解决其固有缺陷**。

#### wujie 解决的问题

```text
主应用（宿主）
├─ WebComponent / shadowRoot   ← 子应用 DOM + CSS（原生样式隔离）
└─ 同源 iframe                ← 子应用 JS + location + history（原生执行隔离）
     │ document 代理：查元素、建节点、弹窗 → 统一落回 shadowRoot
     └─ 路由同步：iframe 的 pathname / hash ↔ 主应用 URL
```

- **DOM 不再割裂**：拦截 iframe 的 `document`，将 DOM 操作代理至 `shadowRoot`，弹窗、下拉、抽屉可覆盖整个宿主，而非受限于 iframe 边界。
- **刷新不丢路由**：iframe 保留子应用的 `pathname` / `hash`，路由变化同步至主应用，刷新后从 URL 恢复。
- **通信不再仅依赖 postMessage**：同源 iframe 天然共享内存，配合去中心化 EventBus，父子通信与同一页面内的组件通信一致。
- **首屏加载更快**：`preloadApp` 在空闲时预加载资源，`exec: true` 支持预执行；配合 fiber 调度避免阻塞主线程。
- **页签切换不销毁实例**：`alive` 保活模式下实例不销毁，定时任务、消息监听与长连接在后台继续工作。
- **不支持时自动降级**：无 Proxy / WebComponent 的环境自动退化为 iframe 模式，业务代码无需分支处理。
- **不依赖 `with`**：子应用代码不在 `with(proxy)` 中执行，运行时性能接近原生。

#### micro-app 的设计特点

- **单标签接入**：`<micro-app name="..." url="...">` 即一个组件，接入成本低。
- **四层隔离**：JS 沙箱、样式隔离、元素隔离、虚拟路由，以类 WebComponent 方式模拟 ShadowDOM 的隔离特性。
- **双沙箱模式**：默认 `with` 沙箱兼容性较好；需要强隔离或环境不支持 Proxy 时，可切换至 iframe 沙箱。
- **状态保持**：`keep-alive` 在子应用切走时挂起全局副作用，`keep-router-state` 使返回时停留在原页面。
- **工程化能力完整**：预加载、数据通信、插件系统，且不绑定任何前端框架。

#### 同时集成 micro-app 与 wujie 的原因

平台**同时注册并运行两套主流微前端内核**，并以菜单为单位选择渲染方式。两者并非替代关系，而是互补的隔离模型：

| 维度 | `micro-app` | `wujie` |
| --- | --- | --- |
| 沙箱基础 | 类 WebComponent + 自定义元素 | WebComponent + 同源 iframe（DOM 代理） |
| 保活策略 | `keep-alive`，隐藏时挂起全局副作用 | `alive`，实例继续运行 |
| 路由处理 | `keep-router-state` 保持子应用路由状态 | `sync` 与宿主路由同步 |
| 兼容降级 | 提供 iframe 沙箱模式 | 无 Proxy 环境自动降级为 iframe |
| 适合场景 | 追求轻量注入、路由状态保持的业务应用 | 需要强隔离、后台持续运行（定时刷新、消息监听）的应用 |

平台将选择权下放到菜单：同一门户中，A 系统可使用 `microApp`，B 系统可使用 `wujie`，二者共享同一套页签、登录态与权限体系，互不干扰。

概括而言：**micro-app 侧重轻量接入与路由状态保持，wujie 侧重强隔离与后台常驻；两者并存，业务系统无需迁就框架限制。**

> 选型建议：**需要后台常驻时选择 `wujie`，需要路由状态保持时选择 `microApp`，无 Proxy 环境交由 `wujie` 自动降级，目标站点禁止嵌入时不使用 `iFrame`。**

该组合的价值在于：**新系统可按自身技术栈选择最合适的接入方式，遗留系统可以最低成本收口，外部站点禁止嵌入时仍有可用方案。**

#### 六种渲染类型

| 渲染类型 | 隔离方式 | 适用场景 |
| --- | --- | --- |
| `native` | 平台内 Vue 页面 | 平台自建页面，零额外开销 |
| `microApp` | 注入宿主 DOM（WebComponent + 沙箱） | 现代前端应用，需要应用级隔离与保活 |
| `microApp-iframe` | micro-app 的 iframe 沙箱 | 需要强隔离，或运行环境不支持 Proxy |
| `wujie` | 注入宿主 DOM（WebComponent + iframe 沙箱） | 现代前端应用，需要后台持续运行 |
| `wujie-iframe` | wujie 降级 iframe | 兼容性优先的接入方式 |
| `iFrame` | 原生 iframe | 任何技术栈的遗留系统，零改造接入 |

#### 渲染器能力矩阵

平台对每种渲染器的能力边界进行显式声明（`src/microfront/capabilities.ts`），并在加载失败时据此提供可操作的排障建议：

| 渲染类型 | 目标站点禁止被嵌入 | 鉴权通道 | 后台保活 | 无 Proxy 环境 |
| --- | --- | --- | --- | --- |
| `microApp` | 仍可用 | 宿主推送 | 挂起全局副作用 | 需关闭沙箱 |
| `microApp-iframe` | 不可用 | 宿主推送 | 挂起全局副作用 | 可用 |
| `wujie` | 仍可用 | 宿主推送 | 继续运行 | 自动降级 |
| `wujie-iframe` | 不可用 | 宿主推送 | 继续运行 | 可用 |
| `iFrame` | 不可用 | 无 | 继续运行 | 可用 |

> 关键差异：`micro-app` 与 `wujie` 通过"拉取入口 HTML + 注入宿主 DOM"运行，**不受目标站点 `X-Frame-Options` / CSP `frame-ancestors` 限制**；`*-iframe` 与原生 `iFrame` 使用真实 iframe，目标站点禁止嵌入时不可用。平台在加载超时或失败时会直接提示改用 `microApp` / `wujie`，而非仅提示稍后重试。

#### 多实例并行与页签模型

- `micro-app` 使用 `keep-alive` + `keep-router-state`，`wujie` 使用 `alive`，页签切换不销毁子应用实例。
- 平台维护统一的页签状态：打开、切换、关闭、刷新恢复；同一页面重复打开只激活已有页签。
- 子应用可通过统一协议请求宿主**以页签方式打开业务详情页**、**关闭自身页签**、**在来源页签重新激活时刷新数据**，行为与用户点击平台菜单一致。
- 页签桥接覆盖四条通道：同源 iframe 全局入口、`wujie` EventBus、`micro-app` globalData、跨域 iframe `postMessage`；非平台环境或宿主未接入时，300ms 内返回 `false`，由子应用自行降级，不阻塞用户操作。

#### 统一登录态注入

子应用不需要自行实现登录页，也不应将 Token 拼入 URL：

| 承载方式 | 登录态获取 | 运行时同步 |
| --- | --- | --- |
| `micro-app` | `window.microApp.getData()` 读取 `token` / `user` | 宿主按应用名 `setData` 精确投递 |
| `wujie` | `window.$wujie.props` 读取 `token` / `user` | 实例级 `nway:auth-change:<appName>` + 全局 `nway:auth-change` |
| `iframe` | `nway:auth-request` / `nway:auth` 握手 | `postMessage` |
| 跨域跳转 | 一次性 `ds_token` 参数，子应用读取后立即从地址栏移除 | — |

Token 滑动续期、登出、切换工作租户均由宿主统一广播，子应用只需订阅并更新本地 `sessionStorage` 中的 `ds-token`。

#### 工程化细节

多内核长期稳定共存依赖以下关键工程处理：

- `micro-app` 使用 `router-mode="state"`，避免子应用虚拟路由污染宿主地址栏；
- 沙箱 `iframeSrc` 指向同源空白页，防止沙箱重复加载宿主应用、产生伪路由参数导致白屏；
- 为 `wujie-vue3` 补齐 Vue 3 下的卸载收尾逻辑；
- 路由守卫自动清理 `micro-app` 遗留的脏查询参数，使已白屏的 URL 可以自愈；
- 生产构建将 Vue、Element Plus、微前端运行时、工具库拆分为独立 chunk，避免单包过大。

> **延伸阅读**：无界的 [官方文档](https://wujie-micro.github.io/doc/) 与 [设计动机：基于 iframe 的全新微前端方案](https://cloud.tencent.com/developer/article/1919034)；micro-app 的 [官方文档](https://jd-opensource.github.io/micro-app/docs.html)。前者阐述了 iframe 方案、single-spa 方案与无界方案各自的取舍，后者对类 WebComponent 渲染与沙箱配置有详细说明。

---

### 二、全局搜索：统一的平台级入口

多数门户的全局搜索仅提供一个输入框：输入关键词后跳转至结果页，用户仍需继续查找。nway-system 将其实现为**常驻所有页面的悬浮入口**，同时承担菜单检索与办公智能体入口两项职责。

#### 能力说明

| 能力 | 说明 |
| --- | --- |
| **全局可用** | 登录后出现在所有页面；支持拖动并记忆位置（`nway_global_search_pos`），避免遮挡页面内容 |
| **双入口** | 悬停展开「菜单搜索」与「AI 助手」：前者用于查找功能，后者用于直接办理 |
| **快速菜单检索** | 输入 2 个字符即开始检索；支持名称包含匹配与**拼音匹配**（`pinyin-pro`），统一召回常用、系统与全部菜单 |
| **键盘操作** | `↑` / `↓` 选择，`Enter` 打开，`Esc` 关闭；结果高亮命中片段 |
| **页签行为一致** | 打开行为与点击菜单一致，统一落入页签；在"所有菜单"页打开时自动关闭该页签 |
| **轻量 AI 对话** | 复用完整对话的 SSE 流式接口，支持思考过程、工具卡片与结构化视图 |
| **页面上下文** | 可附带当前页面的**文本快照**或 **DOM 截图**，使 AI 了解用户当前查看的内容 |
| **附件与语音** | 支持拖拽 / 选择附件（最多 5 个、单个 20 MB）、语音输入与回复语音播报 |
| **会话延续** | 支持历史会话搜索与分页；可将消息、输入、附件与待确认操作一键转交完整对话页 |

#### 设计价值

其价值不在搜索框本身，而在于将三项原本分散的能力整合为统一入口：

1. **导航与智能体同处一个入口**：菜单检索与 AI 问询无需切换产品，悬浮球即统一入口。该组件挂载于宿主层，因此在 `native`、`microApp`、`wujie`、`iFrame` 页面中均可使用，不受微前端沙箱限制。
2. **页面即上下文**：`pageContext.ts` 按渲染方式定位当前页面内容——native、micro-app、wujie 的 open shadowRoot、同源 iframe 均可读取；密码框自动脱敏；文本超过 12,000 字符、截图超过 6 MP / 4.8 MB 时自动收敛。用户询问页面填写方式时，AI 可实际读取页面内容。
3. **轻量到完整的平滑升级**：简单问题在浮层内解决；涉及写操作或长会话时，可一键"打开完整对话"，上下文与待确认表单均不丢失。

```text
任意页面
  └─ 悬浮球（GlobalSearch.vue）
       ├─ 菜单搜索：名称 / 拼音 → 统一页签
       └─ AI 助手：SSE 流式 → 页面文本 / 截图 → 附件 / 语音
            └─ 完整对话：消息 + 输入 + 附件 + 待确认写操作 一键交接
```

实现位置：

- [GlobalSearch.vue](nway-system-web/src/components/GlobalSearch.vue)：悬浮球、菜单搜索、轻量 AI 面板、拖拽缩放与会话交接
- [pageContext.ts](nway-system-web/src/utils/pageContext.ts)：跨渲染方式的页面文本快照与 DOM 截图
- [App.vue](nway-system-web/src/App.vue)：全局挂载，登录后的任何页面均可调用

> 与要求用户前往独立搜索页的方案相比，本方案支持在当前页面直接完成检索与问询，减少操作路径。

### 三、统一身份认证：从密码到本机生物识别

平台将登录抽象为可扩展的认证策略，而非在登录接口中硬编码条件分支。

#### 多认证源并存

| 认证方式 | 说明 |
| --- | --- |
| 账号密码 | 平台本地 BCrypt 校验；密码策略、过期强制改密、首次登录改密 |
| 外部身份源 | 可对接企业统一身份认证；外部校验通过但本地无账号时自动补建影子账号，并保留本地管理员回退入口 |
| 生物识别 | 基于 WebAuthn 平台认证器的本机指纹 / 面容登录，与账号密码身份源解耦 |

#### 生物识别登录

该能力并非"记住密码"，也不是本地开关，而是调用**操作系统原生的平台认证器**（Windows Hello、Touch ID、Face ID 等）完成本机身份确认：

```text
登录页
  └─ client.authenticate({ challenge })
       └─ 操作系统平台认证器（指纹 / 面容）
            └─ 返回 credentialId（生物特征与私钥不出本机）
                 └─ POST /login { loginType: "bio", clientType: "pc", key: credentialId }
                      └─ BioAuthorization：按 PC / 移动端凭证映射用户
                           └─ 签发平台 JWT
```

#### 选择 WebAuthn 的原因

- **标准化**：W3C WebAuthn（FIDO2）由浏览器与操作系统共同实现，不依赖特定硬件厂商。
- **本机验证**：指纹 / 面容数据与私钥通常由 TPM、Secure Enclave 等安全硬件保管，平台只接收凭证 ID。
- **抗钓鱼攻击**：凭证与站点来源（RP ID）绑定，仿冒域名无法复用。
- **系统级体验**：直接复用 Windows Hello、Touch ID、Face ID 等系统能力，无需安装额外应用。
- **可降级**：设备不支持或验证失败时，可随时切换账号密码，不阻断登录。

设计要点：

- **与身份源解耦**：即使账号密码走外部统一身份认证，生物识别仍校验平台本地凭证（`sys_user_login_key.bio_pc` / `bio_mobile`），互不影响。
- **PC / 移动端分离**：同一用户可为不同终端绑定不同凭证。
- **体验优先**：检测到本地已绑定凭证且设备支持时，登录页自动进入生物识别模式并尝试登录；失败静默处理，用户可随时切换到账号密码。
- **安全上下文**：WebAuthn 要求 HTTPS 或 `localhost`，生产环境需启用 HTTPS。

#### JWT 会话与权限模型

- JWT 使用 HMAC-SHA 签名（算法随密钥长度自动选择 HS256/HS384/HS512），默认有效期 24 小时。
- 剩余不足 5 分钟可滑动续期，单 Token 最多续期 30 次，会话总时长上限约 30 天。
- Token 仅接受请求头 `Authorization: Bearer <token>`，**不接受 URL 参数**；仅对可信代理采信 `X-Forwarded-For`。
- 权限模型为「资源目录 + 统一授权」：支持 `ROLE` / `USER` / `POSITION` / `APP` 四种授权维度，授权项包含 `actions`、`effect` 与 `data_scope`。
- 授权变更通过版本号（`sys_authz_version`）失效缓存，`sys_grant_change` 全程留痕。
- 提供独立鉴权 SDK（`nway-system-authz-sdk`），外部系统可复用同一套功能权限与数据权限判断。

---

### 四、办公智能体：从操作软件到表达意图

平台内置的并非仅支持对话的输入框，而是一个**能够理解业务语义、调用系统接口并完成业务办理**的办公智能体。例如，用户可以直接输入："查询上周华东区销售明细并导出为 Excel。"

软件操作方式已经历两次演进，目前正进入第三阶段：

| 阶段 | 用户操作 | 系统提供 | 典型体验 |
| --- | --- | --- | --- |
| **菜单时代** | 记忆功能所在的系统、菜单与按钮 | 页面、表单、校验 | "该功能位于何处？" |
| **搜索时代** | 提炼关键词 | 索引、筛选、排序 | "结果较多，仍需人工筛选。" |
| **智能体时代** | 描述目标，确认关键动作 | 接口元数据、权限、审计、人工确认 | "智能体完成查询，用户确认结果。" |

nway-system 采用第三代交互方式，但并非由 AI 替代用户决策：AI 负责执行查询与准备工作，关键动作由用户确认。

#### 核心设计：业务接口作为元数据而非工具

传统做法是为每个 API 编写独立的 Function Calling 工具，接口数量增长后难以维护。nway-system 的方案是：

> **将业务接口抽象为可配置元数据，Agent 核心工具仅保留四个。**

```json
{
  "interfaceCode": "oa.leave.apply",
  "name": "提交请假申请",
  "description": "为当前员工发起请假流程，需提供请假类型、开始时间、结束时间与事由",
  "appCode": "oa-system",
  "httpMethod": "POST",
  "path": "/leave/apply",
  "riskLevel": "WRITE",
  "confirmRequired": true,
  "inputSchema": [
    { "key": "leaveType", "label": "请假类型", "type": "STRING", "required": true },
    { "key": "startTime", "label": "开始时间", "type": "DATETIME", "required": true },
    { "key": "endTime", "label": "结束时间", "type": "DATETIME", "required": true },
    { "key": "reason", "label": "事由", "type": "STRING", "required": true }
  ],
  "outputSchema": [
    { "key": "applyNo", "label": "申请单号", "type": "STRING" },
    { "key": "status", "label": "流程状态", "type": "STRING" }
  ],
  "searchKeywords": "请假,休假,年假,调休,事假",
  "examples": ["帮我请明天一天年假", "下周一上午请假两小时"]
}
```

接入新业务能力通常只需在管理端配置一条接口元数据并发布，**无需修改代码、无需重新部署 Agent**。

#### 核心工具

| 工具 | 职责 |
| --- | --- |
| `get_interface_config` | 按用户原话检索已发布接口，返回 `inputSchema`、必填项、候选值与置信度 |
| `query_interface` | 只读查询直返：调用结果直接作为本轮答复展示给用户，跳过模型转述 |
| `invoke_interface` | 通用调用：结果返回模型继续处理，用于分析、多次调用与写操作（写操作需用户确认） |
| `export_analysis` | 将当前对话中已展示的真实数据导出为 Word / Excel / PPT / HTML，并返回可下载文件卡片 |

`query_interface` 与 `invoke_interface` 由模型按用户意图选择：单纯查看数据走直返；用户要求分析、对比、总结，或需要基于结果继续调用接口时走通用调用。接口返回的数据保持原样，默认不触发模型的统计分析；仅在用户明确要求分析时由模型处理。

按需还可启用两个可选工具：

- `lookup_options`：字段配置了候选值来源时，按关键词查询候选（如部门、项目、请假类型）。
- `read_file`：按 `fileId` 解析 Word / Excel / PPT / PDF 等附件文本。

#### 两级接口检索

在数十至上百个业务接口的场景下，单纯的关键词匹配并不可靠。平台采用两级检索：

1. **规则召回**：字段加权、IDF、中文二元组、同义词归一化（员工/人员/用户）、问句噪声剥离（"帮我查一下…"）、写操作动词与 `riskLevel` 对齐。
2. **语义路由**：当规则最高分低于阈值（0.50）时，调用大模型在「当前用户可见的接口目录」中做语义排序；超时 3 秒自动回退规则结果。

模型**仅参与排序，不参与执行**。候选结果仍需经过发布态、租户与接口权限过滤；置信度不足时，Agent 会先向用户确认业务对象，而非直接调用。

#### 业务办理完整链路

```text
用户提问 / 上传附件
  └─ POST /nway-system/ai/chat/stream（SSE 流式）
       └─ HarnessAgent（AgentScope 2.1）
            ├─ 意图判断：业务查询 / 文件分析 / 组合场景
            ├─ get_interface_config：两级检索 + 权限过滤
            ├─ 参数校验：inputSchema、必填项、枚举候选、附件归属
            ├─ 写操作 → HITL（Human-in-the-loop）确认表单 → 用户确认 → 后端重新校验后执行
            ├─ query_interface / invoke_interface：透传用户 JWT 调用业务系统
            ├─ export_analysis：将已展示的真实数据导出为文件
            ├─ 直返查询：结果直接作为本轮答复返回，跳过模型转述
            ├─ outputSchema 约束：只允许引用已定义字段
            └─ 审计留痕（sys_audit_log）
  └─ 接口结果卡片 / Markdown + nway-view 结构化渲染
```

#### 安全边界

AI 调用系统接口的主要风险是越权访问与误操作，平台采用分层控制：

| 风险 | 控制手段 |
| --- | --- |
| 越权访问 | 用户上下文 + 接口级授权 + 数据范围；调用时透传用户 JWT，目标系统仍按本人权限校验 |
| 误提交、误删除 | `riskLevel`（READ / WRITE / HIGH_RISK）+ `confirmRequired`；写操作强制 HITL 用户确认 |
| 参数幻觉 | 严格按 `inputSchema` 校验；缺少必填信息先追问，不允许猜测 |
| 结果编造 | `outputSchema` 约束回答字段；工具报错时如实说明，不得伪造成功 |
| 导出编造 | `export_analysis` 只允许使用本轮已展示或工具返回的真实数据；数据不足先说明，不生成空文件 |
| SSRF / 任意 URL | 目标地址只能来自字典 `ai_app_code` 或受控配置，模型**不能传入 URL** |
| 密钥泄露 | 模型 API Key 使用 AES-GCM 加密存储，主密钥由环境变量注入 |
| 无限循环 | Agent 最大迭代 12 轮；禁用 Shell、Web、子代理、文件系统与动态技能工具 |
| 长结果污染上下文 | invoke_interface 结果超过 8000 字符自动截断；query_interface 直返结果不经模型、完整展示；会话超过 30 条消息触发上下文压缩 |

#### 界面能力

完整对话页（[chat.vue](nway-system-web/src/views/system/ai/chat.vue)）将"可办理"落实为可感知的交互细节：

| 能力 | 说明 |
| --- | --- |
| **模型选择** | 按租户可见模型切换，标识视觉分析、思考模式与推理深度，选择结果按用户记忆；兼容 OpenAI 协议 |
| **提示词库** | 个人 / 角色 / 系统三级提示词；星标设为快捷提示词；支持从当前对话提取；Markdown / Mermaid 富文本编辑 |
| **思考过程** | 流式展示，生成时自动展开，结束后收起 |
| **工具卡片** | 查询、调用、导出分状态展示；失败、未完成、被拒绝均有明确提示 |
| **写操作确认** | 按 `inputSchema` 生成枚举、多选、布尔、数字、日期、文本、文件、对象 / 数组等控件；未声明参数单独列出；只提交有值字段 |
| **结构化回答** | `nway-view` 自动渲染表格、指标卡、柱状 / 折线 / 饼图、详情、清单、文件列表 |
| **语音** | Web Speech API 语音输入；回复可单条或自动播报，神经音色优选、长文分句、定期唤醒 |
| **附件** | 图片直接视觉分析；Word / Excel / PPT / PDF 按需提取；上传进度、失败重试、页内预览 |
| **会话管理** | 历史搜索、分页、重命名、删除；标题自动生成 |
| **双形态** | 悬浮球轻量对话 ↔ 完整对话页，消息、输入、附件、待确认操作可无缝交接 |

> 智能化的直接体现并非新增需要学习的功能，而是减少需要记忆的操作步骤。

三项设计约束：

- **不是百科式问答**：没有对应接口或权限时，明确说明不知道或无权限，不编造答案。
- **不替代用户决策**：写操作必须由用户确认；确认后的执行流不可中断，以保证业务调用完整执行。
- **不是黑盒**：参数、结果、耗时与调用记录全程留痕，事后可查。

---

### 五、企业级中台能力

| 能力域 | 说明 |
| --- | --- |
| 组织与用户 | 用户、部门、岗位、角色、用户角色与部门关系维护 |
| 统一授权 | 资源目录、按钮级权限、功能权限、数据范围、授权变更留痕与版本失效 |
| 多租户 | `tenant_code` 数据隔离、平台管理员跨租户授权、租户级 QPS 配额 |
| 开放平台 | OAuth2 风格 Token（`client_credentials` / `user_delegate` / `authorization_code`）、SSO、限流、IP 白名单、Webhook |
| 菜单与应用 | 应用注册、菜单管理、子应用接入、低代码表单菜单同步、菜单自注册 |
| 文件中心 | 上传、下载、预览、短时票据、HTTP Range 视频预览；本地存储，S3/MinIO 预留 |
| 消息中心 | 站内消息、邮件、企业微信；按用户 / 部门 / 角色定向发送 |
| 审计与监控 | 操作审计、开放接口调用日志、在线用户、Actuator 健康检查与运行指标 |
| 基础配置 | 数据字典、系统参数、登录安全策略 |

---

## 技术架构

```mermaid
flowchart TB
    U[用户浏览器] -->|"登录：密码 / WebAuthn"| H["统一门户 nway-system-web<br/>Vue 3 · 页签 · 菜单 · 全局搜索 · 权限 · 登录态"]
    H --> N[native 平台页面]
    H --> M[micro-app 子应用]
    H --> W[wujie 子应用]
    H --> F[iframe 子应用]

    H -->|"/api · JWT"| S["能力中台 nway-system-server<br/>Spring Boot 4 · Java 21"]
    S --> DB[(MariaDB)]
    S --> RD[(Redis)]
    S --> EXT[外部业务系统]

    S --> A["办公智能体运行时<br/>AgentScope HarnessAgent"]
    A --> LLM[OpenAI 兼容模型]
    A --> T["get_interface_config<br/>query_interface<br/>invoke_interface<br/>export_analysis"]
    T --> EXT
    S --> FILE[文件存储]
```

### 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端框架 | Vue 3.5、TypeScript 6、Vite 8、Pinia 4、Vue Router 5 |
| UI | Element Plus 2.14、Sass、Element Plus Icons |
| 微前端 | [`@micro-zoe/micro-app`](https://jd-opensource.github.io/micro-app/docs.html) 1.0.0-rc.32、[`wujie-vue3`](https://wujie-micro.github.io/doc/) 2.1 |
| 全局搜索与页面理解 | [`pinyin-pro`](https://github.com/zh-lx/pinyin-pro) 3.29（拼音检索）、[`modern-screenshot`](https://github.com/qq15725/modern-screenshot) 4.7（DOM 截图）、Web Speech API（语音输入 / 播报） |
| 生物识别 | [`@passwordless-id/webauthn`](https://github.com/passwordless-id/webauthn) 2.4（WebAuthn / FIDO2） |
| 后端 | Spring Boot 4.1、Java 21、Maven 多模块、Druid、Spring Data Redis、Caffeine、jjwt 0.12、BCrypt |
| 数据访问 | [nway-jdbc](https://github.com/zdtjss/nway-jdbc) 1.7（自研单表 ORM） |
| AI 运行时 | AgentScope HarnessAgent 2.1.0-BETA1、OpenAI 兼容模型扩展、Redis / JDBC 状态存储 |
| 文档解析 | Apache POI 6、PDFBox 3 |
| 基础设施 | MariaDB、Redis、Nginx、Docker |

### 关键设计决策

| 决策 | 原因 |
| --- | --- |
| 双微前端内核共存 | 不强制子应用统一技术选型；不同接入场景选择不同隔离与保活策略 |
| 渲染器能力显式声明 | 将白屏问题转化为可诊断、可操作的提示，而非黑盒 |
| 登录态由宿主统一注入 | 子应用不接触登录流程，续期、登出、租户切换天然一致 |
| 搜索与 AI 合并为全局入口 | 用户不必在"查找功能"和"询问 AI"之间切换产品，页面本身就是上下文 |
| 页面上下文默认关闭 | 只有用户显式开启才附带文本 / 截图，避免无感上传 |
| 写操作执行流不可打断 | 确认后保证业务调用完整执行，结果以历史记录为准 |
| 接口元数据驱动 AI | 新增业务能力无需写代码、无需发版，避免工具数量随接口线性膨胀 |
| 写操作强制人工确认 | AI 可以准备和发起，但关键动作必须由人确认 |
| 权限 fail-closed | 无租户上下文、无授权、无内部服务令牌时一律拒绝，而非放行 |
| 前后端分离 + 多阶段镜像 | 前端由 Nginx 托管并反向代理后端，后端以 JRE 镜像独立部署 |

---

## 快速开始

### 最短启动路径

前提：JDK 21、Maven 3.9+、Node 20+、MariaDB、Redis 已就绪，库表已按下方第 2 步初始化。

```bash
# 1. 后端（http://localhost:9081/nway-system）
cd nway-system-service
mvn -pl nway-system-server -am clean package -DskipTests
java -jar nway-system-server/target/nway-system-server-1.0.0.jar --spring.profiles.active=local

# 2. 前端（另开终端，http://localhost:5173）
cd nway-system-web
npm install
npm run dev
```

使用 `admin / 1` 登录后，可依次体验：菜单页签 → 应用管理 → 权限工作台 → AI 对话。

> 登录后，可将鼠标移至右上角悬浮球，输入菜单名或拼音进行检索，也可直接向 AI 询问当前页面的使用方法。

### 环境要求

| 依赖 | 版本要求 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 20.19+（推荐 22 LTS） |
| MariaDB | 10.11+ |
| Redis | 7+ |
| 浏览器 | 支持 WebAuthn 的现代浏览器（生物识别需 HTTPS 或 localhost） |

### 1. 获取代码

```bash
git clone <repository-url> nway-system
cd nway-system
```

### 2. 初始化数据库

```sql
CREATE DATABASE nway_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

按文件名顺序执行 `nway-system-service/db/` 下的场景脚本（详细覆盖范围与依赖见 [db/README.md](nway-system-service/db/README.md)）：

| 脚本 | 业务场景 | 内容 | 是否必需 |
| --- | --- | --- | --- |
| `01_platform_tenant_app.sql` | 平台基座 | 租户、应用；default/public 租户与系统管理应用 | 必需 |
| `02_permission_authz.sql` | 权限与导航 | 角色、用户角色、菜单、收藏、管理员授权、资源、统一授权、视图；管理员角色与平台导航基线 | 必需 |
| `03_organization.sql` | 组织架构 | 部门、跨部门任职、岗位、用户岗位 | 必需 |
| `04_identity_user.sql` | 用户身份 | 用户、登录校验；默认管理员账号 | 必需 |
| `05_dict_config.sql` | 字典与配置 | 字典类别 / 字典项、系统配置 | 必需 |
| `06_message_center.sql` | 消息中心 | 消息主表 / 目标 / 接收人 / 发送日志 | 必需 |
| `07_file_center.sql` | 文件中心 | 文件元数据 | 使用文件能力时必需 |
| `08_open_platform.sql` | 开放平台 | 开放应用 / Webhook / 调用日志 | 使用开放平台时必需 |
| `09_audit_log.sql` | 审计日志 | 审计日志表 | 必需（审计能力） |
| `10_oa_bpm_integration.sql` | OA / BPM 集成 | OA 租户与 BPM 流程菜单 | 可选 |
| `11_ai_office_agent.sql` | 办公智能体 | AI 接口 / 模型 / 提示词 / 会话 / 消息、授权分组、菜单、应用字典 | 使用 AI 时必需 |

Bash 环境可一次执行全部必需脚本：

```bash
cd nway-system-service
for f in db/[0-9][0-9]_*.sql; do
  mysql -h 127.0.0.1 -u root -p nway_system < "$f"
done
```

可选演示数据（集团组织架构、岗位职级字典）在必需脚本之后执行：

```bash
mysql -h 127.0.0.1 -u root -p nway_system < db/demo/01_organization_demo.sql
mysql -h 127.0.0.1 -u root -p nway_system < db/demo/02_dict_demo.sql
```

> **注意**
> - `system.sql` 为早期基线脚本，字段已过时，**不适用于新环境初始化**。
> - 初始化会创建默认账号 `admin`，初始密码 `1`，首次登录后请立即修改。
> - 所有场景脚本按「先 DDL、后 DML」组织，依赖顺序为 `01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11`。
> - Windows 用户可使用 `cmd /c "mysql ... < 脚本路径"` 按文件名顺序执行，或在 MySQL 客户端中依次执行。

### 3. 启动后端

修改 `nway-system-service/nway-system-server/src/main/resources/application-local.yml` 中的数据库与 Redis 地址，或用环境变量覆盖：

```yaml
spring:
  datasource:
    druid:
      url: jdbc:mariadb://127.0.0.1:3306/nway_system?useUnicode=true&characterEncoding=UTF-8&useSSL=false
      username: nway_system
      password: <your-password>
  data:
    redis:
      host: 127.0.0.1
```

编译并启动：

```bash
cd nway-system-service
mvn -pl nway-system-server -am clean package -DskipTests
java -jar nway-system-server/target/nway-system-server-1.0.0.jar --spring.profiles.active=local
```

本地默认地址：

- 服务端口：`9081`（`application.yml` 默认 `8081`，`local` profile 覆盖为 `9081`）
- 上下文路径：`/nway-system`
- 健康检查：<http://localhost:9081/nway-system/actuator/health>

### 4. 启动前端

```bash
cd nway-system-web
npm install
npm run dev
```

访问 <http://localhost:5173>，使用 `admin / 1` 登录。

开发服务器会把 `/api` 代理到 `http://localhost:9081` 并移除 `/api` 前缀，配合 `VITE_API_URL=/api/nway-system` 命中后端上下文路径。

常用命令：

```bash
npm run dev         # 开发服务器
npm run build       # 类型检查 + 生产构建（产物在 dist/）
npm run preview     # 预览生产构建
npm run type-check  # 仅类型检查
npm run lint        # ESLint 检查并修复
npm run format      # Prettier 格式化 src/
```

### 5. Docker 部署

```bash
# 后端（在 nway-system-service 目录执行）
docker build -t nway-system-service .

# 前端（在 nway-system-web 目录执行）
docker build -t nway-system-web .
```

后端容器使用 `docker` profile，需注入数据源与 Redis 环境变量：

```bash
docker run -d --name nway-system-service \
  -e SPRING_DATASOURCE_URL='jdbc:mariadb://mariadb:3306/nway_system' \
  -e SPRING_DATASOURCE_USERNAME=nway_system \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e JWT_SECRET=<at-least-32-characters> \
  -e AI_CRYPTO_KEY=<your-ai-master-key> \
  -p 8081:8081 nway-system-service
```

前端 Nginx 镜像默认把 `/nway-system` 与 `/api` 反向代理到 `nway-system-service:8081`。

---

## 使用指南

### 接入一个子应用

| 步骤 | 操作 |
| --- | --- |
| 1. 创建应用 | 「系统管理 → 应用管理」填写 `appCode`、应用名称、前端地址（`host`）、租户归属 |
| 2. 创建菜单 | 「菜单管理」创建菜单并归属该应用；页面类型选 `PAGE`，填写子应用地址 |
| 3. 选择渲染方式 | 按子应用技术栈与部署条件选择 `microApp` / `microApp-iframe` / `wujie` / `wujie-iframe` / `iFrame` |
| 4. 获取登录态 | 子应用按承载方式读取 Token，统一写入 `sessionStorage` 的 `ds-token` |

子应用请求示例：

```js
// micro-app
const { token, user } = window.microApp.getData()

// wujie
const { token, user, appName } = window.$wujie.props

// iframe：发送握手请求
window.parent.postMessage({ type: 'nway:auth-request' }, '*')
window.addEventListener('message', (e) => {
  if (e.data && e.data.type === 'nway:auth') {
    sessionStorage.setItem('ds-token', e.data.token)
  }
})
```

请求平台接口：

```http
Authorization: Bearer <token>
```

> Token 续期与登出由宿主统一广播，子应用不应自行续期，也不应将 Token 放入 URL。

### 使用全局搜索与 AI 助手

| 目标 | 操作方式 |
| --- | --- |
| 检索菜单 | 悬停右上角悬浮球 → 菜单搜索 → 输入名称或拼音 → `↑` / `↓` 选择 → `Enter` 打开 |
| 询问当前页面 | 悬浮球 → AI 助手 → 启用"页面文本"或"页面截图" → 直接提问 |
| 携带附件提问 | 拖拽文件到输入框（最多 5 个、单个 20 MB），或点击回形针选择 |
| 语音输入 | 点击麦克风说话；回复可点击"语音播报"，完整对话页还支持自动播报 |
| 继续对话或办理 | 点击"完整对话"，消息、输入、附件与待确认操作一并转交 |

> 悬浮球位置与 AI 面板尺寸会被记忆；悬浮球仅在登录后显示，登录页不出现。

### 在平台内以页签打开详情页

子应用点击业务单据时，可请求宿主以页签方式打开目标页面，全程不新开浏览器标签：

```js
import { openPageInHost } from '@/utils/openInHost'

const opened = await openPageInHost({
  name: row.subject || row.no || '详情',
  url: row.detailsUrl // 必须是 http(s) 完整地址
})

if (!opened) {
  // 非平台环境或宿主未接入：自行降级（如应用内 iframe 页签）
}
```

关闭页签与页签重新激活刷新：

```js
import { closePage } from '@/utils/closePage'

await closePage(() => router.replace('/list'))
```

完整协议、四条通道、回执格式与可复制代码见 [子应用页签打开能力](nway-system-web/docs/子应用页签打开能力.md)。

### 开放平台调用

获取开放 Token：

```bash
curl -X POST 'http://localhost:9081/nway-system/open/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'appCode=oa-system' \
  -d 'appSecret=<appSecret>' \
  -d 'grantType=client_credentials'
```

调用开放接口：

```bash
curl 'http://localhost:9081/nway-system/open/user/getByEmployeeId?employeeId=EMP001' \
  -H 'Authorization: Bearer <access_token>'
```

说明：

- 开放 Token 默认有效期 2 小时，只能调用 `/open/*` 接口。
- 用户 JWT 与开放 Token 互不通用。
- 应用身份只以 Token 为准，`X-App-Code` 请求头已废弃。
- 支持 `client_credentials`、`user_delegate`、`authorization_code` 三种授权模式。

完整能力清单与 scope 说明见 [外部系统对接文档](nway-system-service/docs/外部系统对接文档.md)。

### 接入 AI 业务接口

1. 在「平台配置 → AI 接口配置」新增接口元数据：填写 `interfaceCode`、`appCode`、HTTP 方法、路径、风险等级、入参 / 出参 Schema、检索关键词与用户表达示例。
2. 为接口配置目标应用的后端基础地址（字典 `ai_app_code` 中的 `baseUrl`）。
3. 发布接口，并在「权限工作台」为角色 / 用户 / 岗位 / 应用授予调用权限。
4. 在「办公智能体 → AI 对话」中直接提问，或上传附件后由智能体提取信息并办理。

> 接口描述与关键词直接影响检索质量。建议采用"业务场景 + 动作"的描述方式，并补充用户常用表达（如"请假"、"休假"、"调休"）。

### 文件中心

```bash
# 上传
curl -X POST 'http://localhost:9081/nway-system/file/upload' \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@demo.pdf' \
  -F 'bizType=contract' \
  -F 'bizId=1001'

# 下载
curl -OJ 'http://localhost:9081/nway-system/file/download/<fileId>' \
  -H 'Authorization: Bearer <token>'
```

其他端点：`/file/queryPage`、`/file/getById`、`/file/preview/{id}`、`/file/ticket`、`/file/ticketDownload`、`/file/delete`。

前端可直接使用内置 SDK：

```ts
import { FileUpload, FilePreview } from '@nway/file/vue'
```

详见 [文件管理对接文档](nway-system-service/docs/文件管理对接文档.md) 与 [文件上传与预览](nway-system-web/docs/文件上传与预览.md)。

### 文档索引

| 文档 | 内容 |
| --- | --- |
| [外部系统接入身份认证文档](nway-system-service/docs/外部系统接入身份认证文档.md) | 用户登录、JWT 签发 / 校验 / 续期、登录安全策略、开放平台 Token 与 SSO |
| [外部系统对接文档](nway-system-service/docs/外部系统对接文档.md) | 应用注册、授权模式、开放 API、菜单自注册、低代码菜单同步、Webhook |
| [文件管理对接文档](nway-system-service/docs/文件管理对接文档.md) | 文件接口、存储、短时票据、`/open/file/**` 与配置 |
| [文件上传与预览](nway-system-web/docs/文件上传与预览.md) | `@nway/file` SDK、上传校验、进度 / 取消 / 重试、PDF 预览 |
| [子应用页签打开能力](nway-system-web/docs/子应用页签打开能力.md) | 页签打开 / 关闭 / 重新激活刷新、四通道协议与示例代码 |
| [无界官方文档](https://wujie-micro.github.io/doc/) | WebComponent + iframe 原理、预加载 / 预执行、保活模式、路由同步、降级方案 |
| [micro-app 官方文档](https://jd-opensource.github.io/micro-app/docs.html) | 类 WebComponent 渲染、JS 沙箱、样式 / 元素 / 路由隔离、keep-alive、keep-router-state |

---

## 项目结构

```text
nway-system/
├── nway-system-service/                  # 后端 Maven 多模块工程
│   ├── nway-system-server/               # 唯一可部署应用：启动、装配、HTTP 适配、打包
│   │   └── src/main/java/com/nway/system/
│   │       ├── config/                   # 启动配置、拦截器、全局异常
│   │       ├── common/                   # 技术底座：web / security / tenant / persistence / event / util
│   │       ├── identity/                 # 登录认证、用户、部门、岗位、角色
│   │       ├── authorization/            # 资源目录、统一授权、策略、数据范围
│   │       ├── tenant/                   # 租户与管理员跨租户授权
│   │       ├── portal/                   # 菜单、应用、收藏、低代码菜单同步
│   │       ├── message/                  # 消息中心
│   │       ├── file/                     # 文件中心
│   │       ├── metadata/                 # 字典与系统配置
│   │       ├── openplatform/             # 开放平台与 /open/**
│   │       ├── audit/                    # 审计
│   │       └── ai/                       # 办公智能体：Agent、工具、检索、调用器、SSE
│   ├── nway-system-message-api/          # 消息发送契约（按 Java 8 编译）
│   ├── nway-system-authz-sdk/            # 对外鉴权 SDK（功能权限 + 数据权限）
│   ├── db/                               # 建库脚本 01~12（必需）+ 20~30（可选 / AI）
│   ├── docs/                             # 开放平台、身份认证、文件管理对接文档
│   ├── Dockerfile                        # 后端多阶段构建
│   └── pom.xml
├── nway-system-web/                      # 前端 Vue 3 工程
│   ├── packages/file/                    # @nway/file 文件中心前端 SDK
│   ├── docs/                             # 子应用页签、文件上传与预览
│   ├── public/empty.html                 # micro-app 沙箱同源空白页
│   ├── src/
│   │   ├── api/                          # 按模块拆分的接口封装
│   │   ├── components/                   # 通用组件、AI 渲染组件、GlobalSearch 悬浮搜索
│   │   ├── directives/                   # 按钮级权限指令
│   │   ├── js/                           # 请求代理、鉴权、子应用桥接
│   │   ├── microfront/                   # 渲染器能力声明、wujie 生命周期修复
│   │   ├── router/                       # 静态路由与登录守卫
│   │   ├── stores/                       # Pinia：菜单页签、iframe 应用、AI 会话与交接
│   │   ├── utils/                        # 页面上下文、语音播报、AI 视图解析
│   │   ├── views/                        # 登录、布局、系统管理、AI 对话（system/ai/chat.vue）
│   │   ├── App.vue                       # 子应用桥接安装、全局搜索挂载
│   │   └── main.ts                       # micro-app / wujie 双内核启动
│   ├── Dockerfile                        # 前端构建 + Nginx 镜像
│   └── package.json
└── README.md
```

---

## 配置说明

### 配置文件

| 文件 | 用途 |
| --- | --- |
| `application.yml` | 公共配置：端口、JWT、文件、消息、AI、Actuator |
| `application-local.yml` | 本地开发：数据库、Redis、AI 工作目录 |
| `application-test.yml` | 测试环境 |
| `application-docker.yml` | 容器部署：数据源与 Redis 全部通过环境变量注入 |

### 常用环境变量

| 变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | 用户 JWT 签名密钥，至少 32 字符，生产必须覆盖 |
| `JWT_OPEN_SECRET` | 开放平台独立签名密钥；为空时降级使用 `JWT_SECRET` |
| `JWT_EXPIRE_MILLIS` | Token 有效期，默认 `86400000`（24 小时） |
| `JWT_REFRESH_THRESHOLD_MILLIS` | 续期阈值，默认 `300000`（5 分钟） |
| `JWT_MAX_REFRESH_COUNT` | 单 Token 最大续期次数，默认 `30` |
| `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 容器部署数据源 |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PASSWORD` | Redis 连接 |
| `EXTERNAL_AUTH_ENABLED` / `EXTERNAL_AUTH_BASE_URL` | 外部统一身份认证开关与地址 |
| `EXTERNAL_AUTH_TENANT_CODE` | 外部用户落地租户 |
| `FILE_STORAGE_TYPE` / `FILE_STORAGE_ROOT` | 文件存储类型与本地根目录 |
| `AI_CRYPTO_KEY` | AI 模型 API Key 的 AES-GCM 主密钥，生产必须配置 |
| `AI_WORKSPACE` | Agent 工作目录，多实例部署时需挂共享卷或独立目录 |
| `AI_SEARCH_LLM_ROUTING_ENABLED` | 是否启用大模型语义路由，默认 `true` |
| `AI_READ_FILE_ENABLED` | 是否允许智能体读取对话附件，默认 `true` |
| `TRUSTED_PROXIES` | 可信反向代理地址，命中才采信 `X-Forwarded-For` |

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8081`（local：`9081`） | 服务端口 |
| `server.servlet.context-path` | `/nway-system` | 所有接口统一前缀 |
| `nway.tenant.quota.internal-qps` | `1000` | 单租户内部接口 QPS 上限，`<=0` 不限制 |
| `nway.file.max-size` | `209715200` | 文件业务上限（200 MB） |
| `nway.file.ticket-ttl-seconds` | `600` | 下载 / 预览票据有效期 |
| `nway.ai.workspace` | `./data/ai-workspace` | Agent 工作目录 |
| `nway.ai.search.llm-routing-timeout-ms` | `3000` | 语义路由超时，超时回退规则检索 |
| `spring.servlet.multipart.max-file-size` | `200MB` | 单文件上传上限 |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator 暴露端点 |

> **生产部署必改**：`admin` 初始密码、`JWT_SECRET`、`JWT_OPEN_SECRET`、`AI_CRYPTO_KEY`、数据库与 Redis 密码。生产环境建议设置 `EXTERNAL_AUTH_LOCAL_FALLBACK=false`，关闭外部认证失败后的本地密码回退。

---

## 常见问题

**子应用白屏，控制台提示加载失败？**

先确认目标站点是否允许被嵌入。若响应头包含 `X-Frame-Options: DENY/SAMEORIGIN` 或 CSP `frame-ancestors`，使用 `iFrame` / `*-iframe` 渲染方式会失败，请把菜单渲染类型改为 `microApp` 或 `wujie`；同时确认目标站点已放行平台域名的跨域请求与静态资源访问。

**生物识别选项不可用？**

WebAuthn 需要浏览器与操作系统支持平台认证器，且页面运行在 HTTPS 或 `localhost` 下。设备未绑定指纹 / 面容、浏览器不支持、或使用非安全上下文时，请使用账号密码登录。

**智能体无法检索到目标接口？**

依次检查：接口是否已发布、当前用户是否被授予接口权限、租户是否匹配、接口描述与检索关键词是否覆盖用户常用表达。规则检索置信度不足时会触发语义路由，但元数据质量仍是最关键因素。

**子应用请求返回 401？**

确认 Token 来自宿主注入并写入 `sessionStorage` 的 `ds-token`，请求头使用 `Authorization: Bearer <token>`。子应用不应自行续期；续期与登出由宿主广播，收到 `nway:auth-change` 后更新本地 Token。

**页面刷新后 micro-app 子应用白屏？**

平台已使用 `router-mode="state"` 并将沙箱 `iframeSrc` 指向同源空白页来规避该问题。若仍存在历史脏参数，路由守卫会自动清理以 `app_` 开头且值等于宿主路径的查询参数。

**为什么未直接采用 qiankun / single-spa？**

它们解决的是"将多个前端拼接为一个页面"，但沙箱模型以 Proxy + `with` 为主，隔离为逻辑级，且对子应用改造有要求。本项目的接入场景既包括需要后台常驻的系统，也包括需要保留路由状态的老系统，还有禁止被 iframe 嵌入的外部站点；单一沙箱模型无法覆盖这些需求，因此选择了隔离模型更互补的 `micro-app` + `wujie`。这并非否定 qiankun 的能力，而是其不在本项目约束条件下的最优解。

**悬浮球不可见或遮挡页面内容？**

悬浮球仅在登录后显示，登录页不出现。按住图标可拖动至任意位置，位置记录在 `localStorage`；AI 面板也支持拖动与外侧角缩放。若位置或尺寸异常，清除 `nway_global_search_pos` 与 `nway_global_search_ai_size` 即可恢复默认状态。

**悬浮球中的 AI 与完整对话页有何区别？**

悬浮球定位为即时问答：无需离开当前页面，可附带当前页面文本或截图，适用于查询数据、查看页面与询问操作。完整对话页适用于长会话、写操作确认、提示词与历史管理。面板中的「完整对话」会将消息、输入、附件与待确认操作一并转交。

**AI 无法读取当前页面内容？**

页面上下文默认关闭，需要手动启用"页面文本"或"页面截图"。此外，跨域 iframe 受同源策略限制，文本和截图均可能为空；`native`、`microApp`、`wujie` 与同源 iframe 支持最佳。

---

## 贡献指南

欢迎参与贡献，包括缺陷修复、能力增强、文档完善与接入案例。

### 工作流

1. Fork 仓库并创建分支：`feat/xxx`、`fix/xxx`、`docs/xxx`。
2. 保持改动聚焦，一个 PR 只做一件事。
3. 提交前完成本地校验。
4. 提交 PR，说明背景、方案、影响范围与验证方式。

### 提交前校验

前端：

```bash
cd nway-system-web
npm run type-check
npm run lint
npm run build
```

后端：

```bash
cd nway-system-service
mvn -pl nway-system-server -am clean package
```

### 约定

- **提交信息**：遵循 Conventional Commits，如 `feat(ai): 支持按附件内容检索接口`、`fix(web): 修复页签关闭后未刷新来源页`。
- **数据库脚本**：新增脚本按序号递增，不修改已在环境中执行过的脚本；脚本需可重复执行或明确说明执行前提。
- **接口变更**：同步更新 `docs/` 下对应文档与前端接口封装。
- **子应用协议**：修改页签 / 鉴权事件名或载荷时，必须同时更新 `openInHost.js`、`closePage.js`、`subAppBridge.js`、`current.vue` 与文档。
- **安全**：不要在代码、日志、示例中提交真实密钥、Token、账号密码或生产地址。

### 提交 PR 前检查

- [ ] 改动可编译、可通过类型检查
- [ ] 新增逻辑有必要的注释与边界处理
- [ ] 涉及权限、租户、审计的改动已做 fail-closed 校验
- [ ] 文档与代码保持一致
- [ ] 未提交无关文件、构建产物或本地配置

---

## 许可证

本项目按模块采用不同许可证：

| 模块 | 许可证 | 文件 |
| --- | --- | --- |
| `nway-system-service`（后端） | Apache License 2.0 | [LICENSE](nway-system-service/LICENSE) |
| `nway-system-web`（前端） | MIT License | [LICENSE](nway-system-web/LICENSE) |

使用或分发前请阅读对应模块的 LICENSE 文件。

---

<div align="center">

**技术无界，平台有为。**

系统可以有多个，入口只需一个。

如果本项目对你有帮助，欢迎 Star、提交 Issue 与 PR。

</div>
