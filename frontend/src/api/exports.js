import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'
export const createNormalExport=(data)=>request({url:'/exports/normal',method:'post',data})
export const createSensitiveExport=(data)=>request({url:'/exports/sensitive/applications',method:'post',data})
export const getExportApplication=(id)=>request({url:`/exports/applications/${id}`,method:'get'})
export const executeSensitiveExport=(id,version)=>request({url:`/exports/sensitive/applications/${id}/execute`,method:'post',data:{version}})
export const getExportPage=(params={})=>request({url:'/exports',method:'get',params:toSpringPageParams(params)})
export const downloadExport=(id)=>request({url:`/exports/${id}/download`,method:'get',responseType:'blob'})
