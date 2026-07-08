<template>
  <div class="home-container">
    <!-- 顶部导航栏: 严谨的深蓝政务顶栏 -->
    <header class="navbar">
      <div class="logo-area">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="system-title">人口数据库管理系统</span>
        <span class="system-badge">管理后台</span>
      </div>
      <div class="user-area" v-if="username">
        <span class="username">
          欢迎您，{{ username }} 
          <span class="role-badge">{{ role === 'admin' ? '系统管理员' : role }}</span>
        </span>
        <el-button class="logout-btn" text @click="handleLogout">
          <el-icon class="logout-icon"><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </header>

    <div class="main-layout">
      <!-- 侧边栏: 可扩展的模块导航 -->
      <aside class="sidebar">
        <nav class="side-nav">
          <a href="#" class="nav-item" :class="{ active: currentModule === 'person' }" @click="switchModule('person')">
            <el-icon><User /></el-icon> 人口信息管理
          </a>
          <a href="#" class="nav-item" :class="{ active: currentModule === 'household' }" @click="switchModule('household')">
            <el-icon><HomeFilled /></el-icon> 家庭户籍系统
          </a>
          <a href="#" class="nav-item" :class="{ active: currentModule === 'migration' }" @click="switchModule('migration')">
            <el-icon><Switch /></el-icon> 迁入迁出管理
          </a>
          <a href="#" class="nav-item" :class="{ active: currentModule === 'certificate' }" @click="switchModule('certificate')">
            <el-icon><Postcard /></el-icon> 证件管理
          </a>
        </nav>
        <div class="sidebar-footer">
          <p>系统版本 V1.0</p>
          <p>东软政府事业部</p>
        </div>
      </aside>

      <!-- 主体内容区 -->
      <main class="main-content">
        
        <!-- ==================== 模块 1：人口信息管理 ==================== -->
        <div v-if="currentModule === 'person'" class="module-wrapper">
          <div class="page-header">
            <div class="header-left">
              <h1>人口信息管理</h1>
              <p class="subtitle">维护辖区常驻人员的基础卡片信息，支持条件检索及档案登记。</p>
            </div>
            <div class="header-right">
              <el-button type="primary" :icon="Plus" @click="openPersonDialog()">登记新人口</el-button>
            </div>
          </div>

          <!-- 搜索过滤栏 -->
          <div class="filter-bar">
            <el-form :inline="true" :model="personQuery" size="default">
              <el-form-item label="姓名">
                <el-input v-model="personQuery.name" placeholder="请输入姓名" clearable />
              </el-form-item>
              <el-form-item label="身份证号">
                <el-input v-model="personQuery.idCard" placeholder="请输入身份证号" clearable />
              </el-form-item>
              <el-form-item label="当前状态">
                <el-select v-model="personQuery.status" placeholder="全部状态" clearable style="width: 130px;">
                  <el-option label="正常" value="正常" />
                  <el-option label="已注销" value="已注销" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handlePersonSearch">查询</el-button>
                <el-button @click="resetPersonQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 数据表格 -->
          <div class="data-table-card">
            <el-table :data="personsList" v-loading="personLoading" border stripe style="width: 100%">
              <el-table-column prop="name" label="姓名" width="100" fixed />
              <el-table-column prop="gender" label="性别" width="70" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.gender === '男' ? 'primary' : 'danger'" size="small">
                    {{ scope.row.gender }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="idCard" label="身份证号" width="180" align="center" />
              <el-table-column prop="birthDate" label="出生日期" width="120" align="center" />
              <el-table-column prop="ethnicity" label="民族" width="100" align="center" />
              <el-table-column prop="phone" label="联系电话" width="130" align="center" />
              <el-table-column prop="currentAddress" label="现住址" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.status === '正常' ? 'success' : 'info'" size="small">
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="openPersonDialog(scope.row)">修改</el-button>
                  <el-button 
                    size="small" 
                    type="danger" 
                    link 
                    :disabled="scope.row.status === '已注销'"
                    @click="handlePersonDelete(scope.row)"
                  >
                    注销
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页栏 -->
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="personPage.currentPage"
                v-model:page-size="personPage.pageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="personPage.totalElements"
                @size-change="handlePersonSizeChange"
                @current-change="handlePersonPageChange"
              />
            </div>
          </div>
        </div>

        <!-- ==================== 模块 2：家庭户籍系统 ==================== -->
        <div v-else-if="currentModule === 'household'" class="module-wrapper">
          <div class="page-header">
            <div class="header-left">
              <h1>家庭户籍系统</h1>
              <p class="subtitle">确立户籍住址，绑定户主关系，集中管理社区家庭单元信息。</p>
            </div>
            <div class="header-right">
              <el-button type="primary" :icon="Plus" @click="openHouseholdDialog()">开户立户</el-button>
            </div>
          </div>

          <!-- 搜索栏 -->
          <div class="filter-bar">
            <el-form :inline="true" :model="householdQuery" size="default">
              <el-form-item label="户籍编号">
                <el-input v-model="householdQuery.householdNo" placeholder="请输入户号" clearable />
              </el-form-item>
              <el-form-item label="家庭住址">
                <el-input v-model="householdQuery.address" placeholder="请输入地址关键词" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleHouseholdSearch">查询</el-button>
                <el-button @click="resetHouseholdQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 数据表格 -->
          <div class="data-table-card">
            <el-table :data="filteredHouseholds" border stripe style="width: 100%">
              <el-table-column prop="householdNo" label="户口编号" width="130" align="center" />
              <el-table-column prop="headName" label="户主姓名" width="120" align="center" />
              <el-table-column prop="address" label="家庭住址" min-width="250" show-overflow-tooltip />
              <el-table-column prop="establishDate" label="立户日期" width="130" align="center" />
              <el-table-column prop="memberCount" label="家庭成员数" width="110" align="center">
                <template #default="scope">
                  <el-tag size="small" type="info">{{ scope.row.memberCount }} 人</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.status === '正常' ? 'success' : 'danger'" size="small">
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="viewHouseholdMembers(scope.row)">家庭成员</el-button>
                  <el-button size="small" type="primary" link @click="openHouseholdDialog(scope.row)">修改</el-button>
                  <el-button size="small" type="danger" link @click="handleHouseholdDelete(scope.row)">撤销</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- ==================== 模块 3：迁入迁出管理 ==================== -->
        <div v-else-if="currentModule === 'migration'" class="module-wrapper">
          <div class="page-header">
            <div class="header-left">
              <h1>迁入迁出管理</h1>
              <p class="subtitle">追踪社区常驻人员流动变更历史，记录迁移原因与去向。</p>
            </div>
            <div class="header-right">
              <el-button type="primary" :icon="Plus" @click="openMigrationDialog('in')">办理迁入</el-button>
              <el-button type="danger" :icon="Plus" @click="openMigrationDialog('out')">办理迁出</el-button>
            </div>
          </div>

          <!-- 搜索栏 -->
          <div class="filter-bar">
            <el-form :inline="true" :model="migrationQuery" size="default">
              <el-form-item label="姓名">
                <el-input v-model="migrationQuery.name" placeholder="请输入姓名" clearable />
              </el-form-item>
              <el-form-item label="变更类型">
                <el-select v-model="migrationQuery.type" placeholder="全部类型" clearable style="width: 130px;">
                  <el-option label="迁入登记" value="迁入" />
                  <el-option label="迁出登记" value="迁出" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleMigrationSearch">查询</el-button>
                <el-button @click="resetMigrationQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 数据表格 -->
          <div class="data-table-card">
            <el-table :data="filteredMigrations" border stripe style="width: 100%">
              <el-table-column prop="personName" label="人员姓名" width="110" align="center" />
              <el-table-column prop="idCard" label="身份证号" width="180" align="center" />
              <el-table-column prop="type" label="业务类型" width="110" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.type === '迁入' ? 'success' : 'danger'" size="small">
                    {{ scope.row.type === '迁入' ? '迁入登记' : '迁出注销' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="migrationDate" label="迁移日期" width="130" align="center" />
              <el-table-column prop="address" label="来源地 / 迁往地" min-width="250" show-overflow-tooltip />
              <el-table-column prop="reason" label="迁移原因" min-width="150" />
            </el-table>
          </div>
        </div>

        <!-- ==================== 模块 4：证件管理 ==================== -->
        <div v-else-if="currentModule === 'certificate'" class="module-wrapper">
          <div class="page-header">
            <div class="header-left">
              <h1>证件信息管理</h1>
              <p class="subtitle">记录并管辖人员相关的身份证、居住证件，内置到期状态自动预警。</p>
            </div>
            <div class="header-right">
              <el-button type="primary" :icon="Plus" @click="openCertificateDialog()">颁发/登记证件</el-button>
            </div>
          </div>

          <!-- 搜索栏 -->
          <div class="filter-bar">
            <el-form :inline="true" :model="certQuery" size="default">
              <el-form-item label="姓名">
                <el-input v-model="certQuery.name" placeholder="姓名" clearable />
              </el-form-item>
              <el-form-item label="证件号">
                <el-input v-model="certQuery.certNo" placeholder="证件号" clearable />
              </el-form-item>
              <el-form-item label="证件状态">
                <el-select v-model="certQuery.status" placeholder="全部状态" clearable style="width: 130px;">
                  <el-option label="有效" value="有效" />
                  <el-option label="即将到期" value="即将到期" />
                  <el-option label="已过期" value="已过期" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleCertSearch">查询</el-button>
                <el-button @click="resetCertQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 数据表格 -->
          <div class="data-table-card">
            <el-table :data="filteredCertificates" border stripe style="width: 100%">
              <el-table-column prop="personName" label="持有者姓名" width="110" align="center" />
              <el-table-column prop="certType" label="证件类型" width="130" align="center" />
              <el-table-column prop="certNo" label="证件编号" width="200" align="center" />
              <el-table-column prop="issueDate" label="签发日期" width="130" align="center" />
              <el-table-column prop="expireDate" label="有效期至" width="130" align="center" />
              <el-table-column prop="status" label="证件状态" width="120" align="center">
                <template #default="scope">
                  <el-tag :type="getCertStatusType(scope.row.status)" size="small">
                    {{ scope.row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="scope">
                  <el-button size="small" type="primary" link @click="openCertificateDialog(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" link @click="handleCertDelete(scope.row)">作废</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

      </main>
    </div>

    <!-- ==================== 弹窗区 ==================== -->

    <!-- 人口信息登记/修改弹窗 -->
    <el-dialog 
      v-model="personDialog.visible" 
      :title="personDialog.isEdit ? '修改人口信息' : '登记新常驻人口'" 
      width="600px"
      destroy-on-close
    >
      <el-form 
        ref="personFormRef" 
        :model="personForm" 
        :rules="personRules" 
        label-width="100px" 
        size="large"
        style="padding: 10px 20px 0 0;"
      >
        <el-form-item label="真实姓名" prop="name">
          <el-input v-model="personForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="personForm.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="personForm.idCard" placeholder="18位居民身份证号码" :disabled="personDialog.isEdit" />
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker 
            v-model="personForm.birthDate" 
            type="date" 
            placeholder="请选择日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="民族" prop="ethnicity">
          <el-input v-model="personForm.ethnicity" placeholder="如：汉族" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="personForm.phone" placeholder="手机号码或座机" />
        </el-form-item>
        <el-form-item label="现居住住址" prop="currentAddress">
          <el-input v-model="personForm.currentAddress" type="textarea" :rows="2" placeholder="详细居住地址" />
        </el-form-item>
        <el-form-item label="人口状态" prop="status">
          <el-select v-model="personForm.status" style="width: 100%;">
            <el-option label="正常" value="正常" />
            <el-option label="已注销" value="已注销" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="personDialog.visible = false" size="large">取消</el-button>
          <el-button type="primary" :loading="personDialog.submitting" @click="submitPersonForm" size="large">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 家庭开户/修改弹窗 -->
    <el-dialog 
      v-model="householdDialog.visible" 
      :title="householdDialog.isEdit ? '修改户籍住址' : '家庭户籍开户立户'" 
      width="550px"
      destroy-on-close
    >
      <el-form 
        ref="householdFormRef" 
        :model="householdForm" 
        :rules="householdRules" 
        label-width="100px" 
        size="large"
        style="padding: 10px 20px 0 0;"
      >
        <el-form-item label="户口编号" prop="householdNo" v-if="householdDialog.isEdit">
          <el-input v-model="householdForm.householdNo" disabled />
        </el-form-item>
        <el-form-item label="选择户主" prop="headPersonId">
          <el-select v-model="householdForm.headPersonId" placeholder="选择辖区内的常驻人口作为户主" style="width: 100%;">
            <el-option 
              v-for="p in allPersonsRaw" 
              :key="p.personId" 
              :label="p.name + ' (' + p.idCard + ')'" 
              :value="p.personId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="户籍地址" prop="address">
          <el-input v-model="householdForm.address" type="textarea" :rows="2" placeholder="请输入本户的标准户籍家庭住址" />
        </el-form-item>
        <el-form-item label="立户时间" prop="establishDate">
          <el-date-picker 
            v-model="householdForm.establishDate" 
            type="date" 
            placeholder="选择立户落户日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="householdDialog.visible = false" size="large">取消</el-button>
          <el-button type="primary" @click="submitHouseholdForm" size="large">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 户籍成员弹窗 -->
    <el-dialog v-model="membersDialog.visible" title="家庭户籍成员清单" width="650px">
      <div style="margin-bottom: 12px; font-weight: 500;">
        户籍编号：<span style="color: var(--color-accent);">{{ membersDialog.householdNo }}</span> &nbsp;|&nbsp; 
        户籍地址：<span>{{ membersDialog.address }}</span>
      </div>
      <el-table :data="membersDialog.list" border stripe size="small">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="relationship" label="与户主关系" width="110" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.relationship === '户主' ? 'primary' : 'info'" size="small">
              {{ scope.row.relationship }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="idCard" label="身份证号" width="180" align="center" />
        <el-table-column prop="phone" label="联系电话" width="130" align="center" />
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="membersDialog.visible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 办理迁入迁出弹窗 -->
    <el-dialog 
      v-model="migrationDialog.visible" 
      :title="migrationDialog.mode === 'in' ? '办理户口迁入登记' : '办理户口迁出注销'" 
      width="550px"
      destroy-on-close
    >
      <el-form 
        ref="migrationFormRef" 
        :model="migrationForm" 
        :rules="migrationRules" 
        label-width="110px" 
        size="large"
        style="padding: 10px 20px 0 0;"
      >
        <el-form-item label="选择办理人" prop="personId">
          <el-select v-model="migrationForm.personId" placeholder="请选择对应人口" style="width: 100%;">
            <el-option 
              v-for="p in allPersonsRaw" 
              :key="p.personId" 
              :label="p.name + ' (' + p.idCard + ')'" 
              :value="p.personId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变更日期" prop="migrationDate">
          <el-date-picker 
            v-model="migrationForm.migrationDate" 
            type="date" 
            placeholder="请选择办理日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item :label="migrationDialog.mode === 'in' ? '来源省市' : '迁往省市'" prop="address">
          <el-input v-model="migrationForm.address" placeholder="省、市、区及详细住址" />
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input v-model="migrationForm.reason" type="textarea" :rows="2" placeholder="请简要描述迁移原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="migrationDialog.visible = false" size="large">取消</el-button>
          <el-button type="primary" @click="submitMigrationForm" size="large">确定提交</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 证件登记弹窗 -->
    <el-dialog 
      v-model="certificateDialog.visible" 
      :title="certificateDialog.isEdit ? '修改证件信息' : '登记/颁发新证件'" 
      width="550px"
      destroy-on-close
    >
      <el-form 
        ref="certificateFormRef" 
        :model="certificateForm" 
        :rules="certificateRules" 
        label-width="100px" 
        size="large"
        style="padding: 10px 20px 0 0;"
      >
        <el-form-item label="持有人" prop="personId" v-if="!certificateDialog.isEdit">
          <el-select v-model="certificateForm.personId" placeholder="选择证件持有人" style="width: 100%;">
            <el-option 
              v-for="p in allPersonsRaw" 
              :key="p.personId" 
              :label="p.name + ' (' + p.idCard + ')'" 
              :value="p.personId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="持有人" v-else>
          <el-input v-model="certificateForm.personName" disabled />
        </el-form-item>
        <el-form-item label="证件类型" prop="certType">
          <el-select v-model="certificateForm.certType" style="width: 100%;">
            <el-option label="居民身份证" value="居民身份证" />
            <el-option label="居住证" value="居住证" />
            <el-option label="临时居住证" value="临时居住证" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件编号" prop="certNo">
          <el-input v-model="certificateForm.certNo" placeholder="请输入全国统一的证件编号" />
        </el-form-item>
        <el-form-item label="签发日期" prop="issueDate">
          <el-date-picker 
            v-model="certificateForm.issueDate" 
            type="date" 
            placeholder="签发日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="到期日期" prop="expireDate">
          <el-date-picker 
            v-model="certificateForm.expireDate" 
            type="date" 
            placeholder="有效期截至日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="certificateForm.status" style="width: 100%;">
            <el-option label="有效" value="有效" />
            <el-option label="即将到期" value="即将到期" />
            <el-option label="已过期" value="已过期" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="certificateDialog.visible = false" size="large">取消</el-button>
          <el-button type="primary" @click="submitCertificateForm" size="large">确 定</el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { 
  Platform, User, HomeFilled, Switch, Postcard, Setting, SwitchButton, 
  Plus
} from '@element-plus/icons-vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { getPersonList, createPerson, updatePerson, deletePerson } from '../api/person';

const router = useRouter();
const username = ref('');
const role = ref('');
const currentModule = ref('person');

// 全局备用的完整人口列表（用于下拉选择联动）
const allPersonsRaw = ref([]);

onMounted(() => {
  username.value = localStorage.getItem('username') || '';
  role.value = localStorage.getItem('role') || '';
  
  // 载入人口数据
  loadPersonsData();
  
  // 初始化 Mock 模块数据
  initMockData();
});

const switchModule = (moduleName) => {
  currentModule.value = moduleName;
};

// 退出登录
const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录并返回登录页面吗？', '系统安全提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      localStorage.removeItem('role');
      router.push('/login');
      ElMessage.success('已安全退出系统');
    })
    .catch(() => {});
};


// ==========================================
// 1. 人口信息管理 (人口管理核心逻辑)
// ==========================================
const personsList = ref([]);
const personLoading = ref(false);
const personQuery = reactive({
  name: '',
  idCard: '',
  status: '正常' // 默认展示正常状态
});
const personPage = reactive({
  currentPage: 1,
  pageSize: 10,
  totalElements: 0
});

// 载入真实后端人口列表
const loadPersonsData = async () => {
  personLoading.value = true;
  try {
    const res = await getPersonList({
      name: personQuery.name,
      idCard: personQuery.idCard,
      status: personQuery.status,
      page: personPage.currentPage - 1, // 后端从 0 开始
      size: personPage.pageSize
    });
    if (res.success && res.data) {
      personsList.value = res.data.content || [];
      personPage.totalElements = res.data.totalElements || 0;
    }
  } catch (err) {
    console.error('加载人口列表失败:', err);
    ElMessage.error('无法获取云端人口数据，请检查网络或后端是否开启');
  } finally {
    personLoading.value = false;
  }
  
  // 额外拉取一次无分页的数据，供开户、证件模块作为下拉选项
  try {
    const resAll = await getPersonList({ page: 0, size: 1000, status: '正常' });
    if (resAll.success && resAll.data) {
      allPersonsRaw.value = resAll.data.content || [];
    }
  } catch (err) {
    console.error('拉取下拉框备选列表失败:', err);
  }
};

const handlePersonSearch = () => {
  personPage.currentPage = 1;
  loadPersonsData();
};

const resetPersonQuery = () => {
  personQuery.name = '';
  personQuery.idCard = '';
  personQuery.status = '正常';
  handlePersonSearch();
};

const handlePersonSizeChange = (val) => {
  personPage.pageSize = val;
  loadPersonsData();
};

const handlePersonPageChange = (val) => {
  personPage.currentPage = val;
  loadPersonsData();
};

// 登记与修改人口弹窗
const personFormRef = ref(null);
const personDialog = reactive({
  visible: false,
  isEdit: false,
  submitting: false
});
const personForm = reactive({
  personId: null,
  name: '',
  gender: '男',
  idCard: '',
  birthDate: '',
  ethnicity: '汉族',
  phone: '',
  currentAddress: '',
  status: '正常'
});
const personRules = {
  name: [
    { required: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过50个字符', trigger: 'blur' }
  ],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  idCard: [
    { required: true, message: '身份证号不能为空', trigger: 'blur' },
    { pattern: /^[0-9Xx]{18}$/, message: '请输入18位规范身份证号', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号码格式不规范', trigger: 'blur' }
  ],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }]
};

const openPersonDialog = (row = null) => {
  personDialog.isEdit = !!row;
  personDialog.visible = true;
  personDialog.submitting = false;
  nextTick(() => {
    if (row) {
      personForm.personId = row.personId;
      personForm.name = row.name;
      personForm.gender = row.gender;
      personForm.idCard = row.idCard;
      personForm.birthDate = row.birthDate;
      personForm.ethnicity = row.ethnicity || '汉族';
      personForm.phone = row.phone || '';
      personForm.currentAddress = row.currentAddress || '';
      personForm.status = row.status;
    } else {
      personForm.personId = null;
      personForm.name = '';
      personForm.gender = '男';
      personForm.idCard = '';
      personForm.birthDate = '';
      personForm.ethnicity = '汉族';
      personForm.phone = '';
      personForm.currentAddress = '';
      personForm.status = '正常';
    }
  });
};

const submitPersonForm = () => {
  if (!personFormRef.value) return;
  personFormRef.value.validate(async (valid) => {
    if (!valid) return;
    personDialog.submitting = true;
    try {
      const payload = {
        name: personForm.name,
        gender: personForm.gender,
        idCard: personForm.idCard,
        birthDate: personForm.birthDate,
        ethnicity: personForm.ethnicity,
        phone: personForm.phone,
        currentAddress: personForm.currentAddress,
        status: personForm.status
      };
      
      let res;
      if (personDialog.isEdit) {
        res = await updatePerson(personForm.personId, payload);
      } else {
        res = await createPerson(payload);
      }

      if (res.success) {
        ElMessage.success(personDialog.isEdit ? '人口信息修改成功' : '常驻人口登记成功');
        personDialog.visible = false;
        loadPersonsData();
      }
    } catch (err) {
      console.error(err);
      ElMessage.error(err.response?.data?.message || '操作失败，请核对信息是否重复（例如身份证号）');
    } finally {
      personDialog.submitting = false;
    }
  });
};

const handlePersonDelete = (row) => {
  ElMessageBox.confirm(`确定要注销并注销人口 [ ${row.name} ] 吗？`, '安全注销提示', {
    confirmButtonText: '确定注销',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deletePerson(row.personId);
      ElMessage.success('人口档案注销注销成功');
      loadPersonsData();
    } catch (err) {
      console.error(err);
      ElMessage.error('操作失败');
    }
  }).catch(() => {});
};


// ==========================================
// 2. 家庭户籍系统 (纯前端闭环核心逻辑)
// ==========================================
const households = ref([]);
const householdQuery = reactive({ householdNo: '', address: '' });

const filteredHouseholds = computed(() => {
  return households.value.filter(h => {
    const matchNo = h.householdNo.toLowerCase().includes(householdQuery.householdNo.toLowerCase());
    const matchAddr = h.address.includes(householdQuery.address);
    return matchNo && matchAddr;
  });
});

const handleHouseholdSearch = () => {
  // 触发响应式计算
};
const resetHouseholdQuery = () => {
  householdQuery.householdNo = '';
  householdQuery.address = '';
};

// 户籍弹窗
const householdFormRef = ref(null);
const householdDialog = reactive({ visible: false, isEdit: false });
const householdForm = reactive({
  id: null,
  householdNo: '',
  headPersonId: '',
  address: '',
  establishDate: ''
});
const householdRules = {
  headPersonId: [{ required: true, message: '请选择户主', trigger: 'change' }],
  address: [{ required: true, message: '户籍地址不能为空', trigger: 'blur' }],
  establishDate: [{ required: true, message: '请选择立户日期', trigger: 'change' }]
};

const openHouseholdDialog = (row = null) => {
  householdDialog.isEdit = !!row;
  householdDialog.visible = true;
  nextTick(() => {
    if (row) {
      householdForm.id = row.id;
      householdForm.householdNo = row.householdNo;
      householdForm.headPersonId = row.headPersonId;
      householdForm.address = row.address;
      householdForm.establishDate = row.establishDate;
    } else {
      householdForm.id = null;
      householdForm.householdNo = '';
      householdForm.headPersonId = '';
      householdForm.address = '';
      householdForm.establishDate = new Date().toISOString().substring(0, 10);
    }
  });
};

const submitHouseholdForm = () => {
  if (!householdFormRef.value) return;
  householdFormRef.value.validate((valid) => {
    if (!valid) return;
    const selectedHead = allPersonsRaw.value.find(p => p.personId === householdForm.headPersonId);
    const headName = selectedHead ? selectedHead.name : '未知';

    if (householdDialog.isEdit) {
      const idx = households.value.findIndex(h => h.id === householdForm.id);
      if (idx !== -1) {
        households.value[idx].headPersonId = householdForm.headPersonId;
        households.value[idx].headName = headName;
        households.value[idx].address = householdForm.address;
        households.value[idx].establishDate = householdForm.establishDate;
      }
      ElMessage.success('户籍地址更新成功');
    } else {
      // 自动生成新户籍编号 H+随机四位
      const newNo = 'H' + Math.floor(1000 + Math.random() * 9000);
      const newId = households.value.length + 1;
      households.value.unshift({
        id: newId,
        householdNo: newNo,
        headPersonId: householdForm.headPersonId,
        headName: headName,
        address: householdForm.address,
        establishDate: householdForm.establishDate,
        memberCount: Math.floor(1 + Math.random() * 4), // 随机初始成员数
        status: '正常'
      });
      ElMessage.success('成功建立家庭户口，户号为：' + newNo);
    }
    householdDialog.visible = false;
    saveMockData();
  });
};

const handleHouseholdDelete = (row) => {
  ElMessageBox.confirm(`警告：确定要撤销并销户 [ 户号: ${row.householdNo} ] 吗？`, '注销警告', {
    confirmButtonText: '确定撤销',
    cancelButtonText: '取消',
    type: 'error',
  }).then(() => {
    const idx = households.value.findIndex(h => h.id === row.id);
    if (idx !== -1) {
      households.value[idx].status = '撤销';
    }
    ElMessage.success('户籍已成功撤销注销');
    saveMockData();
  }).catch(() => {});
};

// 查看家庭成员列表
const membersDialog = reactive({ visible: false, householdNo: '', address: '', list: [] });
const viewHouseholdMembers = (row) => {
  membersDialog.householdNo = row.householdNo;
  membersDialog.address = row.address;
  
  // 基于模拟：把户主塞进去作为第一成员，随机从人口列表筛选几个人作为亲属
  const list = [
    { name: row.headName, relationship: '户主', idCard: allPersonsRaw.value.find(p => p.personId === row.headPersonId)?.idCard || '310101199001010011', phone: '13812345678' }
  ];
  
  // 关联额外的 1-3 名关系亲属
  const relatives = ['妻子', '长子', '长女', '父亲', '母亲'];
  const others = allPersonsRaw.value.filter(p => p.personId !== row.headPersonId).slice(0, row.memberCount - 1);
  others.forEach((p, idx) => {
    list.push({
      name: p.name,
      relationship: relatives[idx % relatives.length],
      idCard: p.idCard,
      phone: p.phone || '暂无登记'
    });
  });
  
  membersDialog.list = list;
  membersDialog.visible = true;
};


// ==========================================
// 3. 迁入迁出管理 (纯前端闭环核心逻辑)
// ==========================================
const migrations = ref([]);
const migrationQuery = reactive({ name: '', type: '' });

const filteredMigrations = computed(() => {
  return migrations.value.filter(m => {
    const matchName = m.personName.toLowerCase().includes(migrationQuery.name.toLowerCase());
    const matchType = migrationQuery.type ? m.type === migrationQuery.type : true;
    return matchName && matchType;
  });
});

const handleMigrationSearch = () => {};
const resetMigrationQuery = () => {
  migrationQuery.name = '';
  migrationQuery.type = '';
};

// 迁移弹窗
const migrationFormRef = ref(null);
const migrationDialog = reactive({ visible: false, mode: 'in' });
const migrationForm = reactive({
  personId: '',
  migrationDate: '',
  address: '',
  reason: ''
});
const migrationRules = {
  personId: [{ required: true, message: '请选择办理业务人员', trigger: 'change' }],
  migrationDate: [{ required: true, message: '请选择办理日期', trigger: 'change' }],
  address: [{ required: true, message: '地址不能为空', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写原因', trigger: 'blur' }]
};

const openMigrationDialog = (mode) => {
  migrationDialog.mode = mode;
  migrationDialog.visible = true;
  nextTick(() => {
    migrationForm.personId = '';
    migrationForm.migrationDate = new Date().toISOString().substring(0, 10);
    migrationForm.address = '';
    migrationForm.reason = '';
  });
};

const submitMigrationForm = () => {
  if (!migrationFormRef.value) return;
  migrationFormRef.value.validate(async (valid) => {
    if (!valid) return;
    const selectedPerson = allPersonsRaw.value.find(p => p.personId === migrationForm.personId);
    if (!selectedPerson) {
      ElMessage.error('选择的人员无效');
      return;
    }
    
    // 向列表增加一条历史记录
    migrations.value.unshift({
      id: migrations.value.length + 1,
      personName: selectedPerson.name,
      idCard: selectedPerson.idCard,
      type: migrationDialog.mode === 'in' ? '迁入' : '迁出',
      migrationDate: migrationForm.migrationDate,
      address: migrationForm.address,
      reason: migrationForm.reason
    });
    
    // 联动操作：迁入设为“正常”，迁出自动将人口状态更新为“已注销”，调用后端更新接口！
    try {
      const payload = {
        name: selectedPerson.name,
        gender: selectedPerson.gender,
        idCard: selectedPerson.idCard,
        birthDate: selectedPerson.birthDate,
        ethnicity: selectedPerson.ethnicity,
        phone: selectedPerson.phone,
        currentAddress: migrationDialog.mode === 'in' ? migrationForm.address : selectedPerson.currentAddress,
        status: migrationDialog.mode === 'in' ? '正常' : '已注销'
      };
      await updatePerson(selectedPerson.personId, payload);
      ElMessage.success(`成功办理业务！已将人口 [ ${selectedPerson.name} ] 状态联动修改为 [ ${payload.status} ]`);
      loadPersonsData();
    } catch (err) {
      console.error(err);
      ElMessage.warning('记录保存成功，但联动更新人口状态失败。');
    }

    migrationDialog.visible = false;
    saveMockData();
  });
};


// ==========================================
// 4. 证件管理 (纯前端闭环核心逻辑)
// ==========================================
const certificates = ref([]);
const certQuery = reactive({ name: '', certNo: '', status: '' });

const filteredCertificates = computed(() => {
  return certificates.value.filter(c => {
    const matchName = c.personName.toLowerCase().includes(certQuery.name.toLowerCase());
    const matchNo = c.certNo.toLowerCase().includes(certQuery.certNo.toLowerCase());
    const matchStatus = certQuery.status ? c.status === certQuery.status : true;
    return matchNo && matchName && matchStatus;
  });
});

const handleCertSearch = () => {};
const resetCertQuery = () => {
  certQuery.name = '';
  certQuery.certNo = '';
  certQuery.status = '';
};

// 证件弹窗
const certificateFormRef = ref(null);
const certificateDialog = reactive({ visible: false, isEdit: false });
const certificateForm = reactive({
  id: null,
  personId: '',
  personName: '',
  certType: '居民身份证',
  certNo: '',
  issueDate: '',
  expireDate: '',
  status: '有效'
});
const certificateRules = {
  personId: [{ required: true, message: '请选择持有人', trigger: 'change' }],
  certType: [{ required: true, message: '请选择证件类型', trigger: 'change' }],
  certNo: [
    { required: true, message: '证件编号不能为空', trigger: 'blur' },
    { min: 6, max: 30, message: '证件号长度应在 6 - 30 位之间', trigger: 'blur' }
  ],
  issueDate: [{ required: true, message: '请选择签发日期', trigger: 'change' }],
  expireDate: [{ required: true, message: '请选择过期截止日期', trigger: 'change' }]
};

const openCertificateDialog = (row = null) => {
  certificateDialog.isEdit = !!row;
  certificateDialog.visible = true;
  nextTick(() => {
    if (row) {
      certificateForm.id = row.id;
      certificateForm.personId = row.personId;
      certificateForm.personName = row.personName;
      certificateForm.certType = row.certType;
      certificateForm.certNo = row.certNo;
      certificateForm.issueDate = row.issueDate;
      certificateForm.expireDate = row.expireDate;
      certificateForm.status = row.status;
    } else {
      certificateForm.id = null;
      certificateForm.personId = '';
      certificateForm.personName = '';
      certificateForm.certType = '居民身份证';
      certificateForm.certNo = '';
      certificateForm.issueDate = new Date().toISOString().substring(0, 10);
      certificateForm.expireDate = '';
      certificateForm.status = '有效';
    }
  });
};

const submitCertificateForm = () => {
  if (!certificateFormRef.value) return;
  certificateFormRef.value.validate((valid) => {
    if (!valid) return;
    
    const selectedPerson = allPersonsRaw.value.find(p => p.personId === certificateForm.personId);
    const pName = selectedPerson ? selectedPerson.name : certificateForm.personName;

    if (certificateDialog.isEdit) {
      const idx = certificates.value.findIndex(c => c.id === certificateForm.id);
      if (idx !== -1) {
        certificates.value[idx].certType = certificateForm.certType;
        certificates.value[idx].certNo = certificateForm.certNo;
        certificates.value[idx].issueDate = certificateForm.issueDate;
        certificates.value[idx].expireDate = certificateForm.expireDate;
        certificates.value[idx].status = certificateForm.status;
      }
      ElMessage.success('证件信息编辑成功');
    } else {
      certificates.value.unshift({
        id: certificates.value.length + 1,
        personId: certificateForm.personId,
        personName: pName,
        certType: certificateForm.certType,
        certNo: certificateForm.certNo,
        issueDate: certificateForm.issueDate,
        expireDate: certificateForm.expireDate,
        status: certificateForm.status
      });
      ElMessage.success(`证件登记成功，持有人：${pName}`);
    }
    
    certificateDialog.visible = false;
    saveMockData();
  });
};

const handleCertDelete = (row) => {
  ElMessageBox.confirm(`确定要废弃/作废此证件 [ ${row.certNo} ] 吗？`, '作废警告', {
    confirmButtonText: '作废',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    const idx = certificates.value.findIndex(c => c.id === row.id);
    if (idx !== -1) {
      certificates.value[idx].status = '已作废';
    }
    ElMessage.success('证件作废成功');
    saveMockData();
  }).catch(() => {});
};

const getCertStatusType = (status) => {
  if (status === '有效') return 'success';
  if (status === '即将到期') return 'warning';
  if (status === '已过期' || status === '已作废') return 'danger';
  return 'info';
};

// 切换模块逻辑
const nextTick = (fn) => {
  setTimeout(fn, 0);
};

const initMockData = () => {
  // 从 LocalStorage 中读取 Mock 列表数据以维持刷新后的持久化
  const savedHouseholds = localStorage.getItem('mock_households');
  const savedMigrations = localStorage.getItem('mock_migrations');
  const savedCertificates = localStorage.getItem('mock_certificates');

  if (savedHouseholds) households.value = JSON.parse(savedHouseholds);
  else {
    households.value = [
      { id: 1, householdNo: 'H3209', headPersonId: 1, headName: '张三', address: '北京市东城区示例地址1号楼', establishDate: '2020-03-12', memberCount: 3, status: '正常' },
      { id: 2, householdNo: 'H4012', headPersonId: 2, headName: '李四', address: '北京市东城区朝阳门街道4号院', establishDate: '2018-09-05', memberCount: 2, status: '正常' }
    ];
  }

  if (savedMigrations) migrations.value = JSON.parse(savedMigrations);
  else {
    migrations.value = [
      { id: 1, personName: '张小明', idCard: '110101199901010011', type: '迁入', migrationDate: '2026-07-01', address: '河北省石家庄市新华区', reason: '工作调动' },
      { id: 2, personName: '王大柱', idCard: '130102199002021234', type: '迁出', migrationDate: '2026-06-15', address: '上海市浦东新区浦东大道', reason: '房产买卖迁出' }
    ];
  }

  if (savedCertificates) certificates.value = JSON.parse(savedCertificates);
  else {
    certificates.value = [
      { id: 1, personId: 1, personName: '张三', certType: '居民身份证', certNo: '110101199901010011', issueDate: '2020-01-01', expireDate: '2040-01-01', status: '有效' },
      { id: 2, personId: 2, personName: '李四', certType: '居住证', certNo: 'G310220199512010123', issueDate: '2025-06-01', expireDate: '2026-06-01', status: '即将到期' }
    ];
  }
};

const saveMockData = () => {
  localStorage.setItem('mock_households', JSON.stringify(households.value));
  localStorage.setItem('mock_migrations', JSON.stringify(migrations.value));
  localStorage.setItem('mock_certificates', JSON.stringify(certificates.value));
};
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--color-surface-muted);
  overflow: hidden;
}

/* 顶部导航栏 */
.navbar {
  height: 56px;
  background-color: var(--color-accent);
  color: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  box-shadow: var(--shadow-subtle);
  z-index: 10;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 22px;
  color: #ffffff;
}

.system-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1.5px;
}

.system-badge {
  background: rgba(255, 255, 255, 0.15);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
  margin-left: 8px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 14px;
}

.role-badge {
  background: rgba(255, 255, 255, 0.25);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  margin-left: 6px;
}

.logout-btn {
  color: rgba(255, 255, 255, 0.8) !important;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.logout-btn:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

/* 布局骨架 */
.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 220px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.side-nav {
  display: flex;
  flex-direction: column;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  color: var(--color-ink);
  font-size: 14px;
  border-left: 3px solid transparent;
  transition: all var(--transition-base);
}
.nav-item:hover {
  background: var(--color-surface-muted);
}
.nav-item.active {
  background: var(--color-accent-light);
  color: var(--color-accent);
  border-left-color: var(--color-accent);
  font-weight: 600;
}
.nav-item .el-icon {
  font-size: 18px;
}
.sidebar-footer {
  padding: 0 24px;
  font-size: 11px;
  color: var(--color-ink-muted);
  line-height: 1.6;
}

/* 主体内容 */
.main-content {
  flex: 1;
  padding: 28px 36px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* 页面标题 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.page-header h1 {
  font-size: 22px;
  margin-bottom: 6px;
  font-weight: 700;
}
.page-header .subtitle {
  color: var(--color-ink-muted);
  font-size: 13px;
}

/* 搜索过滤栏 */
.filter-bar {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-large);
  padding: 18px 24px 2px 24px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-subtle);
}

/* 数据表格卡片 */
.data-table-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-large);
  padding: 24px;
  box-shadow: var(--shadow-subtle);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 350px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 底部快捷键 */
.quick-nav-section {
  margin-top: 24px;
  border-top: 1px solid var(--color-border);
  padding-top: 20px;
}
.quick-nav-section h3 {
  font-size: 15px;
  margin-bottom: 12px;
}
.quick-nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}
.nav-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 14px 20px;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all var(--transition-base);
}
.nav-card:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-light);
  transform: translateY(-1px);
}
.nav-card .el-icon {
  font-size: 18px;
}
.nav-card span {
  font-size: 13px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .main-layout {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
    padding: 10px 0;
  }
  .side-nav {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: space-around;
  }
  .nav-item {
    padding: 8px 12px;
    border-left: none;
    border-bottom: 3px solid transparent;
  }
  .nav-item.active {
    border-bottom-color: var(--color-accent);
  }
  .sidebar-footer {
    display: none;
  }
  .page-header {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
