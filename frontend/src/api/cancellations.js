import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'
export const getCancellationPage=(params={})=>request({url:'/cancellations',method:'get',params:toSpringPageParams(params)})
export const createPersonCancellation=(data)=>request({url:'/cancellations/person/applications',method:'post',data})
export const createHouseholdCancellation=(data)=>request({url:'/cancellations/household/applications',method:'post',data})
export const getCancellationApplication=(id)=>request({url:`/cancellations/applications/${id}`,method:'get'})
export const executePersonCancellation=(id,version)=>request({url:`/cancellations/person/applications/${id}/execute`,method:'post',data:{version}})
export const executeHouseholdCancellation=(id,version)=>request({url:`/cancellations/household/applications/${id}/execute`,method:'post',data:{version}})
