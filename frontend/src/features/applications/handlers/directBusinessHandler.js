const CANCELLATION = new Set(['PERSON_CANCELLATION','HOUSEHOLD_CANCELLATION'])
const KEY = new Set(['KEY_POPULATION_REGISTER','KEY_POPULATION_RELEASE'])

export function createDirectBusinessHandler(services) {
  return {
    family: 'direct',
    supports(type){return CANCELLATION.has(type)||KEY.has(type)||type==='SENSITIVE_DATA_EXPORT'},
    async loadDetail(id,type){
      if(CANCELLATION.has(type))return services.getCancellationApplication(id)
      if(type==='KEY_POPULATION_REGISTER')return services.getKeyRegisterApplication(id)
      if(type==='KEY_POPULATION_RELEASE')return services.getKeyReleaseApplication(id)
      return services.getExportApplication(id)
    },
    buildEditRoute(){return null},
    getEditPermission(){return null},
    getMaterialOptions({businessType,detail}){
      if(KEY.has(businessType))return [{value:'KEY_POPULATION_BASIS',label:'重点人口业务依据',required:true},{value:'SITUATION_DESCRIPTION',label:'情况说明',required:true}]
      if(businessType==='SENSITIVE_DATA_EXPORT')return [{value:'EXPORT_JUSTIFICATION',label:'敏感导出用途说明',required:true}]
      if(businessType==='HOUSEHOLD_CANCELLATION')return [{value:'CANCELLATION_APPLICATION',label:'销户申请书',required:true},{value:'HOUSEHOLD_BOOK',label:'户口簿',required:true},{value:'HOUSEHOLD_CANCELLATION_PROOF',label:'家庭户销户原因证明',required:true},{value:'HOUSEHOLD_MERGE_PROOF',label:'家庭户合并证明',required:false}]
      const reason=detail?.cancellation?.cancelReasonCode
      const options=[{value:'APPLICANT_IDENTITY_PROOF',label:'申请人身份证明',required:true},{value:'HOUSEHOLD_BOOK',label:'户口簿',required:reason!=='DUPLICATE_REGISTRATION'}]
      const map={DEATH:['DEATH_CERTIFICATE','死亡证明'],DECLARED_DEAD:['DECLARED_DEAD_JUDGMENT','宣告死亡裁判文书'],SETTLED_ABROAD:['SETTLEMENT_ABROAD_PROOF','境外定居证明'],DUPLICATE_REGISTRATION:['DUPLICATE_REGISTRATION_PROOF','重复登记证明']}
      const item=map[reason]||['CANCELLATION_APPLICATION','注销申请书'];options.push({value:item[0],label:item[1],required:true});return options
    },
    getMaterialRuleText(){return '所有标记为必需的材料须由审批人员核验通过后方可提交或执行。'},
    getExecutionMeta({businessType,detail}){
      const permission=CANCELLATION.has(businessType)?'cancellation:execute':KEY.has(businessType)?'key-population:execute':'data:export:sensitive:execute'
      const version=CANCELLATION.has(businessType)?detail?.cancellation?.version:KEY.has(businessType)?detail?.application?.version:detail?.professional?.version
      return {mode:'direct-confirm',permission,type:'专业业务',version}
    },
    async execute({businessType,applicationId,detail}){
      const version=this.getExecutionMeta({businessType,detail}).version
      if(businessType==='PERSON_CANCELLATION')return services.executePersonCancellation(applicationId,version)
      if(businessType==='HOUSEHOLD_CANCELLATION')return services.executeHouseholdCancellation(applicationId,version)
      if(businessType==='KEY_POPULATION_REGISTER')return services.executeKeyRegister(applicationId,version)
      if(businessType==='KEY_POPULATION_RELEASE')return services.executeKeyRelease(applicationId,version)
      return services.executeSensitiveExport(applicationId,version)
    },
    isCompleted({application,detail}){
      const status=detail?.cancellation?.businessStatus||detail?.application?.businessStatus||detail?.professional?.businessStatus
      return application?.status==='COMPLETED'&&status==='COMPLETED'
    }
  }
}
