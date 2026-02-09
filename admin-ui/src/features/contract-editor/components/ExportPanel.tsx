/**
 * ExportPanel (Phase 6: Git Output)
 *
 * Contract 변경 내용을 Git 친화적 형식으로 내보내기.
 * - Unified Patch 미리보기 및 다운로드
 * - PR Description 미리보기 및 복사
 */
import { useCallback, useState } from 'react'
import { Button, Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui'
import type { ExportPatchResponse, ExportPRResponse } from '../api/types'
import './ExportPanel.css'

interface ExportPanelProps {
  originalYaml: string
  currentYaml: string
  contractKind?: string
  contractId?: string
  patch: ExportPatchResponse | null
  prDescription: ExportPRResponse | null
  isExportingPatch: boolean
  isExportingPR: boolean
  onExportPatch: (before: string, after: string) => void
  onExportPR: (before: string, after: string) => void
  onCopyToClipboard: (text: string) => Promise<boolean>
  onDownloadPatch: () => void
}

export function ExportPanel({
  originalYaml,
  currentYaml,
  contractKind,
  contractId,
  patch,
  prDescription,
  isExportingPatch,
  isExportingPR,
  onExportPatch,
  onExportPR,
  onCopyToClipboard,
  onDownloadPatch,
}: ExportPanelProps) {
  const [activeTab, setActiveTab] = useState('patch')
  const [copied, setCopied] = useState(false)

  const hasChanges = originalYaml !== currentYaml && originalYaml.length > 0

  const handleGeneratePatch = useCallback(() => onExportPatch(originalYaml, currentYaml), [originalYaml, currentYaml, onExportPatch])
  const handleGeneratePR = useCallback(() => onExportPR(originalYaml, currentYaml), [originalYaml, currentYaml, onExportPR])

  const handleCopy = useCallback(
    async (text: string) => {
      const success = await onCopyToClipboard(text)
      if (success) {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      }
    },
    [onCopyToClipboard]
  )

  if (!hasChanges) {
    return (
      <div className="export-panel export-panel--empty">
        <span className="export-panel__empty-icon">📤</span>
        <span className="export-panel__empty-text">
          No changes to export. Modify the contract to see export options.
        </span>
      </div>
    )
  }

  return (
    <div className="export-panel">
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="patch">Unified Patch</TabsTrigger>
          <TabsTrigger value="pr">PR Description</TabsTrigger>
        </TabsList>

        {/* Patch 탭 */}
        <TabsContent value="patch">
          <div className="export-panel__content">
            <div className="export-panel__actions">
              <Button
                variant="primary"
                onClick={handleGeneratePatch}
                disabled={isExportingPatch}
              >
                {isExportingPatch ? 'Generating...' : 'Generate Patch'}
              </Button>
            </div>

            {patch ? (
              <div className="export-panel__result">
                <div className="export-panel__stats">
                  <span className="export-panel__stat export-panel__stat--additions">
                    +{patch.additions}
                  </span>
                  <span className="export-panel__stat export-panel__stat--deletions">
                    -{patch.deletions}
                  </span>
                  <span className="export-panel__stat-file">{patch.filePath}</span>
                </div>

                <pre className="export-panel__patch">{patch.patch}</pre>

                <div className="export-panel__result-actions">
                  <Button
                    variant="secondary"
                    onClick={() => handleCopy(patch.patch)}
                  >
                    {copied ? '✓ Copied!' : 'Copy to Clipboard'}
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={onDownloadPatch}
                  >
                    Download .patch
                  </Button>
                </div>
              </div>
            ) : (
              <div className="export-panel__placeholder">
                Click "Generate Patch" to create a unified diff
              </div>
            )}
          </div>
        </TabsContent>

        {/* PR 탭 */}
        <TabsContent value="pr">
          <div className="export-panel__content">
            <div className="export-panel__actions">
              <Button
                variant="primary"
                onClick={handleGeneratePR}
                disabled={isExportingPR || !contractKind || !contractId}
              >
                {isExportingPR ? 'Generating...' : 'Generate PR Description'}
              </Button>
            </div>

            {prDescription ? (
              <div className="export-panel__result">
                <div className="export-panel__pr-header">
                  <h3 className="export-panel__pr-title">{prDescription.title}</h3>
                  <div className="export-panel__pr-labels">
                    {prDescription.labels.map((label) => (
                      <span key={label} className="export-panel__pr-label">
                        {label}
                      </span>
                    ))}
                  </div>
                </div>

                <pre className="export-panel__pr-body">{prDescription.body}</pre>

                <div className="export-panel__result-actions">
                  <Button
                    variant="secondary"
                    onClick={() => handleCopy(prDescription.body)}
                  >
                    {copied ? '✓ Copied!' : 'Copy Body'}
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() =>
                      handleCopy(`${prDescription.title}\n\n${prDescription.body}`)
                    }
                  >
                    Copy All
                  </Button>
                </div>
              </div>
            ) : (
              <div className="export-panel__placeholder">
                {!contractKind || !contractId
                  ? 'Save the contract first to generate PR description'
                  : 'Click "Generate PR Description" to create a PR template'}
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
