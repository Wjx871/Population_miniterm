import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createSSRApp, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createPinia } from 'pinia'
import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createCancellationHandler } from '../src/features/applications/handlers/cancellationHandler.js'
import { createKeyPopulationHandler } from '../src/features/applications/handlers/keyPopulationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'
import { createExportHandler } from '../src/features/applications/handlers/exportHandler.js'

test('actual action bar renders real handler routes and submit permission matrix', async () => {
  const server = await createServer({ configFile: false, plugins: [vue()], server: { middlewareMode: true }, appType: 'custom' })
  try {
    const Component = (await server.ssrLoadModule('/src/components/business/ApplicationActionBar.vue')).default
    const cases = [ ['ORDINARY', null], ['MIGRATION_IN', createMigrationHandler({})], ['MIGRATION_OUT', createMigrationHandler({})],
      ['PERSON_CANCELLATION', createCancellationHandler({})], ['HOUSEHOLD_CANCELLATION', createCancellationHandler({})],
      ['KEY_POPULATION_REGISTER', createKeyPopulationHandler({})], ['KEY_POPULATION_RELEASE', createKeyPopulationHandler({})],
      ['FLOATING_REGISTRATION', createFloatingHandler({})], ['RESIDENCE_PERMIT_FIRST_ISSUE', createResidencePermitHandler({})],
      ['RESIDENCE_PERMIT_ENDORSEMENT', createResidencePermitHandler({})], ['RESIDENCE_PERMIT_CANCELLATION', createResidencePermitHandler({})],
      ['SENSITIVE_DATA_EXPORT', createExportHandler({})] ]
    for (const [businessType, handler] of cases) for (const status of ['DRAFT', 'RETURNED', 'UNDER_REVIEW', 'APPROVED', 'COMPLETED']) {
      for (const granted of [[], ['application:submit'], ['key-population:apply'], ['application:submit', 'key-population:apply'], ['application:submit', 'key-population:apply', 'application:edit', handler?.getEditPermission(businessType)]]) {
        const detail = { application: { businessType }, cancellation: {}, permit: {} }
        const route = handler?.buildEditRoute({ applicationId: 12, detail }) ?? null
        const permissions = handler?.getSubmitPermissions(businessType) ?? ['application:submit']
        const canSubmit = permissions.every(p => granted.includes(p))
        const canContinue = status === 'DRAFT' && Boolean(route) && granted.includes('application:edit') && granted.includes(handler?.getEditPermission(businessType))
        const app = createSSRApp(Component, { application: { status, businessType }, specializedEditRoute: route,
          canContinueSpecialized: canContinue, canSubmit })
        app.use(createPinia())
        app.component('ElButton', { setup(_, { slots }) { return () => h('button', slots.default?.()) } })
        const html = await renderToString(app)
        const hasSubmit = html.includes('重新提交申请') || html.includes('提交申请')
        const expected = canSubmit && (status === 'RETURNED' || (status === 'DRAFT' && !route))
        assert.equal(hasSubmit, expected, `${businessType} ${status} ${granted.join(',')}`)
        assert.equal(html.includes('继续编辑并提交'), canContinue, `${businessType} ${status} edit`)
      }
    }
  } finally { await server.close() }
})
