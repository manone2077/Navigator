import http from './http.js'

export const listSites = () => http.get('all').then((r) => r.data || [])
export const addSite = (site) => http.post('add', site)
export const updateSite = (site) => http.post('update', site)
export const deleteSite = (id) => http.post('delete', { id })
export const deleteAllSites = () => http.post('deleteAll')
export const saveSort = (ids) => http.post('sort', ids)
export const speedtest = () => http.post('speedtest').then((r) => r.data || [])
export const exportSites = () => http.get('export').then((r) => r.data || [])
export const importSites = (sites) => http.post('import', sites)

export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('upload', form).then((r) => '/images/' + r.data)
}
