import { Navigate, Route, Routes } from 'react-router'
import { SearchEventsPage } from '../features/search/pages/SearchEventsPage.tsx'
import { SearchPage } from '../features/search/pages/SearchPage.tsx'
import { SourceDetailPage } from '../features/source/pages/SourceDetailPage.tsx'
import { SourcesPage } from '../features/source/pages/SourcesPage.tsx'
import { UploadPage } from '../features/source/pages/UploadPage.tsx'
import { NotFoundState } from '../shared/components/NotFoundState.tsx'
import { DEFAULT_WORKSPACE_ID, UPLOAD_SECTION, workspaceHomePath } from '../shared/workspace/workspaceRules.ts'
import { NotFoundPage } from './NotFoundPage.tsx'
import { WorkspaceHomeRedirect } from './WorkspaceHomeRedirect.tsx'
import { WorkspaceLayout } from './WorkspaceLayout.tsx'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={workspaceHomePath(DEFAULT_WORKSPACE_ID)} replace />} />
      <Route path="/w/:workspaceId" element={<WorkspaceLayout />}>
        <Route index element={<WorkspaceHomeRedirect />} />
        <Route path="arama" element={<SearchPage />} />
        <Route path="arama-kayitlari" element={<SearchEventsPage />} />
        <Route path="kaynaklar" element={<SourcesPage />} />
        <Route path="kaynaklar/:sourceId" element={<SourceDetailPage />} />
        <Route path={UPLOAD_SECTION} element={<UploadPage />} />
        <Route
          path="*"
          element={
            <NotFoundState
              title="Sayfa bulunamadı"
              linkTo={workspaceHomePath(DEFAULT_WORKSPACE_ID)}
              linkLabel="Varsayılan çalışma alanına dön"
            />
          }
        />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
