const { request, uploadFile } = require('./request')

function list(params) { return request({ url: '/api/residence-permits', data: params }) }
function detail(id) { return request({ url: `/api/residence-permits/${id}` }) }
function expiring(days) { return request({ url: '/api/residence-permits/expiring', data: { days } }) }
function createEndorsement(permitId, data) { return request({ url: `/api/residence-permits/applications/${permitId}/endorsement`, method: 'POST', data }) }
function application(id) { return request({ url: `/api/residence-permits/applications/${id}` }) }
function uploadMaterial(applicationId, filePath, materialType, materialName) {
  return uploadFile({
    url: `/api/applications/${applicationId}/materials`,
    filePath,
    name: 'file',
    formData: { materialType, materialName, requiredFlag: 'true' }
  })
}

module.exports = { list, detail, expiring, createEndorsement, application, uploadMaterial }
