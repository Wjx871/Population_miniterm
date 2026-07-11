import request from './request'

export function getMaterials(applicationId) {
  return request({ url: `/applications/${applicationId}/materials`, method: 'get' })
}

export function uploadMaterial(applicationId, payload) {
  const form = new FormData()
  form.append('materialType', payload.materialType)
  form.append('materialName', payload.materialName)
  form.append('requiredFlag', String(Boolean(payload.requiredFlag)))
  form.append('file', payload.file)
  return request({ url: `/applications/${applicationId}/materials`, method: 'post', data: form, timeout: 60000 })
}

export function deleteMaterial(materialId) {
  return request({ url: `/materials/${materialId}`, method: 'delete' })
}

export function verifyMaterial(materialId, payload) {
  return request({ url: `/materials/${materialId}/verify`, method: 'post', data: payload })
}

export function downloadMaterial(materialId) {
  return request({ url: `/materials/${materialId}/download`, method: 'get', responseType: 'blob', rawResponse: true })
}
