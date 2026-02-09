/**
 * FileUpload Showcase - FileUpload 컴포넌트 전시
 */
import { useState } from 'react'
import { FileUpload, Alert } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function FileUploadShowcase() {
  const [uploadedFile, setUploadedFile] = useState<{ name: string; content: string } | null>(null)

  const handleFileSelect = (file: File, content: string) => {
    setUploadedFile({ name: file.name, content: content.substring(0, 200) + '...' })
  }

  return (
    <div className="showcase">
      <PageHeader
        title="FileUpload"
        description="파일 업로드를 위한 드래그 앤 드롭 컴포넌트입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 FileUpload</h3>
        <LivePreview>
          <div style={{ width: '100%', maxWidth: '500px' }}>
            <FileUpload
              accept=".json"
              onFileSelect={handleFileSelect}
              label="JSON 파일 업로드"
            />
            {uploadedFile && (
              <div style={{ marginTop: '1rem' }}>
                <Alert variant="success" size="sm">
                  파일 업로드 완료: {uploadedFile.name}
                </Alert>
              </div>
            )}
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>다양한 파일 타입</h3>
        <LivePreview>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', width: '100%', maxWidth: '500px' }}>
            <FileUpload
              accept=".json"
              onFileSelect={handleFileSelect}
              label="JSON 파일"
            />
            <FileUpload
              accept=".yaml,.yml"
              onFileSelect={handleFileSelect}
              label="YAML 파일"
            />
            <FileUpload
              accept=".txt"
              onFileSelect={handleFileSelect}
              label="텍스트 파일"
            />
          </div>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { FileUpload } from '@/shared/ui'

const handleFileSelect = (file: File, content: string) => {
  console.log('File:', file.name)
  console.log('Content:', content)
}

<FileUpload
  accept=".json"
  onFileSelect={handleFileSelect}
  label="JSON 파일 업로드"
/>`}
        </pre>
      </section>
    </div>
  )
}
