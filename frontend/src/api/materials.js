import request from './request'
import { normalizeBlobError } from '../utils/download'

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

export function previewMigrationMaterial(applicationId, file, page, signal) {
  const form = new FormData()
  form.append('file', file, file.name)
  if (page) form.append('pageType', page)
  return request({
    url: `/applications/${applicationId}/materials/${page ? 'household-ocr' : 'migration-form-ocr'}`,
    method: 'post',
    data: form,
    timeout: 45000,
    signal,
    silent: true,
  })
}

export function checkMigrationMaterials(applicationId, selection, signal) {
  return request({
    url: `/applications/${applicationId}/materials/consistency-check`,
    method: 'post',
    data: selection,
    timeout: 85000,
    signal,
    silent: true,
  })
}

export function verifyMaterial(materialId, payload) {
  return request({ url: `/materials/${materialId}/verify`, method: 'post', data: payload })
}

export async function downloadMaterial(materialId) {
  try {
    return await request({ url: `/materials/${materialId}/download`, method: 'get', responseType: 'blob', rawResponse: true })
  } catch (error) {
    throw await normalizeBlobError(error)
  }
}
