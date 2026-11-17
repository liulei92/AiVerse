# 脚本插件

> 适用于 AiVerse 1.0.0 及以上。

脚本插件让 AI 不止能用一个固定的工具集——你可以用 JavaScript 写一个插件，向 AI 注册自定义工具，并借助 `ctx` API 访问网络、文件、剪贴板、状态存储等宿主能力。装好后这些工具会自动出现在 AI 的工具列表里，像内置工具一样被调用。

入口在「设置 → 脚本插件」。

## 脚本能干什么

- **注册自定义工具**：向 AI 增加可调用工具，AI 依据 `description` 与 `parameters` 决定是否调用。
- **网络请求**：`ctx.httpFetch`，支持 method、自定义请求头与请求体——可对接任意 HTTP 接口。
- **文件读写**：在插件自己的沙箱目录内读、写、删、列文件，防目录穿越。
- **状态与存储**：`ctx.stateGet/stateSet`（会话内内存）、`ctx.storeGet/storeSet/storeRemove`（持久化）。
- **剪贴板与通知**：读写剪贴板、弹 Toast、发系统通知。
- **调用已有工具**：`ctx.callTool` 调用 AiVerse 中自动授权的内置工具。
- **多插件隔离**：每个插件独立 QuickJS 运行时，互不干扰。

## 怎么安装

插件打包成 zip：包内**任意层级**存在 `plugin.json` 即被识别。在「设置 → 脚本插件」点右上角「安装插件」，选择 zip 包。App 会解压到应用私有目录并解析清单。

- 无危险权限的插件安装后**自动启用**；
- 含危险权限的插件安装后**不自动启用**，卡片上的权限标签为橙色，确认后再手动打开开关。

启用后，插件注册的工具在**下一条消息**即可被 AI 调用，无需重启。

### 从插件仓库安装

「设置 → 脚本插件」右上角「安装插件」菜单里还有「**从仓库**」：从上游插件仓库（`raw.githubusercontent.com/yhdmgf/aiverseRules`）拉取 `index.json` 列出可用插件，点一个下载 zip 并安装。下载按「镜像优先 → 直连回退」尝试，与 APK 下载思路一致。

### 安装权限检查

插件声明了「所有文件访问」或「通知」这类系统权限时，安装/启用会跳转对应系统设置让你先授权（如「所有文件访问」授权页、通知权限页），开启后再回到插件启用。卡片上的权限标签会标明该插件需要哪些权限；含危险权限的插件安装后不自动启用（见上）。

## AI 如何调用

- App 常驻注册一个 `plugins_list` 工具：AI 可先查看已装插件的介绍、声明的技能与 MCP server，再决定调用具体插件工具。
- 每个插件工具以 `包名_工具名` 命名（如 `my_hello_greet`），注册进统一工具注册表，与内置工具共用同一套授权机制。
- 插件清单里可声明 `skills`（推荐/依赖的技能）与 `mcpServers`（依赖的 MCP 服务器），AI 据此判断是否需要配合技能或 MCP 完成任务。

### 与 MCP 搭配

插件可在 `plugin.json` 的 `mcpServers` 里声明依赖的 MCP 服务器名。MCP 负责把外部工具接进来（见 [MCP 服务器](./mcp.md)），插件则可以经 `ctx.callTool` 调用自动授权的工具、把多个 MCP 工具编排成一个高层能力。

### 与提示词、技能搭配

插件可在 `skills` 里声明配套技能（见 [技能](./skills.md)）；技能与插件都是可选的扩展层，AI 的行为边界与工作流由提示词约束（见 [自定义提示词](./custom-prompts.md)）。三者关系：提示词定义"怎么做事"，技能沉淀"某类任务的标准流程"，插件提供"可执行的新工具"。

## 目录结构与 plugin.json

一个插件是一个目录：

```
hello/
├── plugin.json   # 清单（必需）
└── main.js       # 工具入口（默认 main.js）
```

`plugin.json` 字段：

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `package` | ✅ | 唯一标识，建议反向域名（`my.hello`）。兼作工具命名前缀 |
| `name` | ✅ | 显示名，出现在插件列表 |
| `version` | | 版本号 |
| `description` | | 一句话说明 |
| `author` | | 作者名 |
| `main` | | 工具脚本，默认 `main.js` |
| `api` | | 插件规范版本，默认 `1.0.0` |
| `permissions` | | 权限数组，**不写 = 什么都不给** |
| `skills` | | 配套技能名列表 |
| `mcpServers` | | 依赖的 MCP 服务器名列表 |
| `icon` | | 图标路径（预留） |

最小 `plugin.json`：

```json
{
  "package": "my.hello",
  "name": "Hello 插件",
  "version": "1.0.0",
  "description": "我的第一个插件",
  "main": "main.js",
  "permissions": ["network"]
}
```

## 注册工具（main.js）

工具让 AI 能调用你的代码。把工具推进全局 `$ptools` 数组：

```js
var $ptools = $ptools || [];

$ptools.push({
  name: 'greet',
  description: '向某人问好。',        // AI 靠这句话决定要不要调
  parameters: {
    type: 'object',
    properties: {
      who: { type: 'string', description: '打招呼的对象' }
    },
    required: ['who']
  },
  handler: function (args) {
    return JSON.stringify({ ok: true, message: '你好，' + args.who });
  }
});
```

### 命名与 handler 约束

- **工具名会被拼成 `包名_工具名`**（如 `my_hello_greet`），同一个插件的所有工具共用同一包名前缀，卸载时按前缀整体撤销。
- `handler` 参数是工具参数的 JSON 对象；**返回值应为字符串**（通常 `JSON.stringify(...)`），会被序列化后交给 AI。
- `handler` 是同步调用；抛出的异常由宿主捕获并作为错误返回。
- 可选 `permission` 字段：为单个工具声明所需权限。

## ctx API 与权限

`ctx` 是宿主注入到插件 JS 里的能力桥，`ctx.xxx()` 直接调用。所有能力受 `plugin.json` 的 `permissions` 控制，**不声明即不可用**；危险权限安装时需用户确认。

| 方法 | 说明 | 需要的权限 |
| --- | --- | --- |
| `ctx.showToast(msg)` | 弹短提示 | — |
| `ctx.notify(title, body)` | 发通知 | `notify` |
| `ctx.getEnv(key)` / `ctx.setEnv(key, value)` | 会话级环境变量 | — |
| `ctx.stateGet(key)` / `ctx.stateSet(key, value)` | 会话级状态（内存） | — |
| `ctx.storeGet(key)` / `ctx.storeSet(key, value)` / `ctx.storeRemove(key)` | 持久化存储（`store.properties`） | — |
| `ctx.httpFetch(url, optionsJson)` | HTTP 请求，返回 `{status, body}`；`optionsJson` 可含 `method`/`headers`/`body` | `network` |
| `ctx.fileRead(path)` / `ctx.fileList(path)` | 读沙箱文件 / 列目录 | `file` |
| `ctx.fileWrite(path, content)` / `ctx.fileDelete(path)` | 写 / 删沙箱文件 | `file:write` |
| `ctx.clipboardGet()` / `ctx.clipboardSet(text)` | 读写剪贴板 | `clipboard` |
| `ctx.callTool(name, argsJson)` | 调用 AiVerse 里自动授权的工具 | — |

权限标识：`network`、`file`、`file:write`、`clipboard`、`notify`（`server` 为预留）。危险权限为 `network`、`file:write`、`clipboard`，安装时橙色提示。

### 文件沙箱

`fileRead` / `fileWrite` / `fileDelete` / `fileList` 只能访问**插件自己的目录**，用相对路径；传绝对路径或 `..` 会被拒绝。

### 网络请求示例

```js
$ptools.push({
  name: 'fetch_json',
  description: '请求一个 JSON 接口并返回。',
  parameters: {
    type: 'object',
    properties: { url: { type: 'string', description: '接口地址' } },
    required: ['url']
  },
  handler: function (args) {
    var res = ctx.httpFetch(args.url, JSON.stringify({ method: 'GET' }));
    var parsed = JSON.parse(res);
    if (parsed.status !== 200) {
      return JSON.stringify({ ok: false, error: 'HTTP ' + parsed.status });
    }
    return parsed.body;
  }
});
```

## 打包、安装与调试

- **打包**：把含有 `plugin.json` 的目录压成 zip（`cd hello && zip -r ../hello.zip . -x '.*'`），包内任意层级含 `plugin.json` 即可。
- **安装**：设置 → 脚本插件 → 右上角「安装插件」→ 选 zip。
- **分享 / 导出**（**1.0.3 起**）：插件卡片或批量多选的「⋯」菜单里提供「分享」与「导出」——把插件目录打包成 zip，分享走系统分享面板、导出用文件选择器选保存位置；读不到插件文件会提示失败。
- **启用/禁用**：卡片右侧开关，切换即时生效（注册/注销工具）。
- **卸载**：卡片上的「卸载」，会删除插件文件，不可恢复。
- **调试**：日志可在「日志」页查看；插件加载与调用失败会记录到日志。

## 常见问题

- **工具没出现 / 调不到**：确认插件已启用；工具名是否以包名为前缀；`$ptools` 是否在 `main.js` 顶层正确 push。
- **报「缺少权限」**：`ctx.xxx` 需要的权限未在 `plugin.json` 的 `permissions` 里声明，或安装时用户没确认危险权限。
- **调用报错**：`handler` 里抛了异常，或返回了非字符串；把错误 `JSON.stringify` 后返回更利于排查。
- **路径越界**：文件操作只能用插件沙箱内的相对路径。

## 完整示例

```json
{
  "package": "demo.tool",
  "name": "示例插件",
  "version": "1.0.0",
  "description": "演示注册工具与网络能力",
  "main": "main.js",
  "permissions": ["network", "notify"]
}
```

```js
var $ptools = $ptools || [];

$ptools.push({
  name: 'ping',
  description: '测试插件是否已加载。',
  parameters: { type: 'object', properties: {}, required: [] },
  handler: function () {
    ctx.showToast('插件已响应');
    return JSON.stringify({ ok: true, pong: true });
  }
});
```

把上述两文件放进 `demo.tool/` 目录，压成 zip，在「设置 → 脚本插件」安装并启用，即可在对话中让 AI 调用 `demo_tool_ping`。
