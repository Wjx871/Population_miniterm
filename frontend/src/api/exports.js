import request from './request'
export const normalExport=data=>request({url:'/exports/normal',method:'post',data})
export const sensitiveApply=data=>request({url:'/exports/sensitive/applications',method:'post',data})
export const getExportApplication=id=>request.get(`/exports/applications/${id}`)
export const executeSensitive=(id,version)=>request.post(`/exports/sensitive/applications/${id}/execute`,{version})
export const listExports=params=>request.get('/exports',{params})
export const getExport=id=>request.get(`/exports/${id}`)
export const downloadExport=id=>request({url:`/exports/${id}/download`,method:'get',responseType:'blob',rawResponse:true})
