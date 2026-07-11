import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import { useUserStore } from '../stores/user'

const routes=[{path:'/login',name:'Login',component:Login,meta:{title:'登录 - 人口数据库管理系统'}},{path:'/',component:()=>import('../layouts/MainLayout.vue'),redirect:'/home',meta:{requiresAuth:true},children:[
 {path:'home',name:'Dashboard',component:()=>import('../views/dashboard/Dashboard.vue'),meta:{title:'工作台'}},
 {path:'persons',name:'Persons',component:()=>import('../views/persons/PersonList.vue'),meta:{title:'人员管理'}},
 {path:'households',name:'Households',component:()=>import('../views/households/HouseholdList.vue'),meta:{title:'户籍管理'}},
 {path:'households/:id',name:'HouseholdDetail',component:()=>import('../views/households/HouseholdDetail.vue'),meta:{title:'户籍详情'}},
 {path:'migrations/in',name:'MigrationIn',component:()=>import('../views/migrations/MigrationList.vue'),meta:{title:'迁入申请',type:'in'}},
 {path:'migrations/out',name:'MigrationOut',component:()=>import('../views/migrations/MigrationList.vue'),meta:{title:'迁出申请',type:'out'}},
 {path:'migrations/applications/:id',name:'MigrationDetail',component:()=>import('../views/migrations/MigrationDetail.vue'),meta:{title:'迁移申请详情'}},
 {path:'residence-archives',name:'ResidenceArchives',component:()=>import('../views/migrations/ResidenceArchiveList.vue'),meta:{title:'历史户籍归档'}},
 {path:'cancellations/person',name:'PersonCancellations',component:()=>import('../views/cancellations/CancellationList.vue'),meta:{title:'人员注销申请',type:'person'}},
 {path:'cancellations/household',name:'HouseholdCancellations',component:()=>import('../views/cancellations/CancellationList.vue'),meta:{title:'家庭户销户申请',type:'household'}},
 {path:'cancellations/applications/:id',name:'CancellationDetail',component:()=>import('../views/cancellations/CancellationDetail.vue'),meta:{title:'注销申请详情'}},
 {path:'cancellations/records',name:'CancellationRecords',component:()=>import('../views/cancellations/CancellationRecords.vue'),meta:{title:'注销记录'}},
 {path:'household-archives',name:'HouseholdArchives',component:()=>import('../views/cancellations/HouseholdArchives.vue'),meta:{title:'家庭户归档'}},
 {path:'floating-registrations/applications',redirect:'/applications'},
 {path:'floating-registrations/applications/create',component:()=>import('../views/floating/FloatingApplicationCreate.vue'),meta:{title:'新建流动登记申请'}},
 {path:'floating-registrations/applications/:id',component:()=>import('../views/floating/FloatingApplicationDetail.vue'),meta:{title:'流动登记申请详情'}},
 {path:'floating-populations',component:()=>import('../views/floating/FloatingPopulationList.vue'),meta:{title:'流动人口登记'}},
 {path:'floating-populations/:id',component:()=>import('../views/floating/FloatingPopulationDetail.vue'),meta:{title:'居住登记信息'}},
 {path:'residence-permits/applications',redirect:'/applications'},
 {path:'residence-permits/applications/first-issue',component:()=>import('../views/floating/PermitApplicationCreate.vue'),meta:{title:'居住证首次申领',mode:'first'}},
 {path:'residence-permits/applications/:id',component:()=>import('../views/floating/PermitApplicationDetail.vue'),meta:{title:'居住证申请详情'}},
 {path:'residence-permits',component:()=>import('../views/floating/ResidencePermitList.vue'),meta:{title:'居住证管理'}},
 {path:'residence-permits/expiring',component:()=>import('../views/floating/ResidencePermitList.vue'),meta:{title:'居住证即将到期',expiring:true}},
 {path:'residence-permits/:permitId/endorsement',component:()=>import('../views/floating/PermitApplicationCreate.vue'),meta:{title:'居住证签注申请',mode:'endorse'}},
 {path:'residence-permits/:permitId/cancellation',component:()=>import('../views/floating/PermitApplicationCreate.vue'),meta:{title:'居住证注销申请',mode:'cancel'}},
 {path:'residence-permits/:id',component:()=>import('../views/floating/ResidencePermitDetail.vue'),meta:{title:'居住证详情'}},
 {path:'key-population',name:'KeyPopulation',component:{template:'<div>重点人口管理正在建设中...</div>'},meta:{title:'重点人口'}},
 {path:'certificates',name:'Certificates',component:()=>import('../views/certificates/CertificateList.vue'),meta:{title:'证件管理'}},
 {path:'users',name:'Users',component:()=>import('../views/users/UserList.vue'),meta:{title:'用户管理'}},
 {path:'exports',component:()=>import('../views/exports/ExportList.vue'),meta:{title:'数据导出记录'}},
 {path:'exports/sensitive/create',component:()=>import('../views/exports/SensitiveExportCreate.vue'),meta:{title:'敏感数据导出申请'}},
 {path:'exports/applications/:id',component:()=>import('../views/exports/ExportApplicationDetail.vue'),meta:{title:'敏感导出申请详情'}},
 {path:'exports/:id',component:()=>import('../views/exports/ExportDetail.vue'),meta:{title:'导出记录详情'}},
 {path:'system/operation-logs',component:()=>import('../views/system/OperationLogs.vue'),meta:{title:'操作审计日志'}},
 {path:'dictionary',name:'Dictionary',component:{template:'<div>数据字典正在建设中...</div>'},meta:{title:'数据字典'}},
 {path:'applications',name:'Applications',component:()=>import('../views/applications/ApplicationList.vue'),meta:{title:'我的申请'}},
 {path:'applications/create',name:'ApplicationCreate',component:()=>import('../views/applications/ApplicationCreate.vue'),meta:{title:'新建申请'}},
 {path:'applications/:id',name:'ApplicationDetail',component:()=>import('../views/applications/ApplicationDetail.vue'),meta:{title:'申请详情'}},
 {path:'approvals/pending',name:'ApprovalsPending',component:()=>import('../views/approvals/ApprovalList.vue'),meta:{title:'待办审批',processed:false}},
 {path:'approvals/processed',name:'ApprovalsProcessed',component:()=>import('../views/approvals/ApprovalList.vue'),meta:{title:'已办审批',processed:true}},
 {path:'approvals/:id',name:'ApprovalDetail',component:()=>import('../views/approvals/ApprovalDetail.vue'),meta:{title:'审批详情'}}
]},{path:'/:pathMatch(.*)*',redirect:'/home'}]
const router=createRouter({history:createWebHistory(),routes})
router.beforeEach((to,from,next)=>{if(to.meta?.title)document.title=to.meta.title;const user=useUserStore();const auth=to.matched.some(r=>r.meta.requiresAuth);if(auth&&!user.isLoggedIn)next('/login');else if(to.path==='/login'&&user.isLoggedIn)next('/home');else next()})
export default router
