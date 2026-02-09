/**
 * ContractEditorPage - Monaco Editor 기반 Contract 편집
 */
import { useCallback, useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import { useQuery } from '@tanstack/react-query'
import { contractsApi } from '@/shared/api'
import { ChangeSummary, ExportPanel, ImpactGraph, MeaningPanel, SimulationPanel, ValidationPanel, WhyPanel } from '../components'
import { EditorHeader, EditorLoading, RightPanelTabs, useEditorCallbacks } from '../components/editor'
import type { RightPanelTab } from '../components/editor'
import { DEFAULT_YAML, MONACO_OPTIONS } from '../constants/editorOptions'
import { useCursorContext, useGitOutput, useImpactGraph, useSemanticDiff, useSimulation, useValidation, useWhyEngine } from '../hooks'
import './ContractEditorPage.css'

export function ContractEditorPage() {
  const { kind, id } = useParams<{ kind: string; id: string }>()
  const [searchParams] = useSearchParams()
  const isNew = searchParams.get('new') === 'true'
  const [yaml, setYaml] = useState(DEFAULT_YAML)
  const [activeTab, setActiveTab] = useState<RightPanelTab>('meaning')
  const [sampleData, setSampleData] = useState('{\n  "id": "sample-001"\n}')

  const { data: contract, isLoading: isLoadingContract } = useQuery({
    queryKey: ['contract', kind, id], queryFn: () => contractsApi.getDetail(kind!, id!), enabled: !!kind && !!id && !isNew,
  })
  useEffect(() => { if (contract?.content) setYaml(contract.content) }, [contract])
  const originalYaml = contract?.content ?? ''

  const { context, isLoading: isCursorLoading, updatePosition } = useCursorContext({ yaml })
  const { validation, isLoading: isValidationLoading, validate } = useValidation()
  const { diff, isLoading: isDiffLoading, computeDiff } = useSemanticDiff()
  // Why 탭이 활성화될 때만 설명 fetch
  const { explanation, contractExplanation, isAnalyzing, isLoadingExplanation, analyzeFailure } = useWhyEngine({ contractKind: kind, contractId: id, enabled: activeTab === 'why' })
  // Graph는 탭이 활성화될 때만 fetch (초기 로딩 최적화)
  const { nodes, edges, isLoading: isGraphLoading } = useImpactGraph({ contractKind: kind, contractId: id, enabled: !!kind && !!id && activeTab === 'graph' })
  const { result, isSimulating, simulate, sample, isGeneratingSample, generateSample } = useSimulation({ contractKind: kind, contractId: id })
  const { patch, prDescription, isExportingPatch, isExportingPR, exportPatch, exportPR, copyToClipboard } = useGitOutput({ contractKind: kind, contractId: id })

  useEffect(() => { if (sample?.data) setSampleData(sample.data) }, [sample])
  const { editorRef, monacoRef, handleEditorMount, goToLine, applyFix } = useEditorCallbacks({ onPositionChange: updatePosition })
  useEffect(() => { validate(yaml) }, [yaml, validate])
  useEffect(() => { if (originalYaml && yaml !== originalYaml) computeDiff(originalYaml, yaml) }, [yaml, originalYaml, computeDiff])

  useEffect(() => {
    if (!editorRef.current || !monacoRef.current || !validation) return
    const model = editorRef.current.getModel()
    if (!model) return
    monacoRef.current.editor.setModelMarkers(model, 'contract-validation', validation.errors.map(e => ({
      severity: monacoRef.current!.MarkerSeverity.Error, message: e.message,
      startLineNumber: e.line, startColumn: e.column, endLineNumber: e.line, endColumn: e.column + 10,
    })))
  }, [validation, editorRef, monacoRef])

  const jumpTo = useCallback((ref: { id: string; kind: string }) => { window.location.href = `/contracts/${ref.kind}/${encodeURIComponent(ref.id)}/edit` }, [])
  const downloadPatch = useCallback(() => {
    if (!patch) return
    const blob = new Blob([patch.patch], { type: 'text/plain' }), url = URL.createObjectURL(blob), a = document.createElement('a')
    a.href = url; a.download = `${id ?? 'contract'}.patch`; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
  }, [patch, id])

  if (isLoadingContract) return <EditorLoading />

  return (
    <div className="contract-editor">
      <EditorHeader kind={kind} id={id} />
      <div className="contract-editor__main">
        <div className="contract-editor__editor">
          <Editor height="100%" language="yaml" theme="vs-dark" value={yaml} onChange={v => setYaml(v ?? '')} onMount={handleEditorMount} options={MONACO_OPTIONS} />
        </div>
        <div className="contract-editor__panel">
          <RightPanelTabs activeTab={activeTab} onTabChange={setActiveTab} validationErrorCount={validation && !validation.valid ? validation.errors.length : undefined} hasDiffBreaking={diff?.breaking} diffDisabled={!originalYaml} />
          <div className="contract-editor__panel-content">
            {activeTab === 'meaning' && <MeaningPanel context={context} isLoading={isCursorLoading} onJumpToContract={jumpTo} />}
            {activeTab === 'validation' && <ValidationPanel validation={validation} isLoading={isValidationLoading} onGoToLine={goToLine} onApplyFix={applyFix} />}
            {activeTab === 'diff' && <ChangeSummary diff={diff} isLoading={isDiffLoading} />}
            {activeTab === 'why' && <WhyPanel explanation={explanation} contractExplanation={contractExplanation} isAnalyzing={isAnalyzing} isLoadingExplanation={isLoadingExplanation} onAnalyze={analyzeFailure} onJumpToContract={jumpTo} />}
            {activeTab === 'graph' && <ImpactGraph currentContractId={id ? `${kind}:${id}` : undefined} graphNodes={nodes} graphEdges={edges} isLoading={isGraphLoading} onNodeClick={jumpTo} />}
            {activeTab === 'simulate' && <SimulationPanel yaml={yaml} result={result} isSimulating={isSimulating} sampleData={sampleData} onSampleDataChange={setSampleData} onSimulate={() => simulate(yaml, sampleData)} onGenerateSample={kind && id ? generateSample : undefined} isGeneratingSample={isGeneratingSample} />}
            {activeTab === 'export' && <ExportPanel originalYaml={originalYaml} currentYaml={yaml} contractKind={kind} contractId={id} patch={patch} prDescription={prDescription} isExportingPatch={isExportingPatch} isExportingPR={isExportingPR} onExportPatch={() => exportPatch(originalYaml, yaml)} onExportPR={() => exportPR(originalYaml, yaml)} onCopyToClipboard={copyToClipboard} onDownloadPatch={downloadPatch} />}
          </div>
        </div>
      </div>
    </div>
  )
}
