<template>
  <el-card v-if="migration" shadow="never">
    <template #header>迁移专业信息</template>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="业务类型">{{ isIn ? '迁入' : '迁出' }}</el-descriptions-item>
      <el-descriptions-item label="迁移类型">{{ migration.migrationType }}</el-descriptions-item>
      <el-descriptions-item label="迁移人员">
        <span>{{ person?.name || `人员 #${migration.personId}` }}</span>
        <SensitiveText v-if="person?.idCard" :value="person.idCard" kind="idCard" />
      </el-descriptions-item>
      <el-descriptions-item label="业务状态"><StatusTag :value="migration.businessStatus" kind="migration" /></el-descriptions-item>
      <template v-if="isIn">
        <el-descriptions-item label="来源区域">{{ migration.fromRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源地址">{{ migration.fromAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标区域">{{ migration.toRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标家庭户">{{ migration.toHouseholdId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迁入日期">{{ migration.inDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迁移批次号">{{ migration.transferBatchNo || '-' }}</el-descriptions-item>
      </template>
      <template v-else>
        <el-descriptions-item label="原户籍区域">{{ migration.fromRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原户籍地址">{{ migration.fromAddressSnapshot || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迁往区域">{{ migration.toRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迁往地址">{{ migration.toAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迁出日期">{{ migration.outDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="新户主">{{ migration.newHeadPersonId || '-' }}</el-descriptions-item>
      </template>
      <el-descriptions-item label="当前户籍" :span="2">{{ detail.currentResidence?.registeredAddress || '-' }}（{{ detail.currentResidence?.regionCode || '-' }}）</el-descriptions-item>
      <el-descriptions-item label="家庭户信息" :span="2">户号：{{ detail.household?.householdNo || '-' }}；户主：{{ memberLabel(detail.household?.headPersonId) }}；有效成员数：{{ detail.household?.activeMemberCount ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="有效成员" :span="2">{{ memberSummary }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StatusTag from '../../../components/common/StatusTag.vue'
import SensitiveText from '../../../components/common/SensitiveText.vue'
import { getPersonById } from '../../../api/persons'
import { normalizePerson } from '../../../adapters/person'
import { maskIdCard } from '../../../utils/mask'

const props = defineProps({ detail: { type: Object, default: null }, person: { type: Object, default: null } })
const isIn = computed(() => Boolean(props.detail?.migrationIn))
const migration = computed(() => props.detail?.migrationIn || props.detail?.migrationOut || null)
const members = ref(new Map())
const memberSummary = computed(() => {
  const ids = props.detail?.activeMemberPersonIds || []
  return ids.length ? ids.map((id) => memberLabel(id)).join('、') : '-'
})

function memberLabel(personId) {
  if (!personId) return '-'
  const person = members.value.get(String(personId))
  return person ? `${person.name || `成员 #${personId}`}（${maskIdCard(person.idCard)}）` : `成员 #${personId}`
}

watch(
  () => props.detail?.activeMemberPersonIds || [],
  async (ids) => {
    const records = await Promise.all(ids.map(async (personId) => {
      try {
        const person = normalizePerson(await getPersonById(personId))
        return person.id ? [String(person.id), person] : null
      } catch { return null }
    }))
    members.value = new Map(records.filter(Boolean))
  },
  { immediate: true, deep: true }
)
</script>
