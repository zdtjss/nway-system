<template>
    <div class="authz-page">
        <el-tabs v-model="tab">
            <!-- 有效权限 -->
            <el-tab-pane label="有效权限" name="effective">
                <el-form :inline="true">
                    <el-form-item label="工号">
                        <el-input v-model="employeeId" placeholder="如 001" style="width: 220px"
                            @keyup.enter="loadEffective" />
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" :icon="Search" @click="loadEffective">查询</el-button>
                    </el-form-item>
                </el-form>
                <el-alert v-if="effectiveList.length === 0 && effectiveQueried" type="info" :closable="false"
                    title="该用户没有任何有效授权；如应有权限，请到「决策诊断」查看拒绝原因" />
                <el-table v-else :data="effectiveList" stripe>
                    <el-table-column prop="appCode" label="应用" width="160" />
                    <el-table-column prop="resourceCode" label="资源" width="200" />
                    <el-table-column label="动作">
                        <template #default="{ row }">
                            <el-tag v-for="a in row.actions" :key="a" size="small" style="margin-right: 6px">{{ a }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="数据范围">
                        <template #default="{ row }">
                            <span v-for="p in row.dataPredicates" :key="p.kind" style="margin-right: 8px">
                                {{ p.kind }}<span v-if="p.values.length">({{ p.values.join(',') }})</span>
                            </span>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- 决策诊断 -->
            <el-tab-pane label="决策诊断" name="decision">
                <el-form :inline="true">
                    <el-form-item label="工号"><el-input v-model="diag.employeeId" style="width: 160px" /></el-form-item>
                    <el-form-item label="应用"><el-input v-model="diag.appCode" style="width: 160px" /></el-form-item>
                    <el-form-item label="资源"><el-input v-model="diag.resourceCode" style="width: 160px" /></el-form-item>
                    <el-form-item label="动作"><el-input v-model="diag.action" style="width: 120px" /></el-form-item>
                    <el-form-item>
                        <el-button type="primary" :icon="Search" @click="runDiagnose">诊断</el-button>
                    </el-form-item>
                </el-form>
                <el-card v-if="decision" shadow="never">
                    <el-result :icon="decision.allow ? 'success' : 'error'"
                        :title="decision.allow ? '允许访问' : '拒绝访问'"
                        :sub-title="'reason=' + decision.reason + '，decisionId=' + decision.decisionId + '，授权版本=' + decision.version" />
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="命中授权">
                            <el-tag v-for="id in decision.matchedGrantIds" :key="id" size="small"
                                style="margin-right: 6px">{{ id }}</el-tag>
                            <span v-if="decision.matchedGrantIds.length === 0">无</span>
                        </el-descriptions-item>
                        <el-descriptions-item label="数据谓词">
                            <span v-for="p in decision.dataPredicates" :key="p.kind" style="margin-right: 10px">
                                {{ p.kind }}<span v-if="p.values.length">({{ p.values.join(',') }})</span>
                            </span>
                            <span v-if="decision.dataPredicates.length === 0">无</span>
                        </el-descriptions-item>
                    </el-descriptions>
                </el-card>
            </el-tab-pane>

            <!-- 变更记录 -->
            <el-tab-pane label="变更记录" name="changes">
                <el-form :inline="true">
                    <el-form-item label="主体类型">
                        <el-select v-model="changeQuery.targetType" style="width: 140px">
                            <el-option label="角色" value="ROLE" />
                            <el-option label="用户" value="USER" />
                            <el-option label="岗位" value="POSITION" />
                            <el-option label="应用" value="APP" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="主体ID">
                        <el-input v-model="changeQuery.targetId" style="width: 220px" />
                    </el-form-item>
                    <el-form-item>
                        <el-button :icon="Search" @click="loadChanges">查询</el-button>
                    </el-form-item>
                </el-form>
                <el-table :data="changeList" stripe>
                    <el-table-column prop="createTime" label="时间" width="180" />
                    <el-table-column prop="actorId" label="操作人" width="120" />
                    <el-table-column prop="targetType" label="类型" width="100" />
                    <el-table-column prop="targetId" label="主体" width="160" />
                    <el-table-column prop="reason" label="原因" min-width="160" />
                    <el-table-column prop="authzVersion" label="版本" width="90" />
                    <el-table-column label="差异">
                        <template #default="{ row }">
                            <el-popover width="600" trigger="click">
                                <template #reference><el-button link>查看</el-button></template>
                                <div class="diff-block">
                                    <div><b>before</b></div>
                                    <pre>{{ pretty(row.beforeJson) }}</pre>
                                    <div><b>after</b></div>
                                    <pre>{{ pretty(row.afterJson) }}</pre>
                                </div>
                            </el-popover>
                            <el-popconfirm title="回滚到该次变更之前的状态？" @confirm="doRollback(row)">
                                <template #reference><el-button link type="warning">回滚</el-button></template>
                            </el-popconfirm>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- 授权维护 -->
            <el-tab-pane label="授权维护" name="grants">
                <el-form :inline="true">
                    <el-form-item label="主体类型">
                        <el-select v-model="grantQuery.subjectType" style="width: 130px">
                            <el-option label="角色" value="ROLE" />
                            <el-option label="用户" value="USER" />
                            <el-option label="岗位" value="POSITION" />
                            <el-option label="应用" value="APP" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="主体ID">
                        <el-input v-model="grantQuery.subjectId" style="width: 220px" />
                    </el-form-item>
                    <el-form-item>
                        <el-button :icon="Search" @click="loadGrants">查询</el-button>
                        <el-button type="primary" :icon="Plus" @click="openGrant()">新增授权</el-button>
                        <el-button @click="openBatch()">批量授权</el-button>
                    </el-form-item>
                </el-form>
                <el-table :data="grantList" stripe>
                    <el-table-column label="效果" width="90">
                        <template #default="{ row }">
                            <el-tag :type="row.effect === 'DENY' ? 'danger' : 'success'" size="small">{{ row.effect }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="资源" min-width="220">
                        <template #default="{ row }">{{ row.appCode }}:{{ row.resourceCode }}</template>
                    </el-table-column>
                    <el-table-column label="动作" min-width="180">
                        <template #default="{ row }">
                            <el-tag v-for="a in parseActions(row.actions)" :key="a" size="small"
                                style="margin-right: 4px">{{ a }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="dataScope" label="数据范围" width="120" />
                    <el-table-column prop="expireAt" label="失效时间" width="180" />
                    <el-table-column prop="status" label="状态" width="100" />
                    <el-table-column label="操作" width="150">
                        <template #default="{ row }">
                            <el-button link type="primary" @click="openGrant(row)">编辑</el-button>
                            <el-popconfirm title="删除该授权？" @confirm="removeGrant(row)">
                                <template #reference><el-button link type="danger">删除</el-button></template>
                            </el-popconfirm>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- 模板 / 套餐 -->
            <el-tab-pane label="模板/套餐" name="template">
                <el-form :inline="true">
                    <el-form-item>
                        <el-button type="primary" :icon="Plus" @click="openTemplate()">新增模板</el-button>
                    </el-form-item>
                </el-form>
                <el-table :data="templateList" stripe>
                    <el-table-column prop="templateCode" label="编码" width="180" />
                    <el-table-column prop="name" label="名称" width="200" />
                    <el-table-column prop="appCode" label="应用" width="160" />
                    <el-table-column prop="status" label="状态" width="100" />
                    <el-table-column label="操作" width="200">
                        <template #default="{ row }">
                            <el-button link type="primary" @click="openTemplate(row)">编辑</el-button>
                            <el-button link type="warning" @click="openApply(row)">应用到租户</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- 资源登记 -->
            <el-tab-pane label="资源登记" name="resource">
                <el-form :inline="true">
                    <el-form-item label="应用">
                        <el-input v-model="resourceAppCode" placeholder="留空=全部" style="width: 180px" />
                    </el-form-item>
                    <el-form-item>
                        <el-button :icon="Search" @click="loadResources">查询</el-button>
                        <el-button type="primary" :icon="Plus" @click="openResource()">新增资源</el-button>
                        <el-button @click="loadCoverage">接口覆盖报表</el-button>
                    </el-form-item>
                </el-form>

                <el-alert v-if="coverage" type="info" :closable="false" style="margin-bottom: 12px"
                    :title="`接口 ${coverage.totalEndpoints} 个：注解 ${coverage.annotated}，历史映射 ${coverage.legacy}，未声明 ${coverage.uncovered}，公共 ${coverage.publicApi}；注解覆盖率 ${(coverage.annotationCoverage * 100).toFixed(1)}%；缺资源 ${coverage.missingResources}`" />

                <el-table :data="resources" stripe>
                    <el-table-column prop="appCode" label="应用" width="150" />
                    <el-table-column prop="resourceCode" label="资源标识" width="200" />
                    <el-table-column prop="name" label="名称" width="180" />
                    <el-table-column prop="resourceType" label="类型" width="110" />
                    <el-table-column prop="tenantScope" label="作用域" width="110" />
                    <el-table-column label="动作" min-width="220">
                        <template #default="{ row }">
                            <el-tag v-for="a in row.actions" :key="a.action" size="small"
                                :type="a.riskLevel === 'HIGH' ? 'danger' : 'info'" style="margin-right: 6px">
                                {{ a.action }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="status" label="状态" width="110" />
                    <el-table-column label="操作" width="150">
                        <template #default="{ row }">
                            <el-button link type="primary" @click="openResource(row)">编辑</el-button>
                            <el-popconfirm title="下线后不可再被新授权，确认？" @confirm="deprecate(row)">
                                <template #reference><el-button link type="danger">下线</el-button></template>
                            </el-popconfirm>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>
        </el-tabs>

        <!-- 授权编辑 -->
        <el-dialog v-model="grantDialog" :title="grantForm.id ? '编辑授权' : '新增授权'" width="760px">
            <el-form :model="grantForm" label-width="96px">
                <el-form-item label="主体">
                    <el-input :model-value="`${grantQuery.subjectType}:${grantQuery.subjectId}`" disabled />
                </el-form-item>
                <el-form-item label="应用"><el-input v-model="grantForm.appCode" :disabled="!!grantForm.id" /></el-form-item>
                <el-form-item label="资源">
                    <el-select v-model="grantForm.resourceCode" filterable :disabled="!!grantForm.id"
                        style="width: 100%" @change="onGrantResourceChange">
                        <el-option v-for="r in resources.filter(x => !grantForm.appCode || x.appCode === grantForm.appCode)"
                            :key="r.id" :label="`${r.resourceCode}（${r.name}）`" :value="r.resourceCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="动作">
                    <el-checkbox-group v-model="grantForm.actionsList">
                        <el-checkbox v-for="a in actionsForResource()" :key="a" :value="a" :label="a" />
                    </el-checkbox-group>
                </el-form-item>
                <el-form-item v-if="grantMissingRead">
                    <el-alert type="warning" :closable="false" show-icon
                        title="已勾选写类动作但未勾选“读取”：读接口（如 /data/metadata、列表查询）会 403，形成“能改不能看”" />
                </el-form-item>
                <el-form-item label="效果">
                    <el-radio-group v-model="grantForm.effect">
                        <el-radio value="ALLOW">ALLOW（允许）</el-radio>
                        <el-radio value="DENY">DENY（拒绝，优先）</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="数据范围">
                    <el-select v-model="grantForm.dataScope" style="width: 100%">
                        <el-option v-for="s in ['ALL', 'SELF', 'DEPT', 'DEPT_TREE', 'DEPT_LIST', 'NONE']"
                            :key="s" :label="s" :value="s" />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="grantForm.dataScope === 'DEPT_LIST'" label="部门编码">
                    <el-input v-model="grantForm.deptCodesText" placeholder="逗号分隔，如 D01,D02" />
                </el-form-item>
                <el-form-item label="失效时间">
                    <el-date-picker v-model="grantForm.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="留空=长期" style="width: 100%" />
                </el-form-item>
                <el-form-item label="原因"><el-input v-model="grantForm.reason" placeholder="变更留痕用" /></el-form-item>
            </el-form>
            <el-alert v-if="previewResult" type="warning" :closable="false" style="margin-bottom: 10px"
                :title="`预计影响 ${previewResult.affectedUsers} 人：` + previewResult.diffs.map(d => `${d.resourceCode} +${d.added.length}/-${d.removed.length}/ deny ${d.denied.length}`).join('；')" />
            <template #footer>
                <el-button @click="doPreview">预演影响面</el-button>
                <el-button @click="grantDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveGrantForm">保存</el-button>
            </template>
        </el-dialog>

        <!-- 批量授权：多主体同一资源动作/范围 -->
        <el-dialog v-model="batchDialog" title="批量授权" width="760px">
            <el-form :model="batchForm" label-width="110px">
                <el-form-item label="主体类型">
                    <el-select v-model="batchForm.subjectType" style="width: 100%">
                        <el-option label="角色" value="ROLE" />
                        <el-option label="用户" value="USER" />
                        <el-option label="岗位" value="POSITION" />
                        <el-option label="应用" value="APP" />
                    </el-select>
                </el-form-item>
                <el-form-item label="主体ID">
                    <el-input v-model="batchForm.subjectIdsText" type="textarea" :rows="3"
                        placeholder="多个主体用逗号或换行分隔，如 role_a,role_b" />
                </el-form-item>
                <el-form-item label="应用"><el-input v-model="batchForm.appCode" /></el-form-item>
                <el-form-item label="资源">
                    <el-select v-model="batchForm.resourceCode" filterable style="width: 100%"
                        @change="onBatchResourceChange">
                        <el-option v-for="r in resources.filter(x => !batchForm.appCode || x.appCode === batchForm.appCode)"
                            :key="r.id" :label="`${r.resourceCode}（${r.name}）`" :value="r.resourceCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="动作">
                    <el-checkbox-group v-model="batchForm.actionsList">
                        <el-checkbox v-for="a in batchActions()" :key="a" :value="a" :label="a" />
                    </el-checkbox-group>
                </el-form-item>
                <el-form-item v-if="batchMissingRead">
                    <el-alert type="warning" :closable="false" show-icon
                        title="已勾选写类动作但未勾选“读取”：读接口（如 /data/metadata、列表查询）会 403，形成“能改不能看”" />
                </el-form-item>
                <el-form-item label="效果">
                    <el-radio-group v-model="batchForm.effect">
                        <el-radio value="ALLOW">ALLOW（允许）</el-radio>
                        <el-radio value="DENY">DENY（拒绝，优先）</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="数据范围">
                    <el-select v-model="batchForm.dataScope" style="width: 100%">
                        <el-option v-for="s in ['ALL', 'SELF', 'DEPT', 'DEPT_TREE', 'DEPT_LIST', 'NONE']"
                            :key="s" :label="s" :value="s" />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="batchForm.dataScope === 'DEPT_LIST'" label="部门编码">
                    <el-input v-model="batchForm.deptCodesText" placeholder="逗号分隔，如 D01,D02" />
                </el-form-item>
                <el-form-item label="失效时间">
                    <el-date-picker v-model="batchForm.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="留空=长期" style="width: 100%" />
                </el-form-item>
                <el-form-item label="原因"><el-input v-model="batchForm.reason" placeholder="变更留痕用" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="batchDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="doBatch">应用</el-button>
            </template>
        </el-dialog>

        <!-- 模板编辑 -->
        <el-dialog v-model="templateDialog" :title="templateForm.id ? '编辑模板' : '新增模板'" width="760px">
            <el-form :model="templateForm" label-width="96px">
                <el-form-item label="编码"><el-input v-model="templateForm.templateCode" :disabled="!!templateForm.id" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="templateForm.name" /></el-form-item>
                <el-form-item label="应用"><el-input v-model="templateForm.appCode" /></el-form-item>
                <el-form-item label="说明"><el-input v-model="templateForm.description" /></el-form-item>
                <el-form-item label="角色与授权">
                    <el-input v-model="templateForm.itemsJson" type="textarea" :rows="12"
                        placeholder='[{"roleCode":"clerk","roleName":"业务员","appCode":"wform","grants":[{"appCode":"wform","resourceCode":"wform:data","actions":["read"],"dataScope":"SELF","effect":"ALLOW"}]}]' />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="templateDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveTemplateForm">保存</el-button>
            </template>
        </el-dialog>

        <!-- 应用模板 -->
        <el-dialog v-model="applyDialog" title="应用模板到租户" width="520px">
            <el-form label-width="96px">
                <el-form-item label="模板">{{ applyForm.templateName }}</el-form-item>
                <el-form-item label="目标租户"><el-input v-model="applyForm.tenantCode" /></el-form-item>
                <el-form-item label="原因"><el-input v-model="applyForm.reason" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="applyDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="doApplyTemplate">应用</el-button>
            </template>
        </el-dialog>

        <!-- 资源编辑 -->
        <el-dialog v-model="resourceDialog" :title="resourceForm.id ? '编辑资源' : '新增资源'" width="680px">
            <el-form :model="resourceForm" label-width="96px">
                <el-form-item label="应用">
                    <el-input v-model="resourceForm.appCode" :disabled="!!resourceForm.id" />
                </el-form-item>
                <el-form-item label="资源标识">
                    <el-input v-model="resourceForm.resourceCode" :disabled="!!resourceForm.id"
                        placeholder="字母开头，如 user、wform:designer" />
                </el-form-item>
                <el-form-item label="名称"><el-input v-model="resourceForm.name" /></el-form-item>
                <el-form-item label="类型">
                    <el-select v-model="resourceForm.resourceType" style="width: 100%">
                        <el-option v-for="t in ['PAGE', 'CAPABILITY', 'API', 'DATA', 'FIELD']" :key="t"
                            :label="t" :value="t" />
                    </el-select>
                </el-form-item>
                <el-form-item label="作用域">
                    <el-select v-model="resourceForm.tenantScope" style="width: 100%">
                        <el-option label="PLATFORM（全平台）" value="PLATFORM" />
                        <el-option label="SHARED（所有租户可授权）" value="SHARED" />
                        <el-option label="TENANT（私有）" value="TENANT" />
                    </el-select>
                </el-form-item>
                <el-form-item label="归属租户">
                    <el-input v-model="resourceForm.ownerTenantCode" placeholder="PLATFORM 固定 default" />
                </el-form-item>
                <el-form-item label="动作">
                    <el-checkbox-group v-model="resourceForm.actions">
                        <el-checkbox v-for="a in actionVocabulary" :key="a" :value="a" :label="a" />
                    </el-checkbox-group>
                </el-form-item>
                <el-form-item label="说明"><el-input v-model="resourceForm.description" type="textarea" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="resourceDialog = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveResourceForm">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
    applyTemplate, batchGrant, changes, decide, deleteGrant, effective, grants as listGrants, preview, rollbackChange,
    saveGrant, saveTemplate, templates as listTemplates,
    type Decision, type EffectiveResource, type Grant, type GrantChange, type GrantTemplate, type PreviewResult
} from '@/api/authz'
import {
    deprecateResource, listResources, resourceCoverage, saveResource,
    type CoverageReport, type ResourceVo
} from '@/api/resource'

const tab = ref('effective')

const employeeId = ref('')
const effectiveList = ref<EffectiveResource[]>([])
const effectiveQueried = ref(false)

const diag = ref({ employeeId: '', appCode: 'nway-system', resourceCode: 'user', action: 'view' })
const decision = ref<Decision | null>(null)

const changeQuery = ref({ targetType: 'ROLE', targetId: '' })
const changeList = ref<GrantChange[]>([])

// ====== 授权维护 ======
const grantQuery = ref({ subjectType: 'ROLE', subjectId: '' })
const grantList = ref<Grant[]>([])
const grantDialog = ref(false)
const previewResult = ref<PreviewResult | null>(null)
const grantForm = ref<any>({
    appCode: 'nway-system', resourceCode: '', actionsList: [] as string[], effect: 'ALLOW',
    dataScope: 'SELF', deptCodesText: '', expireAt: null, reason: ''
})

// ====== 模板/套餐 ======
const templateList = ref<GrantTemplate[]>([])
const templateDialog = ref(false)
const templateForm = ref<any>({ templateCode: '', name: '', appCode: 'nway-system', description: '', itemsJson: '' })
const applyDialog = ref(false)
const applyForm = ref({ templateId: '', templateName: '', tenantCode: '', reason: '' })

function parseActions(json?: string | null): string[] {
    if (!json) return []
    try {
        const list = JSON.parse(json)
        return Array.isArray(list) ? list : []
    } catch {
        return []
    }
}

function actionsForResource(): string[] {
    return actionsFor(grantForm.value.appCode, grantForm.value.resourceCode)
}

function actionsFor(appCode: string, resourceCode: string): string[] {
    const resource = resources.value.find(r => r.appCode === appCode && r.resourceCode === resourceCode)
    return (resource?.actions || []).map(a => a.action)
}

/** 写类动作：与后端 GrantActionAdvisor 保持同一词表 */
const WRITE_FAMILY = ['write', 'delete', 'export', 'import']

/**
 * “有写无读”提示：只在该资源目录确实声明了 read 时提示，
 * 避免只写资源（如 wform:dict 只有 write）误报。
 */
function missingRead(appCode: string, resourceCode: string, actions: string[] | undefined): boolean {
    const selected = new Set(actions || [])
    if (selected.has('*') || selected.has('read')) return false
    if (!WRITE_FAMILY.some(a => selected.has(a))) return false
    return actionsFor(appCode, resourceCode).includes('read')
}

const grantMissingRead = computed(() =>
    missingRead(grantForm.value.appCode, grantForm.value.resourceCode, grantForm.value.actionsList))

function onGrantResourceChange() {
    grantForm.value.actionsList = actionsForResource()
}

async function loadGrants() {
    if (!grantQuery.value.subjectId) {
        ElMessage.warning('请输入主体ID')
        return
    }
    const res = await listGrants(grantQuery.value.subjectType, grantQuery.value.subjectId)
    grantList.value = res.data || []
}

function openGrant(row?: Grant) {
    if (!grantQuery.value.subjectId) {
        ElMessage.warning('请先指定主体')
        return
    }
    previewResult.value = null
    if (row) {
        const deptCodes = parseActions(row.scopeDeptIds)
        grantForm.value = {
            ...row,
            actionsList: parseActions(row.actions),
            deptCodesText: deptCodes.join(','),
            reason: ''
        }
    } else {
        grantForm.value = {
            appCode: 'nway-system', resourceCode: '', actionsList: [], effect: 'ALLOW',
            dataScope: 'SELF', deptCodesText: '', expireAt: null, reason: ''
        }
    }
    grantDialog.value = true
}

function buildGrantPayload(): Grant {
    const deptCodes = grantForm.value.deptCodesText
        ? grantForm.value.deptCodesText.split(',').map((s: string) => s.trim()).filter(Boolean)
        : []
    return {
        id: grantForm.value.id,
        subjectType: grantQuery.value.subjectType,
        subjectId: grantQuery.value.subjectId,
        appCode: grantForm.value.appCode,
        resourceCode: grantForm.value.resourceCode,
        actions: JSON.stringify(grantForm.value.actionsList || []),
        effect: grantForm.value.effect || 'ALLOW',
        dataScope: grantForm.value.dataScope || 'SELF',
        scopeDeptIds: grantForm.value.dataScope === 'DEPT_LIST' ? JSON.stringify(deptCodes) : null,
        expireAt: grantForm.value.expireAt || null,
        status: 'ACTIVE'
    }
}

async function doPreview() {
    const res = await preview({
        subjectType: grantQuery.value.subjectType,
        subjectId: grantQuery.value.subjectId,
        grants: [buildGrantPayload()]
    })
    previewResult.value = res.data
}

async function saveGrantForm() {
    if (!grantForm.value.resourceCode) {
        ElMessage.warning('请选择资源')
        return
    }
    saving.value = true
    try {
        await saveGrant(buildGrantPayload(), grantForm.value.reason || '工作台授权')
        ElMessage.success('保存成功')
        grantDialog.value = false
        await loadGrants()
    } finally {
        saving.value = false
    }
}

async function removeGrant(row: Grant) {
    await deleteGrant(row.id!, '工作台删除')
    ElMessage.success('已删除')
    await loadGrants()
}

// ====== 批量授权 ======
const batchDialog = ref(false)
const batchForm = ref<any>({
    subjectType: 'ROLE', subjectIdsText: '', appCode: 'nway-system', resourceCode: '',
    actionsList: [] as string[], effect: 'ALLOW', dataScope: 'SELF', deptCodesText: '',
    expireAt: null, reason: ''
})

const batchMissingRead = computed(() =>
    missingRead(batchForm.value.appCode, batchForm.value.resourceCode, batchForm.value.actionsList))

function openBatch() {
    batchForm.value = {
        subjectType: grantQuery.value.subjectType, subjectIdsText: '', appCode: 'nway-system',
        resourceCode: '', actionsList: [], effect: 'ALLOW', dataScope: 'SELF',
        deptCodesText: '', expireAt: null, reason: ''
    }
    batchDialog.value = true
}

function batchActions(): string[] {
    return actionsFor(batchForm.value.appCode, batchForm.value.resourceCode)
}

function onBatchResourceChange() {
    batchForm.value.actionsList = batchActions()
}

async function doBatch() {
    const subjectIds = batchForm.value.subjectIdsText
        .split(/[,\s;]+/).map((s: string) => s.trim()).filter(Boolean)
    if (subjectIds.length === 0) {
        ElMessage.warning('请填写至少一个主体ID')
        return
    }
    if (!batchForm.value.resourceCode) {
        ElMessage.warning('请选择资源')
        return
    }
    const deptCodes = batchForm.value.deptCodesText
        ? batchForm.value.deptCodesText.split(',').map((s: string) => s.trim()).filter(Boolean)
        : []
    saving.value = true
    try {
        const res = await batchGrant({
            subjectType: batchForm.value.subjectType,
            subjectIds,
            reason: batchForm.value.reason || '批量授权',
            grant: {
                subjectType: batchForm.value.subjectType,
                subjectId: subjectIds[0],
                appCode: batchForm.value.appCode,
                resourceCode: batchForm.value.resourceCode,
                actions: JSON.stringify(batchForm.value.actionsList || []),
                effect: batchForm.value.effect,
                dataScope: batchForm.value.dataScope,
                scopeDeptIds: batchForm.value.dataScope === 'DEPT_LIST' ? JSON.stringify(deptCodes) : null,
                expireAt: batchForm.value.expireAt || null,
                status: 'ACTIVE'
            }
        })
        ElMessage.success(`已为 ${res.data ?? 0} 个主体写入授权`)
        batchDialog.value = false
        if (grantQuery.value.subjectId) await loadGrants()
    } finally {
        saving.value = false
    }
}

async function loadTemplates() {
    const res = await listTemplates()
    templateList.value = res.data || []
}

function openTemplate(row?: GrantTemplate) {
    templateForm.value = row
        ? { ...row }
        : { templateCode: '', name: '', appCode: 'nway-system', description: '', itemsJson: '' }
    templateDialog.value = true
}

async function saveTemplateForm() {
    saving.value = true
    try {
        await saveTemplate(templateForm.value)
        ElMessage.success('模板已保存')
        templateDialog.value = false
        await loadTemplates()
    } finally {
        saving.value = false
    }
}

function openApply(row: GrantTemplate) {
    applyForm.value = {
        templateId: row.id || '',
        templateName: row.name,
        tenantCode: '',
        reason: ''
    }
    applyDialog.value = true
}

async function doApplyTemplate() {
    saving.value = true
    try {
        const res = await applyTemplate(applyForm.value.templateId, applyForm.value.tenantCode,
            applyForm.value.reason || '模板开通')
        ElMessage.success(`已应用：角色 ${res.data?.roleCount ?? 0}，新建 ${res.data?.createdRoles ?? 0}，授权 ${res.data?.grantCount ?? 0}`)
        applyDialog.value = false
    } finally {
        saving.value = false
    }
}

const resourceAppCode = ref('')
const resources = ref<ResourceVo[]>([])
const coverage = ref<CoverageReport | null>(null)
const resourceDialog = ref(false)
const saving = ref(false)
const actionVocabulary = ['read', 'view', 'write', 'create', 'edit', 'delete', 'export', 'import',
    'approve', 'execute', 'design', 'publish', 'reset', 'grant', 'revoke']
const resourceForm = ref<any>({ actions: [] as string[] })

async function loadEffective() {
    if (!employeeId.value) {
        ElMessage.warning('请输入工号')
        return
    }
    const res = await effective(employeeId.value)
    effectiveList.value = res.data || []
    effectiveQueried.value = true
}

async function runDiagnose() {
    const res = await decide(diag.value.employeeId, diag.value.appCode,
        diag.value.resourceCode, diag.value.action)
    decision.value = res.data
}

async function loadChanges() {
    if (!changeQuery.value.targetId) {
        ElMessage.warning('请输入主体ID')
        return
    }
    const res = await changes(changeQuery.value.targetType, changeQuery.value.targetId)
    changeList.value = res.data || []
}

async function loadResources() {
    const res = await listResources(resourceAppCode.value || undefined)
    resources.value = res.data || []
}

async function loadCoverage() {
    const res = await resourceCoverage()
    coverage.value = res.data
}

function openResource(row?: ResourceVo) {
    if (row) {
        resourceForm.value = {
            ...row,
            actions: row.actions.map(a => a.action)
        }
    } else {
        resourceForm.value = {
            appCode: 'nway-system', resourceCode: '', name: '', resourceType: 'PAGE',
            tenantScope: 'TENANT', ownerTenantCode: '', description: '', actions: []
        }
    }
    resourceDialog.value = true
}

async function saveResourceForm() {
    saving.value = true
    try {
        await saveResource({
            ...resourceForm.value,
            actions: resourceForm.value.actions.map((a: string) => ({ action: a, label: a }))
        })
        ElMessage.success('保存成功')
        resourceDialog.value = false
        await loadResources()
        if (coverage.value) await loadCoverage()
    } finally {
        saving.value = false
    }
}

async function deprecate(row: ResourceVo) {
    await deprecateResource(row.id)
    ElMessage.success('已下线')
    await loadResources()
}

async function doRollback(row: GrantChange) {
    await rollbackChange(row.id, '工作台回滚')
    ElMessage.success('已回滚')
    await loadChanges()
}

function pretty(json: string) {
    if (!json) return '—'
    try {
        return JSON.stringify(JSON.parse(json), null, 2)
    } catch {
        return json
    }
}

onMounted(async () => {
    await Promise.all([loadResources(), loadTemplates()])
})
</script>

<style scoped>
.authz-page {
    width: 98%;
    margin: 10px auto;
}

.diff-block pre {
    max-height: 220px;
    overflow: auto;
    background: #f5f7fa;
    padding: 8px;
    font-size: 12px;
}
</style>
