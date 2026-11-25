<template>
  <div class="ai-admin">
    <el-tabs v-model="activeTab" class="ai-tabs">
      <el-tab-pane label="接口配置" name="interface">
        <div class="toolbar">
          <el-form :inline="true">
            <el-form-item label="应用">
              <el-select v-model="query.appCode" clearable filterable placeholder="全部" style="width: 180px">
                <el-option v-for="app in apps" :key="app.appCode"
                           :label="app.appName || app.appCode" :value="app.appCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="租户">
              <el-select v-model="query.tenantCode" clearable filterable placeholder="全部" style="width: 160px">
                <el-option v-for="tenant in tenantOptions" :key="tenant.tenantCode"
                           :label="tenant.tenantName || tenant.tenantCode" :value="tenant.tenantCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已下线" value="OFFLINE" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" clearable placeholder="编码/名称/描述"
                        style="width: 220px" @keyup.enter="loadInterfaces" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadInterfaces">查询</el-button>
            </el-form-item>
          </el-form>
          <div class="toolbar-actions">
            <el-dropdown split-button type="primary" @click="openCurlDialog" @command="openCreate">
              <el-icon style="margin-right: 4px"><Link /></el-icon>从 cURL 新建
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="Plus">新建空白接口</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button @click="importVisible = true">导入</el-button>
            <el-button @click="exportAll">导出</el-button>
          </div>
        </div>

        <div class="table-wrap">
          <el-table v-loading="loading" :data="interfaces" stripe border height="100%" style="width: 100%">
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="interfaceCode" label="接口编码" min-width="150" />
            <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
            <el-table-column label="应用" width="110">
              <template #default="scope">
                <el-tooltip v-if="scope.row.appCode" :content="scope.row.appCode" placement="top">
                  <span>{{ appLabel(scope.row.appCode) }}</span>
                </el-tooltip>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="租户" width="120">
              <template #default="scope">
                <el-tag v-if="scope.row.tenantCode === 'public'" size="small" type="warning">公共租户</el-tag>
                <el-tooltip v-else-if="scope.row.tenantCode" :content="scope.row.tenantCode" placement="top">
                  <span>{{ tenantLabel(scope.row.tenantCode) }}</span>
                </el-tooltip>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="httpMethod" label="方法" width="90" />
            <el-table-column label="风险" width="120">
              <template #default="scope">
                <el-tag size="small" :type="riskTag(scope.row.riskLevel)">{{ riskLabel(scope.row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="statusTag(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="访问权限" width="160">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.accessMode === 'PUBLIC' ? 'info' : 'warning'">
                  {{ scope.row.accessMode === 'PUBLIC' ? '公开' : '受控' }}
                </el-tag>
                <span class="grant-count">
                  {{ scope.row.grantCount || 0 }} 个授权
                  <template v-if="scope.row.allGrantCount"> · 含全部</template>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="modifiedTime" label="更新时间" width="170" />
            <el-table-column label="操作" width="270" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="openPermissions(scope.row)">权限</el-button>
                <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button link type="primary" @click="openTest(scope.row)">测试</el-button>
                <el-button v-if="scope.row.status !== 'PUBLISHED'" link type="success"
                           @click="publish(scope.row)">发布</el-button>
                <el-button v-else link type="warning" @click="offline(scope.row)">下线</el-button>
                <el-button link type="danger" @click="removeInterface(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pager">
          <span class="total">共 {{ interfaces.length }} 个接口</span>
        </div>
      </el-tab-pane>

      <el-tab-pane label="模型设置" name="model">
        <div class="toolbar">
          <div class="toolbar-tip">模型密钥使用 AES-GCM 加密存储，接口只返回是否已配置。</div>
          <div class="toolbar-actions">
            <el-button type="primary" :icon="Plus" @click="openModelCreate">新增模型</el-button>
          </div>
        </div>
        <div class="table-wrap">
          <el-table v-loading="modelLoading" :data="models" stripe border height="100%" style="width: 100%">
            <el-table-column prop="name" label="配置名" min-width="140" />
            <el-table-column prop="modelName" label="模型" min-width="160" />
            <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
            <el-table-column label="默认" width="90">
              <template #default="scope">
                <el-tag v-if="scope.row.isDefault" size="small" type="success">默认</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="Key" width="100">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.apiKeyConfigured ? 'success' : 'danger'">
                  {{ scope.row.apiKeyConfigured ? '已配置' : '未配置' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="能力" width="210">
              <template #default="scope">
                <div class="capability-tags">
                  <el-tooltip v-if="scope.row.visionSupported"
                              :content="scope.row.supportsVision == null ? '视觉：按模型名自动识别' : '视觉：手动配置'"
                              placement="top">
                    <el-tag size="small" type="success">视觉</el-tag>
                  </el-tooltip>
                  <el-tag v-if="scope.row.supportsToolCalling" size="small" type="primary">工具</el-tag>
                  <el-tag v-if="modelThinkingActive(scope.row)" size="small" type="warning">思考</el-tag>
                  <el-tag v-if="scope.row.reasoningEffort" size="small" type="info">
                    {{ scope.row.reasoningEffort }}
                  </el-tag>
                  <span v-if="!scope.row.visionSupported && !scope.row.supportsToolCalling
                               && !modelThinkingActive(scope.row) && !scope.row.reasoningEffort">-</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="openModelEdit(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="removeModel(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 接口编辑 -->
    <el-dialog v-model="editVisible" :width="editFullscreen ? '100%' : '1080px'" :fullscreen="editFullscreen"
               :close-on-click-modal="false" draggable align-center>
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ form.id ? '编辑接口' : '新建接口' }}</span>
          <el-button link :icon="editFullscreen ? ScaleToOriginal : FullScreen"
                     @click="editFullscreen = !editFullscreen">
            {{ editFullscreen ? '退出全屏' : '全屏' }}
          </el-button>
        </div>
      </template>
      <div class="dialog-body" :style="{ maxHeight: editFullscreen ? 'calc(100vh - 160px)' : '68vh' }">
      <el-form label-width="110px" label-position="left">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="接口编码" required>
              <el-input v-model="form.interfaceCode" placeholder="bpm_visit_factory_submit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="2"
                        placeholder="可选。面向模型的场景/动作描述，例如：提交访厂体验申请并发起审批流程" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="应用编码" required>
              <el-select v-model="form.appCode" filterable placeholder="请选择系统应用" style="width: 100%">
                <el-option v-for="app in appOptions" :key="app.appCode"
                           :label="appOptionLabel(app)" :value="app.appCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="归属租户">
              <el-select v-model="form.tenantCode" filterable clearable :placeholder="tenantPlaceholder"
                         style="width: 100%">
                <el-option v-for="tenant in tenantSelectOptions" :key="tenant.tenantCode"
                           :label="tenantOptionLabel(tenant)" :value="tenant.tenantCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="HTTP 方法" required>
              <el-select v-model="form.httpMethod" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Content-Type">
              <el-input v-model="form.contentType" placeholder="application/json" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="风险等级" required>
              <el-select v-model="form.riskLevel" style="width: 100%">
                <el-option label="只读 READ" value="READ" />
                <el-option label="写入 WRITE" value="WRITE" />
                <el-option label="高危 HIGH_RISK" value="HIGH_RISK" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="需要确认">
              <el-switch v-model="form.confirmRequired" :disabled="form.riskLevel !== 'READ'" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="访问模式">
              <el-radio-group v-model="form.accessMode">
                <el-radio value="PUBLIC">公开（具备 AI 对话权限即可调用）</el-radio>
                <el-radio value="RESTRICTED">受控（需在“权限”中授权）</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="路径" required>
              <el-input v-model="form.path" placeholder="/bpmVisit/saveOrApprovalVisit" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="检索关键词">
              <el-input v-model="form.searchKeywords" placeholder="同义词/简称/业务黑话，多个可用空格或逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="用户表达示例">
              <el-input v-model="examplesText" type="textarea" :rows="2"
                        placeholder="每行一条，例如：帮我提交访厂申请" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">inputSchema（JSON 数组，可空；为空时参数原样透传）</el-divider>
        <el-input v-model="inputSchemaText" type="textarea" :rows="10" class="code-input" />
        <el-divider content-position="left">outputSchema（JSON 数组，可空；为空时原样返回目标响应）</el-divider>
        <el-input v-model="outputSchemaText" type="textarea" :rows="5" class="code-input"
                  placeholder='[{"key": "msg", "label": "结果消息", "type": "STRING"}]' />
        <el-divider content-position="left">invokeConfig（JSON 对象，可空）</el-divider>
        <el-input v-model="invokeConfigText" type="textarea" :rows="7" class="code-input" />
      </el-form>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveInterfaceConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 接口调用测试 -->
    <el-dialog v-model="testVisible" title="接口调用测试" width="760px" :close-on-click-modal="false">
      <div v-if="testRow" class="test-meta">
        <el-tag size="small">{{ testRow.name }}</el-tag>
        <el-tag size="small" type="info">{{ testRow.httpMethod }} {{ testRow.path }}</el-tag>
        <el-tag size="small" type="warning">{{ appLabel(testRow.appCode) }}</el-tag>
        <el-tag size="small" :type="riskTag(testRow.riskLevel)">{{ riskLabel(testRow.riskLevel) }}</el-tag>
        <el-tag size="small" :type="testRow.status === 'PUBLISHED' ? 'success' : 'info'">
          {{ statusLabel(testRow.status) }}
        </el-tag>
      </div>
      <div v-if="testRow" class="test-target">
        <span class="test-target-label">目标地址</span>
        <span class="test-target-url">{{ testTargetUrl }}</span>
      </div>
      <div class="import-tip">
        仅需填写模型可见字段；USER_CONTEXT / CONSTANT 字段由后端自动注入。基础路径取自字典 ai_app_code 的 baseUrl，与 path 拼接后发往目标应用。
      </div>
      <div class="test-section-title">
        <span>请求参数（JSON）</span>
        <el-button link type="primary" @click="resetTestArgs">按 schema 重置</el-button>
      </div>
      <el-input v-model="testArgsText" type="textarea" :rows="10" class="code-input" />
      <div class="test-section-title result-title">
        <span>调用结果</span>
        <template v-if="testResult">
          <el-tag size="small" :type="testResult.success ? 'success' : 'danger'">
            {{ testResult.success ? '成功' : '失败' }}
          </el-tag>
          <span v-if="testResult.latencyMs !== undefined" class="test-latency">{{ testResult.latencyMs }} ms</span>
        </template>
      </div>
      <pre v-if="testResult" class="test-result">{{ testResultText }}</pre>
      <div v-else class="test-empty">尚未发起调用</div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">发送测试</el-button>
      </template>
    </el-dialog>

    <!-- 接口权限 -->
    <el-dialog v-model="permissionVisible" title="接口权限" width="900px" :close-on-click-modal="false">
      <div v-if="permissionDetail" class="permission-meta">
        <el-tag size="small">{{ permissionDetail.interfaceCode }}</el-tag>
        <span class="permission-resource">资源：{{ permissionDetail.resourceCode }}</span>
        <el-tag v-if="!permissionDetail.resourceRegistered" size="small" type="info">未登记（保存授权时自动登记）</el-tag>
        <el-tag v-else size="small" type="success">{{ permissionDetail.resourceTenantScope }}</el-tag>
      </div>
      <el-form label-width="90px" label-position="left">
        <el-form-item label="访问模式">
          <el-radio-group v-model="permissionForm.accessMode">
            <el-radio value="PUBLIC">公开（具备 AI 对话权限即可调用）</el-radio>
            <el-radio value="RESTRICTED">受控（需授权）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="test-section-title">
        <span>授权清单</span>
        <el-button link type="primary" @click="addPermissionRow">添加授权</el-button>
      </div>
      <el-table :data="permissionForm.grants" size="small" border class="permission-table">
        <el-table-column label="作用域" width="130">
          <template #default="{ row }">
            <el-select v-model="row.scope" style="width: 100%">
              <el-option label="本接口" value="INTERFACE" />
              <el-option label="全部 AI 接口" value="ALL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="主体类型" width="110">
          <template #default="{ row }">
            <el-select v-model="row.subjectType" style="width: 100%" @change="row.subjectId = ''">
              <el-option label="角色" value="ROLE" />
              <el-option label="用户" value="USER" />
              <el-option label="岗位" value="POSITION" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="主体" min-width="240">
          <template #default="{ row }">
            <el-select v-if="row.subjectType === 'ROLE'" v-model="row.subjectId" filterable
                       placeholder="选择角色" style="width: 100%">
              <el-option v-for="role in roleOptions" :key="role.id"
                         :label="`${role.roleName || role.roleCode}（${role.roleCode}）`" :value="role.id" />
            </el-select>
            <el-select v-else-if="row.subjectType === 'POSITION'" v-model="row.subjectId" filterable
                       placeholder="选择岗位" style="width: 100%">
              <el-option v-for="position in positionOptions" :key="position.id"
                         :label="`${position.positionName || position.positionCode}（${position.positionCode}）`"
                         :value="position.id" />
            </el-select>
            <el-select v-else v-model="row.subjectId" filterable remote reserve-keyword
                       :remote-method="searchUsers" :loading="userLoading"
                       placeholder="输入工号/姓名搜索" style="width: 100%">
              <el-option v-for="user in userOptions" :key="user.employeeId"
                         :label="`${user.realName || user.employeeId}（${user.employeeId}）`"
                         :value="user.employeeId" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="效果" width="110">
          <template #default="{ row }">
            <el-select v-model="row.effect" style="width: 100%">
              <el-option label="允许" value="ALLOW" />
              <el-option label="拒绝" value="DENY" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" @click="permissionForm.grants.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="permission-tip">
        “全部 AI 接口”写入 ai:* 分组授权，不会影响 nway-system 的其他资源；拒绝（DENY）优先于允许。
        <template v-if="permissionForm.accessMode === 'PUBLIC'">
          当前为公开模式，授权清单仅作预配置，切换为受控后生效。
        </template>
      </div>

      <div class="test-section-title"><span>权限诊断</span></div>
      <div class="permission-check">
        <el-input v-model="checkEmployeeId" placeholder="输入工号，检查能否调用" style="width: 220px"
                  @keyup.enter="runPermissionCheck" />
        <el-button :loading="checking" @click="runPermissionCheck">检查</el-button>
        <template v-if="permissionCheck">
          <el-tag :type="permissionCheck.allowed ? 'success' : 'danger'">
            {{ permissionCheck.allowed ? '可调用' : '不可调用' }}
          </el-tag>
          <span class="permission-reason">
            reason={{ permissionCheck.reason }}
            <template v-if="permissionCheck.decisionId"> · decisionId={{ permissionCheck.decisionId }}</template>
            · 本接口授权 {{ permissionCheck.grantCount }} · 全部接口授权 {{ permissionCheck.allGrantCount }}
          </span>
        </template>
      </div>
      <template #footer>
        <el-button @click="permissionVisible = false">关闭</el-button>
        <el-button type="primary" :loading="permissionSaving" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importVisible" title="导入接口配置" width="760px">
      <div class="import-tip">
        支持单个对象、数组，或 <code>{"version":"2.0","interfaces":[...]}</code>。
        导入后为草稿状态，需人工审核后发布。
      </div>
      <el-input v-model="importText" type="textarea" :rows="14" class="code-input" placeholder="粘贴 ai_interface_seed.json 内容" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 从 cURL 新建 -->
    <el-dialog v-model="curlVisible" width="760px" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-title-group">
          <span class="dialog-title">从 cURL 新建接口</span>
          <el-tooltip placement="bottom" effect="dark" :show-after="200">
            <template #content>
              粘贴 curl 命令，自动提取方法、路径、查询参数、请求头与请求体并生成 inputSchema。<br />
              支持 bash（\ 续行、单引号）、Windows CMD（^ 续行、\" 转义）与 PowerShell（` 续行）。<br />
              可选粘贴一次真实响应 JSON，用于校准 codePath/dataPath 并生成 outputSchema。
            </template>
            <el-icon class="tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
      </template>
      <el-input v-model="curlText" type="textarea" :rows="10" class="code-input"
                placeholder="curl -X POST 'http://127.0.0.1:9081/nway-system/bpmVisit/saveOrApprovalVisit' -H 'Content-Type: application/json' -d '{&quot;visitCustomer&quot;:&quot;ACME&quot;}'" />
      <div class="test-section-title" style="margin-top: 12px">
        <span>响应示例（可选，JSON）</span>
      </div>
      <el-input v-model="curlResponseText" type="textarea" :rows="8" class="code-input"
                placeholder="粘贴一次成功调用的响应，例如 {&quot;code&quot;:200,&quot;msg&quot;:&quot;success&quot;,&quot;data&quot;:{&quot;visitId&quot;:123}}" />
      <template #footer>
        <el-button @click="curlVisible = false">取消</el-button>
        <el-button type="primary" @click="createFromCurl">解析并填充</el-button>
      </template>
    </el-dialog>

    <!-- 模型编辑 -->
    <el-dialog v-model="modelVisible" :title="modelForm.id ? '编辑模型' : '新增模型'" width="720px"
               :close-on-click-modal="false">
      <el-form label-width="120px" label-position="left" class="model-form">
        <el-divider content-position="left">基础配置</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="配置名" required>
              <el-input v-model="modelForm.name" placeholder="生产环境 GPT" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名" required>
              <el-input v-model="modelForm.modelName" placeholder="gpt-4o-mini" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Base URL" required>
              <el-input v-model="modelForm.baseUrl" placeholder="https://api.openai.com/v1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="API Key">
              <el-input v-model="modelForm.apiKey" type="password" show-password
                        :placeholder="modelForm.id ? '留空表示不修改' : ''" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="modelForm.status" active-value="ACTIVE" inactive-value="DISABLED"
                         active-text="激活" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设为默认">
              <el-switch v-model="modelForm.isDefault" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">能力与推理</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="视觉分析">
              <el-select v-model="modelForm.visionMode" style="width: 100%">
                <el-option label="自动识别（按模型名）" value="AUTO" />
                <el-option label="支持" value="ON" />
                <el-option label="不支持" value="OFF" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支持工具调用">
              <el-switch v-model="modelForm.supportsToolCalling" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="思考模式">
              <el-select v-model="modelForm.thinkingMode" style="width: 100%">
                <el-option label="自动（模型默认）" value="AUTO" />
                <el-option label="开启" value="ON" />
                <el-option label="关闭" value="OFF" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="推理深度">
              <el-select v-model="modelForm.reasoningEffort" clearable filterable allow-create
                         default-first-option placeholder="不指定" style="width: 100%">
                <el-option label="low（低）" value="low" />
                <el-option label="medium（中）" value="medium" />
                <el-option label="high（高）" value="high" />
                <el-option label="max（最高）" value="max" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="思考预算">
              <el-input-number v-model="modelForm.thinkingBudget" :min="0" :max="128000" :step="256"
                               placeholder="不指定" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-tip">
          思考模式关闭时不发送推理深度与思考预算；若模型默认开启思考，可在下方额外参数中按厂商要求关闭
          （如 {"enable_thinking": false}）。
        </div>

        <el-divider content-position="left">采样与惩罚</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  Temperature
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      采样温度，范围 0~2。值越高输出越发散、有创造性；越低越稳定、可复现。<br />
                      建议：精确问答 0~0.3，创意写作 0.7~1.0。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.temperature" :min="0" :max="2" :step="0.1"
                               :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  Top P
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      核采样阈值，范围 0~1。仅从累计概率达到该值的最小候选词集合中采样，值越小越保守。<br />
                      通常与 Temperature 二选一调整，不建议同时大幅修改；留空则使用模型默认值。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.topP" :min="0" :max="1" :step="0.05"
                               :precision="2" placeholder="不指定" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  频率惩罚
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      范围 -2~2。按 token 在已生成内容中出现的次数累加惩罚，降低重复用词的概率，值越大越不容易重复。<br />
                      留空则不发送该参数，使用模型默认值。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.frequencyPenalty" :min="-2" :max="2" :step="0.1"
                               :precision="2" placeholder="不指定" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  存在惩罚
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      范围 -2~2。token 一旦出现过就施加固定惩罚，鼓励模型引入新词、新话题，值越大越倾向展开新内容。<br />
                      留空则不发送该参数，使用模型默认值。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.presencePenalty" :min="-2" :max="2" :step="0.1"
                               :precision="2" placeholder="不指定" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  Max Tokens
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      单次生成的最大 token 数（不包含输入）。设置过小可能导致回答被截断，范围 256~128000。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.maxTokens" :min="256" :max="128000" :step="256"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span class="label-tip">
                  最大上下文
                  <el-tooltip placement="top" effect="dark" :show-after="200">
                    <template #content>
                      模型支持的上下文窗口 token 总数（输入 + 输出），用于上下文裁剪与用量估算。<br />
                      留空则按模型名自动识别。
                    </template>
                    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </span>
              </template>
              <el-input-number v-model="modelForm.contextWindowTokens" :min="1000" :max="2000000"
                               :step="1000" placeholder="自动识别" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">额外请求参数（JSON 对象，可空）</el-divider>
        <el-input v-model="modelForm.extraBody" type="textarea" :rows="4" class="code-input"
                  placeholder='例如 {"enable_thinking": false} 或 {"thinking": {"type": "disabled"}}' />
      </el-form>
      <template #footer>
        <el-button @click="modelVisible = false">取消</el-button>
        <el-button type="primary" :loading="modelSaving" @click="saveModelConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, Link, Plus, QuestionFilled, ScaleToOriginal } from '@element-plus/icons-vue'
import {
  checkInterfacePermission, deleteInterface, deleteModel, exportInterfaces, getInterface,
  getInterfacePermissions, importInterfaces,
  listInterfaceApps, listInterfaceTenants, listInterfaces, listModels, offlineInterface,
  previewInterfacePermissions, publishInterface,
  saveInterface, saveInterfacePermissions, saveModel, testInterface,
  type AiFieldDef, type AiInterfaceAppOption, type AiInterfaceConfig, type AiInterfaceVo,
  type AiInvokeResultVo, type AiModelVo, type AiOutputField, type AiOutputItem,
  type AiPermissionCheck, type AiPermissionDetail, type AiPermissionGrantPayload, type AiTenantOption
} from '@/api/ai'
import { queryByCode } from '@/api/dict'
import { queryPage as queryRolePage, type RoleDto } from '@/api/role'
import { queryPage as queryUserPage, type UserDto } from '@/api/user'
import { queryAll as queryAllPositions, type PositionDto } from '@/api/position'
import { getTenantName } from '@/utils/auth'
import { parseCurl, type ParsedCurl } from '@/utils/curlParser'

const activeTab = ref('interface')
const loading = ref(false)
const saving = ref(false)
const interfaces = ref<AiInterfaceVo[]>([])
const query = reactive({ status: '', keyword: '', appCode: '', tenantCode: '' })

const editVisible = ref(false)
const editFullscreen = ref(false)
const form = reactive<any>({
  id: '',
  interfaceCode: '',
  name: '',
  description: '',
  appCode: '',
  tenantCode: '',
  httpMethod: 'POST',
  path: '',
  contentType: 'application/json',
  riskLevel: 'READ',
  confirmRequired: false,
  accessMode: 'PUBLIC',
  searchKeywords: ''
})
// 应用选项来自字典 ai_app_code（多字段：appCode=应用编码，appName=应用名称，baseUrl=域名+contextPath），
// 并合并 sys_app 的租户信息，使公共租户（public）下的应用可被识别为「公共应用」
interface AppOption {
  appCode: string
  appName: string
  baseUrl: string
  tenantCode?: string
  tenantName?: string
}
const apps = ref<AppOption[]>([])
const appOptions = computed(() => {
  const list = apps.value.slice()
  // 存量配置引用的应用可能已从字典移除，仍保留为可选项，避免保存时被清空
  if (form.appCode && !list.some((app) => app.appCode === form.appCode)) {
    list.unshift({ appCode: form.appCode, appName: form.appCode, baseUrl: '' })
  }
  return list
})
// 归属租户选项：后端仅返回当前用户可管理的租户（公共租户需显式授权）
const tenantOptions = ref<AiTenantOption[]>([])
const tenantPlaceholder = computed(() =>
  getTenantName() ? `默认当前租户（${getTenantName()}）` : '默认当前租户')
const tenantSelectOptions = computed<AiTenantOption[]>(() => {
  const list = tenantOptions.value.slice()
  // 编辑存量接口时，原租户可能不在当前可管理范围内，保留展示避免被误解为未配置
  if (form.tenantCode && !list.some((tenant) => tenant.tenantCode === form.tenantCode)) {
    list.unshift({ tenantCode: form.tenantCode, tenantName: tenantLabel(form.tenantCode) })
  }
  return list
})
const inputSchemaText = ref('[]')
const outputSchemaText = ref('[]')
const invokeConfigText = ref('{}')
const examplesText = ref('')

const testVisible = ref(false)
const testing = ref(false)
const testRow = ref<AiInterfaceVo | null>(null)
const testArgsText = ref('{}')
const testResult = ref<AiInvokeResultVo | null>(null)
const testResultText = computed(() => (testResult.value ? JSON.stringify(testResult.value, null, 2) : ''))
const testTargetUrl = computed(() => (testRow.value ? targetUrl(testRow.value) : ''))

const importVisible = ref(false)
const importText = ref('')
const importing = ref(false)

const curlVisible = ref(false)
const curlText = ref('')
const curlResponseText = ref('')

// ==================== 接口权限 ====================

interface PermissionRow {
  subjectType: 'ROLE' | 'USER' | 'POSITION'
  subjectId: string
  effect: 'ALLOW' | 'DENY'
  scope: 'INTERFACE' | 'ALL'
}

const permissionVisible = ref(false)
const permissionSaving = ref(false)
const permissionRow = ref<AiInterfaceVo | null>(null)
const permissionDetail = ref<AiPermissionDetail | null>(null)
const permissionForm = reactive<{ accessMode: 'PUBLIC' | 'RESTRICTED'; grants: PermissionRow[] }>({
  accessMode: 'RESTRICTED',
  grants: []
})
const roleOptions = ref<RoleDto[]>([])
const positionOptions = ref<PositionDto[]>([])
const userOptions = ref<UserDto[]>([])
const userLoading = ref(false)
const checkEmployeeId = ref('')
const checking = ref(false)
const permissionCheck = ref<AiPermissionCheck | null>(null)

const models = ref<AiModelVo[]>([])
const modelLoading = ref(false)
const modelVisible = ref(false)
const modelSaving = ref(false)
const modelForm = reactive<any>({
  id: '',
  name: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  temperature: 0.3,
  maxTokens: 8192,
  supportsToolCalling: true,
  visionMode: 'AUTO',
  contextWindowTokens: null,
  topP: null,
  frequencyPenalty: null,
  presencePenalty: null,
  thinkingMode: 'AUTO',
  reasoningEffort: '',
  thinkingBudget: null,
  extraBody: '',
  isDefault: false,
  status: 'ACTIVE'
})

onMounted(() => {
  loadInterfaces()
  loadModels()
  loadApps()
  loadTenants()
})

/**
 * 应用选项 = 字典 ai_app_code（提供 baseUrl）+ sys_app（提供租户信息/公共应用）。
 * 两者按 appCode 合并：字典仍是调用基础路径的权威来源，sys_app 补充租户归属。
 */
async function loadApps() {
  const [dictResult, appResult] = await Promise.all([
    queryByCode('ai_app_code'),
    listInterfaceApps().catch(() => null)
  ])
  const merged = new Map<string, AppOption>()
  for (const app of appResult?.data || []) {
    const appCode = String(app.appCode || '').trim()
    if (!appCode) {
      continue
    }
    merged.set(appCode, {
      appCode,
      appName: String(app.appName || appCode).trim(),
      baseUrl: String(app.apiBaseUrl || '').trim(),
      tenantCode: app.tenantCode,
      tenantName: app.tenantName
    })
  }
  if (dictResult.code === 200) {
    for (const item of dictResult.data || []) {
      const appCode = String(item.appCode ?? item.key ?? '').trim()
      if (!appCode) {
        continue
      }
      const existing = merged.get(appCode)
      merged.set(appCode, {
        appCode,
        appName: String(item.appName ?? item.value ?? '').trim() || existing?.appName || appCode,
        baseUrl: String(item.baseUrl ?? '').trim() || existing?.baseUrl || '',
        tenantCode: existing?.tenantCode,
        tenantName: existing?.tenantName
      })
    }
  }
  apps.value = Array.from(merged.values())
}

async function loadTenants() {
  const res = await listInterfaceTenants()
  tenantOptions.value = res.data || []
}

function appLabel(appCode?: string) {
  if (!appCode) {
    return '-'
  }
  const app = apps.value.find((item) => item.appCode === appCode)
  return app ? (app.appName || app.appCode) : appCode
}

function appOptionLabel(app: AiInterfaceAppOption) {
  const label = `${app.appName || app.appCode}（${app.appCode}）`
  return app.tenantCode === 'public' ? `${label} · 公共应用` : label
}

function tenantLabel(tenantCode?: string) {
  if (!tenantCode) {
    return '-'
  }
  const tenant = tenantOptions.value.find((item) => item.tenantCode === tenantCode)
  if (tenant) {
    return tenant.tenantName || tenant.tenantCode
  }
  return tenantCode === 'public' ? '公共租户' : tenantCode
}

function tenantOptionLabel(tenant: AiTenantOption) {
  return tenant.tenantName ? `${tenant.tenantName}（${tenant.tenantCode}）` : tenant.tenantCode
}

function appBaseUrl(appCode?: string) {
  if (!appCode) {
    return ''
  }
  return apps.value.find((item) => item.appCode === appCode)?.baseUrl || ''
}

/**
 * 目标地址 = invokeConfig.baseUrlOverride 或字典应用 baseUrl（域名+contextPath）+ 接口 path。
 */
function targetUrl(row: AiInterfaceVo) {
  const override = String(row.invokeConfig?.baseUrlOverride || '').trim()
  const base = (override || appBaseUrl(row.appCode)).replace(/\/+$/, '')
  const path = String(row.path || '').trim()
  if (!base) {
    return path ? `${path}（未配置应用基础路径）` : '未配置应用基础路径'
  }
  if (!path) {
    return base
  }
  return base + (path.startsWith('/') ? path : `/${path}`)
}

async function loadInterfaces() {
  loading.value = true
  try {
    const res = await listInterfaces({
      status: query.status,
      keyword: query.keyword,
      appCode: query.appCode,
      tenantCode: query.tenantCode
    })
    interfaces.value = res.data || []
  } finally {
    loading.value = false
  }
}

function defaultInputSchema() {
  return JSON.stringify([
    {
      key: 'visitCustomer',
      label: '来访客户',
      type: 'STRING',
      required: true,
      description: '来访客户名称'
    }
  ], null, 2)
}

function openCreate() {
  form.id = ''
  form.interfaceCode = ''
  form.name = ''
  form.description = ''
  form.appCode = ''
  form.tenantCode = ''
  form.httpMethod = 'POST'
  form.path = ''
  form.contentType = 'application/json'
  form.riskLevel = 'READ'
  form.confirmRequired = false
  form.accessMode = 'PUBLIC'
  form.searchKeywords = ''
  examplesText.value = ''
  inputSchemaText.value = defaultInputSchema()
  outputSchemaText.value = '[]'
  invokeConfigText.value = '{}'
  editFullscreen.value = false
  editVisible.value = true
}

// ==================== 从 cURL 新建 ====================

function openCurlDialog() {
  curlText.value = ''
  curlResponseText.value = ''
  curlVisible.value = true
}

interface CurlDraft {
  form: Record<string, any>
  inputSchema: AiFieldDef[]
  outputSchema: AiOutputField[]
  invokeConfig: Record<string, any>
  warnings: string[]
}

function createFromCurl() {
  let parsed: ParsedCurl
  try {
    parsed = parseCurl(curlText.value)
  } catch (e: any) {
    ElMessage.error(e.message || 'cURL 解析失败')
    return
  }
  let responseSample: any
  if (curlResponseText.value.trim()) {
    try {
      responseSample = JSON.parse(curlResponseText.value)
    } catch (e: any) {
      ElMessage.error(`响应示例 JSON 格式错误：${e.message}`)
      return
    }
  }
  const draft = buildCurlDraft(parsed, responseSample)
  openCreate()
  Object.assign(form, draft.form)
  inputSchemaText.value = JSON.stringify(draft.inputSchema, null, 2)
  outputSchemaText.value = JSON.stringify(draft.outputSchema, null, 2)
  invokeConfigText.value = JSON.stringify(draft.invokeConfig, null, 2)
  curlVisible.value = false
  if (draft.warnings.length) {
    ElMessageBox.alert(draft.warnings.map((item) => `· ${item}`).join('\n'), '已提取配置，请确认以下内容',
      { type: 'warning' })
  } else {
    ElMessage.success('已从 cURL 提取接口配置')
  }
}

/**
 * cURL 解析结果 → 新建表单：路径参数/查询参数/请求体生成 inputSchema，
 * 固定业务请求头写入 invokeConfig.headers，绝对地址按字典应用 baseUrl 剥离前缀。
 * 提供响应示例时按其实际字段校准 invokeConfig.response 并生成 outputSchema。
 */
function buildCurlDraft(parsed: ParsedCurl, responseSample?: any): CurlDraft {
  const warnings = [...parsed.warnings]
  const matched = matchAppByUrl(parsed)
  let path = matched ? matched.path : parsed.path
  const inputSchema: AiFieldDef[] = []
  const usedKeys = new Set<string>()

  for (const raw of Array.from(path.matchAll(/\{([^{}]+)\}/g)).map((item) => item[1])) {
    const key = uniqueFieldKey(safeFieldKey(raw), usedKeys)
    if (key !== raw) {
      path = path.replace(`{${raw}}`, `{${key}}`)
      warnings.push(`路径参数 ${raw} 含非法字符，已重命名为 ${key}`)
    }
    inputSchema.push({
      key,
      label: truncateText(raw, 100),
      type: 'STRING',
      required: true,
      location: 'PATH'
    })
  }

  for (const param of parsed.query) {
    if (safeFieldKey(param.name) !== param.name) {
      warnings.push(`查询参数 ${param.name} 含非法字符，无法映射为字段，已跳过`)
      continue
    }
    if (usedKeys.has(param.name)) {
      warnings.push(`查询参数 ${param.name} 重复，仅保留第一个`)
      continue
    }
    usedKeys.add(param.name)
    const type = inferValueType(param.value)
    const field: AiFieldDef = {
      key: param.name,
      label: truncateText(param.name, 100),
      type,
      location: 'QUERY'
    }
    if (param.value !== '') {
      field.default = coerceSample(param.value, type)
    }
    inputSchema.push(field)
  }

  let bodyMode = ''
  const body = parsed.body
  if (body?.mode === 'JSON') {
    if (body.value && typeof body.value === 'object' && !Array.isArray(body.value)) {
      collectBodyFields(body.value, [], inputSchema, usedKeys, warnings)
    } else {
      warnings.push('请求体不是 JSON 对象，未生成字段（接口请求体需为对象）')
    }
  } else if (body?.mode === 'FORM') {
    const fileFields = new Set<string>(body.fileFields || [])
    for (const rawName of Object.keys(body.value || {})) {
      const isFile = fileFields.has(rawName)
      const key = uniqueFieldKey(safeFieldKey(rawName), usedKeys)
      inputSchema.push({
        key,
        label: truncateText(rawName, 100),
        type: isFile ? 'FILE' : 'STRING',
        ...(key !== rawName ? { jsonPath: rawName } : {})
      })
    }
    bodyMode = 'FORM'
  }

  const responseDraft = buildResponseDraft(responseSample)
  warnings.push(...responseDraft.warnings)
  const invokeConfig: Record<string, any> = { response: responseDraft.response }
  if (Object.keys(parsed.headers).length) {
    invokeConfig.headers = { ...parsed.headers }
  }
  if (bodyMode) {
    invokeConfig.bodyMode = bodyMode
  }
  if (!matched) {
    if (parsed.origin) {
      invokeConfig.baseUrlOverride = parsed.origin
      warnings.push(`未匹配到字典 ai_app_code 中的应用，已将 ${parsed.origin} 写入 invokeConfig.baseUrlOverride`)
    }
    warnings.push('请选择应用编码（appCode）：接口路径会拼接在该应用的 baseUrl 之后')
  }
  if (!inputSchema.length) {
    warnings.push('未从 cURL 提取到任何参数，inputSchema 为空时所有参数将原样透传')
  }

  const riskLevel = parsed.method === 'GET' ? 'READ' : 'WRITE'
  return {
    form: {
      interfaceCode: suggestInterfaceCode(path),
      name: '',
      description: '',
      appCode: matched?.appCode || '',
      httpMethod: parsed.method,
      path,
      contentType: resolveContentType(parsed, bodyMode),
      riskLevel,
      confirmRequired: riskLevel !== 'READ',
      accessMode: riskLevel === 'READ' ? 'PUBLIC' : 'RESTRICTED',
      searchKeywords: ''
    },
    inputSchema,
    outputSchema: responseDraft.outputSchema,
    invokeConfig,
    warnings
  }
}

interface ResponseDraft {
  response: Record<string, any>
  outputSchema: AiOutputField[]
  warnings: string[]
}

/** 响应状态码/消息/数据字段候选，按优先级大小写不敏感匹配 */
const RESPONSE_CODE_KEYS = ['code', 'errcode', 'errCode', 'status', 'statusCode', 'ret', 'resultCode', 'success']
const RESPONSE_MESSAGE_KEYS = ['msg', 'message', 'errmsg', 'errMsg', 'errorMessage', 'error', 'description']
const RESPONSE_DATA_KEYS = ['data', 'result', 'payload']

/** 未提供响应示例时沿用 nway 标准信封 */
function defaultResponseConfig() {
  return { codePath: '$.code', successCodes: [200], messagePath: '$.msg', dataPath: '$.data' }
}

/**
 * 响应示例 → invokeConfig.response 与 outputSchema。
 * 只按示例推断字段路径与成功码，避免调用时凭空套用标准信封导致 codePath 校验失败。
 */
function buildResponseDraft(sample: any): ResponseDraft {
  const warnings: string[] = []
  if (sample === undefined) {
    return { response: defaultResponseConfig(), outputSchema: [], warnings }
  }
  if (sample === null || typeof sample !== 'object') {
    warnings.push('响应示例不是 JSON 对象/数组，未生成 codePath/dataPath：调用时按 HTTP 2xx 成功并原样返回')
    return { response: {}, outputSchema: [], warnings }
  }
  if (Array.isArray(sample)) {
    warnings.push('响应示例是 JSON 数组，未生成 codePath/dataPath：调用时按 HTTP 2xx 成功并原样返回')
    return { response: {}, outputSchema: [], warnings }
  }

  const codeKey = findFieldKey(sample, RESPONSE_CODE_KEYS)
  const messageKey = findFieldKey(sample, RESPONSE_MESSAGE_KEYS)
  const dataKey = findFieldKey(sample, RESPONSE_DATA_KEYS)
  const response: Record<string, any> = {}
  const outputSchema: AiOutputField[] = []
  const usedKeys = new Set<string>()

  if (codeKey) {
    const code = sample[codeKey]
    const successCode = successCodeFromSample(code)
    response.codePath = `$.${codeKey}`
    if (successCode === null) {
      response.successCodes = [200]
      warnings.push(`响应示例中 ${codeKey}=${sampleText(code)} 不是常见成功码，successCodes 保持 [200]，请按实际接口确认`)
    } else {
      response.successCodes = [successCode]
      if (String(successCode) !== '200') {
        warnings.push(`已按响应示例将成功码设为 ${sampleText(successCode)}，请确认该示例确为成功响应`)
      }
    }
    outputSchema.push({
      key: uniqueFieldKey(codeKey, usedKeys),
      label: '业务码',
      type: inferValueType(code)
    })
  } else {
    warnings.push('响应示例中未找到状态码字段（code/status/success 等），codePath 留空：HTTP 2xx 即视为成功')
  }

  if (messageKey) {
    response.messagePath = `$.${messageKey}`
    outputSchema.push({
      key: uniqueFieldKey(messageKey, usedKeys),
      label: '结果消息',
      type: inferValueType(sample[messageKey])
    })
  }

  if (dataKey) {
    response.dataPath = `$.${dataKey}`
    collectOutputFields(sample[dataKey], dataKey, outputSchema, usedKeys, warnings)
  } else {
    warnings.push('响应示例中未找到 data/result/payload 字段，dataPath 留空：调用时原样返回响应体')
  }

  return { response, outputSchema, warnings }
}

function findFieldKey(sample: Record<string, any>, candidates: string[]) {
  const keys = Object.keys(sample)
  for (const candidate of candidates) {
    const matched = keys.find((key) => key.toLowerCase() === candidate.toLowerCase())
    if (matched) {
      return matched
    }
  }
  return ''
}

/** 常见成功码：200 / 0 / 000000 / true 等；无法判断时返回 null 交由人工确认 */
function successCodeFromSample(code: any): any {
  if (typeof code === 'boolean') {
    return code ? code : null
  }
  const text = String(code ?? '').trim().toLowerCase()
  if (['200', '0', '000000', 'success', 'ok', 'true', 'succeeded'].includes(text)) {
    return code
  }
  return null
}

/** 把数据节点展开为一层出参字段；path 是相对完整响应的绝对 JSONPath */
function collectOutputFields(node: any, dataKey: string, fields: AiOutputField[],
                             usedKeys: Set<string>, warnings: string[]) {
  if (node === null || node === undefined) {
    warnings.push(`响应示例中 ${dataKey} 为 null，无法推断出参结构`)
    return
  }
  if (typeof node !== 'object') {
    fields.push({
      key: uniqueFieldKey(dataKey, usedKeys),
      label: '返回数据',
      path: `$.${dataKey}`,
      type: inferValueType(node),
      description: '返回数据'
    })
    return
  }
  if (Array.isArray(node)) {
    fields.push({
      key: uniqueFieldKey(dataKey, usedKeys),
      label: '返回数据',
      path: `$.${dataKey}`,
      type: 'ARRAY',
      description: `返回数据列表（示例 ${node.length} 条）`,
      items: outputArrayItems(node, dataKey, warnings)
    })
    return
  }
  const entries = Object.entries(node)
  if (!entries.length) {
    fields.push({
      key: uniqueFieldKey(dataKey, usedKeys),
      label: '返回数据',
      path: `$.${dataKey}`,
      type: 'OBJECT',
      description: '返回数据（示例为空对象）'
    })
    return
  }
  for (const [childKey, child] of entries) {
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(childKey)) {
      warnings.push(`出参字段 ${dataKey}.${childKey} 含特殊字符，无法用 JSONPath 表达，已跳过`)
      continue
    }
    const field: AiOutputField = {
      key: uniqueFieldKey(childKey, usedKeys),
      label: truncateText(childKey, 100),
      path: `$.${dataKey}.${childKey}`,
      type: Array.isArray(child) ? 'ARRAY' : inferValueType(child),
      description: `返回数据字段 ${truncateText(`${dataKey}.${childKey}`, 120)}`
    }
    fillOutputDetail(field, child, `${dataKey}.${childKey}`, warnings)
    fields.push(field)
  }
}

/**
 * 按示例补全出参的嵌套结构：数组生成 items，对象生成 properties。
 * 这些结构仅作为接口元数据，运行时仍按 field.path 整体取值，不逐字段映射。
 */
function fillOutputDetail(field: AiOutputField, sample: any, label: string, warnings: string[]) {
  if (field.type === 'ARRAY') {
    field.items = outputArrayItems(sample as any[], label, warnings)
    return
  }
  if (field.type !== 'OBJECT' || !sample || typeof sample !== 'object') {
    return
  }
  const properties = outputProperties(sample, label, warnings)
  if (properties.length) {
    field.properties = properties
  }
}

/** 数组元素结构：标量数组给元素类型，对象数组给元素字段 */
function outputArrayItems(list: any[], label: string, warnings: string[]): AiOutputItem {
  if (!list.length) {
    warnings.push(`响应示例中 ${label} 为空数组，未生成元素字段`)
    return { type: 'STRING', description: '数组元素' }
  }
  const first = list.find((item) => item !== null && item !== undefined)
  if (first === undefined) {
    warnings.push(`响应示例中 ${label} 的元素均为 null，未生成元素字段`)
    return { type: 'STRING', description: '数组元素' }
  }
  if (Array.isArray(first)) {
    return { type: 'ARRAY', description: '数组元素' }
  }
  if (typeof first === 'object') {
    const properties = outputProperties(first, `${label}[]`, warnings)
    return properties.length
      ? { type: 'OBJECT', description: '数组元素', properties }
      : { type: 'OBJECT', description: '数组元素（示例为空对象）' }
  }
  return { type: inferValueType(first), description: '数组元素' }
}

/** 按示例对象递归生成出参子字段，仅用于元数据说明与展示 */
function outputProperties(sample: Record<string, any>, label: string, warnings: string[]): AiOutputField[] {
  const usedKeys = new Set<string>()
  const properties: AiOutputField[] = []
  for (const [childKey, child] of Object.entries(sample || {})) {
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(childKey)) {
      warnings.push(`出参字段 ${label}.${childKey} 含特殊字符，无法用 JSONPath 表达，已跳过`)
      continue
    }
    const type = Array.isArray(child) ? 'ARRAY' : inferValueType(child)
    const field: AiOutputField = {
      key: uniqueFieldKey(childKey, usedKeys),
      label: truncateText(childKey, 100),
      type,
      description: `返回数据字段 ${truncateText(`${label}.${childKey}`, 120)}`
    }
    fillOutputDetail(field, child, `${label}.${childKey}`, warnings)
    properties.push(field)
  }
  return properties
}

/** 按字典应用 baseUrl 匹配 URL，返回应用编码与去掉 baseUrl 前缀的路径 */
function matchAppByUrl(parsed: ParsedCurl) {
  let best: { appCode: string; path: string } | null = null
  let bestLength = -1
  for (const app of apps.value) {
    const base = (app.baseUrl || '').trim().replace(/\/+$/, '')
    if (!base) {
      continue
    }
    const originMatch = /^[a-z][a-z0-9+.-]*:\/\/[^/?#]+/i.exec(base)
    const baseOrigin = originMatch ? originMatch[0] : ''
    const basePath = base.slice(baseOrigin.length).replace(/\/+$/, '')
    if (parsed.origin) {
      if (!baseOrigin || baseOrigin.toLowerCase() !== parsed.origin.toLowerCase()
        || !hasPathPrefix(parsed.path, basePath)) {
        continue
      }
    } else if (!basePath || !hasPathPrefix(parsed.path, basePath)) {
      continue
    }
    if (basePath.length > bestLength) {
      bestLength = basePath.length
      best = { appCode: app.appCode, path: parsed.path.slice(basePath.length) || '/' }
    }
  }
  return best
}

function hasPathPrefix(path: string, prefix: string) {
  const normalized = prefix.replace(/\/+$/, '')
  return !normalized || path === normalized || path.startsWith(`${normalized}/`)
}

function resolveContentType(parsed: ParsedCurl, bodyMode: string) {
  if (bodyMode === 'FORM' && (!parsed.contentType || /multipart/i.test(parsed.contentType))) {
    return 'application/x-www-form-urlencoded'
  }
  return parsed.contentType || 'application/json'
}

/** 把嵌套 JSON 拍平成带 jsonPath 的字段，避免生成需要递归映射的 OBJECT */
function collectBodyFields(value: Record<string, any>, pathParts: string[], fields: AiFieldDef[],
                           usedKeys: Set<string>, warnings: string[]) {
  for (const [rawKey, child] of Object.entries(value)) {
    const currentPath = [...pathParts, rawKey]
    const jsonPath = currentPath.join('.')
    if (child !== null && typeof child === 'object' && !Array.isArray(child)) {
      if (!Object.keys(child).length) {
        warnings.push(`字段 ${jsonPath} 是空对象，已跳过`)
        continue
      }
      collectBodyFields(child, currentPath, fields, usedKeys, warnings)
      continue
    }
    const type = Array.isArray(child) ? 'ARRAY' : inferValueType(child)
    const key = uniqueFieldKey(safeFieldKey(rawKey), usedKeys)
    const field: AiFieldDef = {
      key,
      label: truncateText(rawKey, 100),
      type,
      ...(jsonPath === key ? {} : { jsonPath })
    }
    if (Array.isArray(child)) {
      field.items = arrayItems(child, warnings, jsonPath)
    } else if (child !== null && child !== undefined) {
      field.default = coerceSample(child, type)
    }
    fields.push(field)
  }
}

function arrayItems(list: any[], warnings: string[], jsonPath: string): Record<string, any> {
  if (!list.length) {
    warnings.push(`字段 ${jsonPath} 是空数组，items 默认按 STRING 生成`)
    return { type: 'STRING', description: '数组元素' }
  }
  if (list.every((item) => item !== null && typeof item === 'object' && !Array.isArray(item))) {
    const properties = buildItemProperties(list[0], warnings, `${jsonPath}[]`)
    if (properties.length) {
      return { type: 'OBJECT', description: '数组元素', properties }
    }
    warnings.push(`字段 ${jsonPath} 的数组元素为空对象，items 默认按 STRING 生成`)
    return { type: 'STRING', description: '数组元素' }
  }
  const first = list.find((item) => item !== null && item !== undefined)
  const type = inferValueType(first)
  if (type === 'OBJECT' || type === 'ARRAY') {
    return { type: 'STRING', description: '数组元素' }
  }
  return { type, description: '数组元素' }
}

function buildItemProperties(sample: any, warnings: string[], label: string): AiFieldDef[] {
  const used = new Set<string>()
  const properties: AiFieldDef[] = []
  for (const [rawKey, child] of Object.entries(sample || {})) {
    const type = Array.isArray(child) ? 'ARRAY' : inferValueType(child)
    const key = uniqueFieldKey(safeFieldKey(rawKey), used)
    if (type === 'OBJECT') {
      if (!child || !Object.keys(child).length) {
        continue
      }
      const children = buildItemProperties(child, warnings, `${label}.${rawKey}`)
      if (!children.length) {
        continue
      }
      properties.push({
        key,
        label: truncateText(rawKey, 100),
        type: 'OBJECT',
        description: `${truncateText(label, 120)} 中的对象字段`,
        properties: children
      })
      continue
    }
    const field: AiFieldDef = {
      key,
      label: truncateText(rawKey, 100),
      type,
      description: `${truncateText(label, 120)} 中的字段`
    }
    if (type === 'ARRAY') {
      field.items = arrayItems(child as any[], warnings, `${label}.${rawKey}`)
    } else if (child !== null && child !== undefined) {
      field.default = coerceSample(child, type)
    }
    properties.push(field)
  }
  return properties
}

function inferValueType(value: any): string {
  if (typeof value === 'boolean') return 'BOOLEAN'
  if (typeof value === 'number') return Number.isInteger(value) ? 'INTEGER' : 'NUMBER'
  if (Array.isArray(value)) return 'ARRAY'
  if (value !== null && typeof value === 'object') return 'OBJECT'
  const text = String(value ?? '').trim()
  if (/^(true|false)$/i.test(text)) return 'BOOLEAN'
  if (/^-?(0|[1-9]\d*)$/.test(text)) return 'INTEGER'
  if (/^-?(0|[1-9]\d*)\.\d+$/.test(text)) return 'NUMBER'
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return 'DATE'
  if (/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}$/.test(text)) return 'DATETIME'
  return text.length > 80 ? 'TEXT' : 'STRING'
}

function coerceSample(value: any, type: string) {
  if (type === 'INTEGER') {
    const parsed = Number.parseInt(String(value), 10)
    return Number.isNaN(parsed) ? value : parsed
  }
  if (type === 'NUMBER') {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? value : parsed
  }
  if (type === 'BOOLEAN') {
    return String(value).toLowerCase() === 'true'
  }
  return value
}

function sampleText(value: any) {
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return truncateText(text, 60)
}

function truncateText(text: any, max: number) {
  const value = String(text ?? '')
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function safeFieldKey(raw: string) {
  let key = String(raw ?? '').trim().replace(/[^A-Za-z0-9_]/g, '_')
  if (!key) {
    key = 'field'
  }
  if (!/^[A-Za-z]/.test(key)) {
    key = `f_${key}`
  }
  return key.slice(0, 64)
}

function uniqueFieldKey(base: string, used: Set<string>) {
  let key = base
  let index = 2
  while (used.has(key)) {
    const suffix = `_${index}`
    key = `${base.slice(0, 64 - suffix.length)}${suffix}`
    index += 1
  }
  used.add(key)
  return key
}

function suggestInterfaceCode(path: string) {
  const code = path
    .replace(/[{}]/g, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[^A-Za-z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toLowerCase()
    .slice(0, 64)
  return code.length >= 2 ? code : ''
}

async function openEdit(row: AiInterfaceVo) {
  const res = await getInterface(row.id)
  const detail = res.data
  Object.assign(form, detail, { id: detail.id })
  inputSchemaText.value = JSON.stringify(detail.inputSchema || [], null, 2)
  outputSchemaText.value = JSON.stringify(detail.outputSchema || [], null, 2)
  invokeConfigText.value = JSON.stringify(detail.invokeConfig || {}, null, 2)
  examplesText.value = (detail.examples || []).join('\n')
  editFullscreen.value = false
  editVisible.value = true
}

// ==================== 接口权限 ====================

async function openPermissions(row: AiInterfaceVo) {
  permissionRow.value = row
  const res = await getInterfacePermissions(row.id)
  permissionDetail.value = res.data
  permissionForm.accessMode = res.data.accessMode === 'PUBLIC' ? 'PUBLIC' : 'RESTRICTED'
  permissionForm.grants = (res.data.grants || []).map((grant) => ({
    subjectType: grant.subjectType,
    subjectId: grant.subjectId,
    effect: grant.effect === 'DENY' ? 'DENY' : 'ALLOW',
    scope: grant.scope
  }))
  checkEmployeeId.value = ''
  permissionCheck.value = null
  permissionVisible.value = true
  loadPermissionSubjects()
}

async function loadPermissionSubjects() {
  const [roleResult, positionResult] = await Promise.all([
    roleOptions.value.length ? null : queryRolePage({ current: 1, size: 500 }).catch(() => null),
    positionOptions.value.length ? null : queryAllPositions().catch(() => null)
  ])
  if (roleResult?.data?.data) {
    roleOptions.value = roleResult.data.data
  }
  if (positionResult?.data) {
    positionOptions.value = positionResult.data
  }
}

async function searchUsers(keyword: string) {
  const kw = String(keyword || '').trim()
  if (!kw) {
    userOptions.value = []
    return
  }
  userLoading.value = true
  try {
    const res = await queryUserPage({ keyword: kw, current: 1, size: 20 })
    userOptions.value = res.data?.data || []
  } catch (e) {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

function addPermissionRow() {
  permissionForm.grants.push({
    subjectType: 'ROLE',
    subjectId: '',
    effect: 'ALLOW',
    scope: 'INTERFACE'
  })
}

function grantPayload(scope: 'INTERFACE' | 'ALL'): AiPermissionGrantPayload[] {
  return permissionForm.grants
    .filter((row) => row.scope === scope && row.subjectId)
    .map((row) => ({
      subjectType: row.subjectType,
      subjectId: row.subjectId,
      effect: row.effect
    }))
}

async function savePermissions() {
  if (!permissionRow.value) {
    return
  }
  const grants = grantPayload('INTERFACE')
  const allGrants = grantPayload('ALL')
  permissionSaving.value = true
  try {
    if (grants.length || allGrants.length) {
      const preview = await previewInterfacePermissions(permissionRow.value.id, {
        accessMode: permissionForm.accessMode,
        grants,
        allGrants
      })
      const affected = preview.data?.affectedUsers || 0
      if (affected > 0) {
        const sample = (preview.data?.sampleUsers || []).join('、')
        try {
          await ElMessageBox.confirm(
            `本次授权将影响 ${affected} 名用户${sample ? `（示例：${sample}）` : ''}，是否保存？`,
            '影响面确认', { type: 'info' }
          )
        } catch (e) {
          return
        }
      }
    }
    const res = await saveInterfacePermissions(permissionRow.value.id, {
      accessMode: permissionForm.accessMode,
      grants,
      allGrants
    })
    permissionDetail.value = res.data
    ElMessage.success('权限已保存')
    await loadInterfaces()
  } finally {
    permissionSaving.value = false
  }
}

async function runPermissionCheck() {
  const employeeId = checkEmployeeId.value.trim()
  if (!permissionRow.value || !employeeId) {
    ElMessage.warning('请输入工号')
    return
  }
  checking.value = true
  try {
    const res = await checkInterfacePermission(permissionRow.value.id, employeeId)
    permissionCheck.value = res.data
  } finally {
    checking.value = false
  }
}

// ==================== 调用测试 ====================

function openTest(row: AiInterfaceVo) {
  testRow.value = row
  testArgsText.value = JSON.stringify(buildTestArgs(row.inputSchema), null, 2)
  testResult.value = null
  testVisible.value = true
}

function resetTestArgs() {
  testArgsText.value = JSON.stringify(buildTestArgs(testRow.value?.inputSchema), null, 2)
}

/**
 * 按 inputSchema 生成请求参数模板：仅包含模型可见字段的必填项、默认值与枚举首项。
 */
function buildTestArgs(fields?: AiFieldDef[]): Record<string, any> {
  const args: Record<string, any> = {}
  for (const field of fields || []) {
    if (field.valueSource && field.valueSource.toUpperCase() !== 'LLM') {
      continue
    }
    const type = (field.type || 'STRING').toUpperCase()
    if (field.default !== undefined && field.default !== null) {
      args[field.key] = field.default
      continue
    }
    if (type === 'ENUM' && field.options?.length) {
      args[field.key] = field.options[0].value
      continue
    }
    if (type === 'MULTI_ENUM') {
      args[field.key] = field.options?.length ? [field.options[0].value] : []
      continue
    }
    if (type === 'OBJECT' && field.required && field.properties?.length) {
      args[field.key] = buildTestArgs(field.properties)
      continue
    }
    if (!field.required) {
      continue
    }
    if (type === 'ARRAY' || type === 'FILE_ARRAY') {
      args[field.key] = []
    } else if (type === 'FILE') {
      args[field.key] = ''
    } else {
      args[field.key] = sampleValue(type)
    }
  }
  return args
}

function sampleValue(type: string) {
  const now = new Date()
  const date = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
  switch (type) {
    case 'INTEGER':
    case 'NUMBER':
      return 0
    case 'BOOLEAN':
      return false
    case 'DATE':
      return date
    case 'DATETIME':
      return `${date} ${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}`
    default:
      return ''
  }
}

function pad2(value: number) {
  return String(value).padStart(2, '0')
}

async function runTest() {
  if (!testRow.value) {
    return
  }
  let args: any
  try {
    args = parseJson(testArgsText.value || '{}', '请求参数')
  } catch (e: any) {
    ElMessage.error(e.message)
    return
  }
  if (!args || typeof args !== 'object' || Array.isArray(args)) {
    ElMessage.error('请求参数必须是 JSON 对象')
    return
  }
  testing.value = true
  try {
    const res = await testInterface(testRow.value.id, args)
    testResult.value = res.data
  } finally {
    testing.value = false
  }
}

function parseJson(text: string, label: string) {
  try {
    return JSON.parse(text || 'null')
  } catch (e: any) {
    throw new Error(`${label} JSON 格式错误：${e.message}`)
  }
}

async function saveInterfaceConfig() {
  let inputSchema: any
  let outputSchema: any
  let invokeConfig: any
  try {
    inputSchema = parseJson(inputSchemaText.value, 'inputSchema')
    outputSchema = parseJson(outputSchemaText.value, 'outputSchema')
    invokeConfig = parseJson(invokeConfigText.value || '{}', 'invokeConfig')
  } catch (e: any) {
    ElMessage.error(e.message)
    return
  }
  if (!Array.isArray(inputSchema)) {
    ElMessage.error('inputSchema 必须是 JSON 数组')
    return
  }
  if (!Array.isArray(outputSchema)) {
    ElMessage.error('outputSchema 必须是 JSON 数组，元素形如 {"key":"msg","label":"结果消息","type":"STRING"}；留空请填 []')
    return
  }
  if (!invokeConfig || typeof invokeConfig !== 'object' || Array.isArray(invokeConfig)) {
    ElMessage.error('invokeConfig 必须是 JSON 对象')
    return
  }
  const config: AiInterfaceConfig = {
    ...form,
    confirmRequired: form.riskLevel !== 'READ' ? true : form.confirmRequired,
    inputSchema,
    outputSchema,
    invokeConfig,
    examples: examplesText.value.split('\n').map((s) => s.trim()).filter(Boolean)
  }
  saving.value = true
  try {
    await saveInterface(config)
    ElMessage.success('已保存')
    editVisible.value = false
    await loadInterfaces()
  } finally {
    saving.value = false
  }
}

async function publish(row: AiInterfaceVo) {
  if (row.accessMode === 'RESTRICTED' && !row.grantCount && !row.allGrantCount) {
    try {
      await ElMessageBox.confirm(
        '该接口为受控访问且尚未授权，发布后仅超级管理员可调用。是否继续？',
        '发布确认', { type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  await publishInterface(row.id)
  ElMessage.success('已发布')
  await loadInterfaces()
}

async function offline(row: AiInterfaceVo) {
  await offlineInterface(row.id)
  ElMessage.success('已下线')
  await loadInterfaces()
}

async function removeInterface(row: AiInterfaceVo) {
  try {
    await ElMessageBox.confirm(`删除接口「${row.name}」？`, '删除', { type: 'warning' })
    await deleteInterface(row.id)
    await loadInterfaces()
  } catch (e) {
    // 取消
  }
}

async function doImport() {
  let payload: any
  try {
    payload = parseJson(importText.value, '导入内容')
  } catch (e: any) {
    ElMessage.error(e.message)
    return
  }
  importing.value = true
  try {
    const result = await importInterfaces(payload)
    const data = result.data || {}
    ElMessage.success(`导入完成：成功 ${data.saved || 0} / ${data.total || 0}`)
    if (data.errors && data.errors.length) {
      ElMessageBox.alert(data.errors.join('\n'), '部分接口导入失败', { type: 'warning' })
    }
    importVisible.value = false
    importText.value = ''
    await loadInterfaces()
  } finally {
    importing.value = false
  }
}

async function exportAll() {
  const result = await exportInterfaces()
  const payload = result.data || { version: '2.0', interfaces: [] }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ai_interface_seed.json'
  link.click()
  URL.revokeObjectURL(url)
}

// ==================== 模型 ====================

async function loadModels() {
  modelLoading.value = true
  try {
    const res = await listModels()
    models.value = res.data || []
  } finally {
    modelLoading.value = false
  }
}

function openModelCreate() {
  Object.assign(modelForm, {
    id: '', name: '', baseUrl: '', modelName: '', apiKey: '',
    temperature: 0.3, maxTokens: 8192, supportsToolCalling: true,
    visionMode: 'AUTO', contextWindowTokens: null, topP: null,
    frequencyPenalty: null, presencePenalty: null, thinkingMode: 'AUTO',
    reasoningEffort: '', thinkingBudget: null, extraBody: '',
    isDefault: false, status: 'ACTIVE'
  })
  modelVisible.value = true
}

function openModelEdit(row: AiModelVo) {
  Object.assign(modelForm, {
    id: row.id,
    name: row.name,
    baseUrl: row.baseUrl,
    modelName: row.modelName,
    apiKey: '',
    temperature: row.temperature ?? 0.3,
    maxTokens: row.maxTokens ?? 8192,
    supportsToolCalling: row.supportsToolCalling !== false,
    visionMode: row.supportsVision == null ? 'AUTO' : row.supportsVision ? 'ON' : 'OFF',
    contextWindowTokens: row.contextWindowTokens ?? null,
    topP: row.topP ?? null,
    frequencyPenalty: row.frequencyPenalty ?? null,
    presencePenalty: row.presencePenalty ?? null,
    thinkingMode: row.thinkingEnabled == null ? 'AUTO' : row.thinkingEnabled ? 'ON' : 'OFF',
    reasoningEffort: row.reasoningEffort || '',
    thinkingBudget: row.thinkingBudget ?? null,
    extraBody: row.extraBody || '',
    isDefault: !!row.isDefault,
    status: row.status || 'ACTIVE'
  })
  modelVisible.value = true
}

async function saveModelConfig() {
  if (!modelForm.name || !modelForm.baseUrl || !modelForm.modelName) {
    ElMessage.warning('配置名、Base URL、模型名不能为空')
    return
  }
  const extraBody = String(modelForm.extraBody || '').trim()
  if (extraBody) {
    try {
      const parsed = JSON.parse(extraBody)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        ElMessage.error('额外请求参数必须是 JSON 对象')
        return
      }
    } catch (e: any) {
      ElMessage.error(`额外请求参数 JSON 格式错误：${e.message}`)
      return
    }
  }
  const { visionMode, thinkingMode, ...rest } = modelForm
  const payload = {
    ...rest,
    supportsVision: visionMode === 'AUTO' ? null : visionMode === 'ON',
    thinkingEnabled: thinkingMode === 'AUTO' ? null : thinkingMode === 'ON',
    reasoningEffort: modelForm.reasoningEffort || null,
    extraBody: extraBody || null
  }
  modelSaving.value = true
  try {
    await saveModel(payload)
    ElMessage.success('已保存')
    modelVisible.value = false
    await loadModels()
  } finally {
    modelSaving.value = false
  }
}

async function removeModel(row: AiModelVo) {
  try {
    await ElMessageBox.confirm(`删除模型「${row.name}」？`, '删除', { type: 'warning' })
    await deleteModel(row.id)
    await loadModels()
  } catch (e) {
    // 取消
  }
}

/** 思考是否生效：显式开启，或未配置时由推理深度/思考预算推断。 */
function modelThinkingActive(row: AiModelVo) {
  return row.thinkingEnabled === true
    || (row.thinkingEnabled == null && (!!row.reasoningEffort || row.thinkingBudget != null))
}

// ==================== 展示 ====================

function riskLabel(risk: string) {
  if (risk === 'WRITE') return '写入'
  if (risk === 'HIGH_RISK') return '高危'
  return '只读'
}

function riskTag(risk: string) {
  if (risk === 'WRITE') return 'warning'
  if (risk === 'HIGH_RISK') return 'danger'
  return 'success'
}

function statusLabel(status?: string) {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'OFFLINE') return '已下线'
  return '草稿'
}

function statusTag(status?: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'OFFLINE') return 'info'
  return 'warning'
}
</script>

<style scoped lang="scss">
.ai-admin {
  position: absolute;
  top: 12px;
  right: 1%;
  bottom: 12px;
  left: 1%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

/* 页签铺满容器剩余高度，表格区域内部滚动，避免整页出现垂直滚动条 */
.ai-tabs {
  flex: 1;
  min-height: 0;
}

:deep(.el-tab-pane) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.toolbar-tip {
  color: #8f959e;
  font-size: 12px;
  line-height: 32px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.total {
  color: #8f959e;
  font-size: 12px;
}

.code-input :deep(textarea) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.import-tip {
  color: #8f959e;
  font-size: 12px;
  margin-bottom: 8px;
  line-height: 1.6;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dialog-title-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tip-icon {
  font-size: 15px;
  color: #909399;
  cursor: help;
}

.tip-icon:hover {
  color: #409eff;
}

.label-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.dialog-body {
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.test-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.test-target {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
  word-break: break-all;
}

.test-target-label {
  flex-shrink: 0;
  color: #8f959e;
}

.test-target-url {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.test-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.result-title {
  margin-top: 12px;
}

.test-latency {
  color: #8f959e;
  font-size: 12px;
  font-weight: normal;
}

.test-result {
  margin: 0;
  padding: 10px 12px;
  max-height: 260px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.test-empty {
  color: #8f959e;
  font-size: 12px;
  padding: 12px;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
}

.grant-count {
  margin-left: 6px;
  color: #8f959e;
  font-size: 12px;
}

.permission-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.permission-resource {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}

.permission-table {
  margin-bottom: 8px;
}

.permission-tip {
  color: #8f959e;
  font-size: 12px;
  line-height: 1.6;
}

.permission-check {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.permission-reason {
  color: #606266;
  font-size: 12px;
  word-break: break-all;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.form-tip {
  margin: -4px 0 12px 120px;
  color: #8f959e;
  font-size: 12px;
  line-height: 1.6;
}

.model-form :deep(.el-divider) {
  margin: 6px 0 16px;
}

.model-form :deep(.el-divider__text) {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
</style>
