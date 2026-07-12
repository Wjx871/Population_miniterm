<template>
  <el-table :data="materials" border stripe empty-text="暂无材料">
    <el-table-column prop="materialName" label="材料名称" min-width="160" show-overflow-tooltip />
    <el-table-column prop="materialType" label="材料类型" width="150" />
    <el-table-column prop="requiredFlag" label="必需" width="80" align="center">
      <template #default="{ row }">{{ row.requiredFlag ? '是' : '否' }}</template>
    </el-table-column>
    <el-table-column prop="verifyStatus" label="核验状态" width="120" align="center"><template #default="{ row }"><StatusTag :value="row.verifyStatus" kind="material" /></template></el-table-column>
    <el-table-column prop="verifyComment" label="核验意见" min-width="150" show-overflow-tooltip />
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="download(row)">下载</el-button>
        <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
        <el-button v-if="canVerify" link type="success" @click="verify(row, 'VERIFIED')">通过</el-button>
        <el-button v-if="canVerify" link type="danger" @click="verify(row, 'REJECTED')">驳回</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../common/StatusTag.vue'
import { deleteMaterial, downloadMaterial, verifyMaterial } from '../../api/materials'
import { downloadBlob } from '../../utils/download'
import { getApiErrorMessage } from '../../utils/apiError'

const props = defineProps({
  materials: { type: Array, default: () => [] },
  canDelete: { type: Boolean, default: false },
  canVerify: { type: Boolean, default: false },
})
const emit = defineEmits(['changed'])

async function download(row) {
  try {
    const response = await downloadMaterial(row.materialId)
    await downloadBlob(response, row.originalFilename || row.materialName || 'material')
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '材料下载失败'))
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除材料“${row.materialName}”吗？`, '删除材料', { type: 'warning' })
  await deleteMaterial(row.materialId)
  ElMessage.success('材料已删除')
  emit('changed')
}

async function verify(row, result) {
  const { value } = await ElMessageBox.prompt(result === 'VERIFIED' ? '可填写核验意见' : '请填写驳回原因', result === 'VERIFIED' ? '核验通过' : '核验驳回', {
    inputPattern: result === 'REJECTED' ? /\S+/ : undefined,
    inputErrorMessage: '驳回原因不能为空',
    confirmButtonText: '确认',
  })
  await verifyMaterial(row.materialId, { result, comment: value || '' })
  ElMessage.success('材料核验结果已提交')
  emit('changed')
}
</script>
