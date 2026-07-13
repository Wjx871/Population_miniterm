import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'
export const getKeyPopulationPage=(params={})=>request({url:'/key-populations',method:'get',params:toSpringPageParams(params)})
export const getKeyPopulation=(id)=>request({url:`/key-populations/${id}`,method:'get'})
export const getKeyPopulationHistory=(id)=>request({url:`/key-populations/${id}/history`,method:'get'})
export const createKeyRegisterApplication=(data)=>request({url:'/key-populations/register-applications',method:'post',data})
export const createKeyReleaseApplication=(id,data)=>request({url:`/key-populations/${id}/release-applications`,method:'post',data})
export const getKeyRegisterApplication=(id)=>request({url:`/key-populations/register-applications/${id}`,method:'get'})
export const getKeyReleaseApplication=(id)=>request({url:`/key-populations/release-applications/${id}`,method:'get'})
export const executeKeyRegister=(id,version)=>request({url:`/key-populations/register-applications/${id}/execute`,method:'post',data:{version}})
export const executeKeyRelease=(id,version)=>request({url:`/key-populations/release-applications/${id}/execute`,method:'post',data:{version}})
