# 数据库脚本目录说明

本目录按**业务场景**组织数据库脚本：每个业务模块 / 业务流程的 DDL 与 DML 收敛到同一个场景文件，文件名前缀仅表示**首次初始化的推荐执行顺序**。原先按单次操作拆分的脚本（如单独加一列、单独改一条菜单）已合并到对应场景。

## 1. 目录结构

```text
db/
├── README.md                          # 本说明
├── 01_platform_tenant_app.sql         # 平台基座：租户 + 应用
├── 02_permission_authz.sql            # 权限与导航：角色 / 菜单 / 资源 / 统一授权
├── 03_organization.sql                # 组织架构：部门 / 岗位 / 任职
├── 04_identity_user.sql               # 用户身份：用户 / 登录凭证 / 默认管理员
├── 05_dict_config.sql                 # 字典与系统配置
├── 06_message_center.sql              # 消息中心
├── 07_file_center.sql                 # 文件中心
├── 08_open_platform.sql               # 开放平台
├── 09_audit_log.sql                   # 审计日志
├── 10_oa_bpm_integration.sql          # OA / BPM 集成：OA 租户 + BPM 流程菜单
├── 11_ai_office_agent.sql             # 办公智能体：AI 接口 / 模型 / 提示词 / 会话 / 消息
└── demo/                              # 可选演示数据（不参与必需初始化）
    ├── 01_organization_demo.sql       # 集团组织架构演示数据
    └── 02_dict_demo.sql               # 岗位职级 / 岗位名称演示字典
```

## 2. 场景划分与覆盖范围

| 文件 | 业务场景 | 覆盖对象 | 前置场景 | 必需性 |
| --- | --- | --- | --- | --- |
| `01_platform_tenant_app.sql` | 平台基座 | `sys_tenant`、`sys_app`；default/public 租户、nway-system 应用 | 无 | 必需 |
| `02_permission_authz.sql` | 权限与导航 | `sys_role`、`sys_user_role`、`sys_menu`、`sys_favorite_menu`、`sys_admin_tenant`、`sys_resource`、`sys_resource_action`、`sys_grant`、`sys_grant_template`、`sys_grant_change`、`sys_authz_version`、视图 `v_sys_user_role`；管理员角色、平台导航菜单、管理员全量授权、全局授权版本 | 01 | 必需 |
| `03_organization.sql` | 组织架构 | `sys_dept`、`sys_user_dept`、`sys_position`、`sys_user_position` | 01 | 必需 |
| `04_identity_user.sql` | 用户身份 | `sys_user`、`sys_user_login_key`；默认管理员 `admin`（密码 `1`）及其角色绑定 | 01、02 | 必需 |
| `05_dict_config.sql` | 字典与配置 | `sys_dict_type`、`sys_dict_item`、`sys_config` | 01 | 必需 |
| `06_message_center.sql` | 消息中心 | `message_info`、`message_target`、`message_receiver`、`message_send_log` | 01 | 必需 |
| `07_file_center.sql` | 文件中心 | `sys_file` | 01 | 使用文件能力时必需 |
| `08_open_platform.sql` | 开放平台 | `sys_open_app`、`sys_open_webhook`、`sys_open_api_log` | 01 | 使用开放平台时必需 |
| `09_audit_log.sql` | 审计日志 | `sys_audit_log` | 01 | 必需（审计能力） |
| `10_oa_bpm_integration.sql` | OA / BPM 集成 | OA 租户 `nway`；`sys_menu` 中 `app_code='bpm'` 的 13 个业务域目录 + 137 个申请页 | 01、02 | 可选（配套 OA 系统） |
| `11_ai_office_agent.sql` | 办公智能体 | `ai_interface`、`ai_model`、`ai_prompt`、`ai_conversation`、`ai_message`；AI 授权分组、办公智能体菜单、`ai_app_code` 字典 | 01、02、05 | 使用 AI 时必需 |
| `demo/01_organization_demo.sql` | 演示数据 | 集团总部 + 五大事业部 + 岗位种子数据 | 01、03 | 可选 |
| `demo/02_dict_demo.sql` | 演示数据 | `position_level`、`position_name` 字典 | 01、05 | 可选 |

补充说明：

- **平台基础导航统一在 02 维护**：组织架构、权限管理、平台配置、系统监控、开放平台等页面入口都在 `02_permission_authz.sql` 初始化；业务场景文件只负责自身数据结构，AI / BPM 的导航分别在 11 / 10 维护。
- **AI 场景一次性建表**：`11_ai_office_agent.sql` 直接包含 `ai_message.msg_id`、`ai_interface.access_mode`、`ai_model` 生成参数与最终菜单层级，执行一次即可。
- **OA/BPM 应用配置不在脚本内**：`sys_app.app_code='bpm'` 的 `host` / `api_base_url` 需按部署环境在库中配置。

## 3. 执行方式

### 3.1 首次初始化（必需脚本）

先创建数据库：

```sql
CREATE DATABASE nway_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

按文件名顺序执行根目录下的 11 个场景脚本（`demo/` 不参与）：

```bash
cd nway-system-service
for f in db/[0-9][0-9]_*.sql; do
  mysql -h 127.0.0.1 -u root -p nway_system < "$f"
done
```

Windows PowerShell：

```powershell
Get-ChildItem db\*.sql | Sort-Object Name | ForEach-Object {
  cmd /c "mysql -h 127.0.0.1 -u root -p nway_system < `"$($_.FullName)`""
}
```

依赖顺序：`01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11`。

- `01` 提供租户与应用，是其余所有初始化数据的外键语义基础。
- `02` 提供角色、菜单、资源与授权表，并写入管理员角色与平台导航。
- `04` 写入默认管理员账号，并绑定 `02` 中创建的管理员角色。
- `10`、`11` 依赖 `01` / `02`；`11` 另依赖 `05` 的字典表。
- 每个文件内部严格遵循「先 DDL、后 DML」；建表语句之间无外键依赖，因此各场景的表结构可单独执行。

### 3.2 演示数据（可选）

演示数据必须在对应场景脚本之后执行：

```bash
mysql -h 127.0.0.1 -u root -p nway_system < db/demo/01_organization_demo.sql
mysql -h 127.0.0.1 -u root -p nway_system < db/demo/02_dict_demo.sql
```

`demo/01_organization_demo.sql` 的 `dept_code` 全局唯一，重复执行会因唯一索引报错，请核对后再执行。

## 4. 维护约定

1. **新增表或初始化数据**：优先并入对应业务场景文件，不再新建「单操作」脚本。
2. **新增业务域**：新增 `12_xxx.sql` 等场景文件，同步更新本 README 的场景表和前置依赖。
3. **文件内顺序**：同一文件保持「DDL → 索引 / 视图 → 基线 DML」；大段演示数据放入 `demo/`。
4. **结构变更**：直接修改对应场景文件即可；本项目为个人使用，不维护增量迁移脚本。
5. **场景命名**：文件名使用 `NN_<业务场景>.sql`，编号只增不改，避免破坏既有执行顺序。

## 5. 原脚本迁移对照

| 原脚本 | 现位置 |
| --- | --- |
| `01_user_role_menu_tables.sql` | `sys_user` / `sys_user_login_key` → 04；`sys_app` → 01；`sys_role` / `sys_user_role` / `sys_menu` / `sys_favorite_menu` → 02 |
| `02_dept_position_tables.sql` | 03 |
| `03_message_tables.sql` | 06 |
| `04_dict_config_tables.sql` | 05 |
| `05_open_platform_tables.sql` | 08 |
| `06_audit_tenant_tables.sql` | `sys_tenant` → 01；`sys_audit_log` → 09 |
| `07_admin_grant_tables.sql` | 02 |
| `08_resource_grant_tables.sql` | 02 |
| `09_file_tables.sql` | 07 |
| `10_views.sql` | 02（`v_sys_user_role`） |
| `11_core_init_data.sql` | 租户 / 应用 → 01；角色 / 菜单 / 授权 / 授权版本 → 02；默认管理员账号与角色绑定 → 04 |
| `12_admin_menu.sql` | 02（并入平台导航） |
| `20_demo_org_data.sql` | `demo/01_organization_demo.sql` |
| `21_demo_dict_data.sql` | `demo/02_dict_demo.sql` |
| `22_oa_auth_tenant.sql` | 10（OA 租户） |
| `23_oa_bpm_process_menu.sql` | 10（BPM 菜单） |
| `24_ai_platform_tables.sql` | 11 |
| `25_ai_platform_menu.sql` | 11（按最终菜单层级写入） |
| `26_ai_app_code_dict.sql` | 11 |
| `27_ai_message.sql` | 11（按最终结构建表） |
| `28_ai_message_msg_id.sql` | 11（并入最终表结构） |
| `29_ai_interface_access_mode.sql` | 11（列与授权分组种子） |
| `30_ai_agent_ia.sql` | 11（最终菜单层级） |
| `31_ai_model_capabilities.sql` | 11（并入最终列定义） |
