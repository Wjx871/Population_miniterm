import request from './request'
export const createPersonCancellation=(data)=>request({url:'/cancellations/person/applications',method:'post',data})
export const createHouseholdCancellation=(data)=>request({url:'/cancellations/household/applications',method:'post',data})
export const getCancellationDetail=(id)=>request({url:`/cancellations/applications/${id}`,method:'get'})
export const executePersonCancellation=(id,version)=>request({url:`/cancellations/person/applications/${id}/execute`,method:'post',data:{version}})
export const executeHouseholdCancellation=(id,version)=>request({url:`/cancellations/household/applications/${id}/execute`,method:'post',data:{version}})
export const listCancellations=(params)=>request({url:'/cancellations',method:'get',params})
export const listHouseholdArchives=(params)=>request({url:'/household-archives',method:'get',params})
export const getHouseholdArchive=(id)=>request({url:`/household-archives/${id}`,method:'get'})
