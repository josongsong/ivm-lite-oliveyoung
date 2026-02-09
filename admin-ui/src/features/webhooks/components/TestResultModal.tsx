import { Button, Modal } from '@/shared/ui'
import type { TestResult } from '../types/webhooks'

interface TestResultModalProps {
  result: TestResult | null
  onClose: () => void
}

export function TestResultModal({ result, onClose }: TestResultModalProps) {
  return (
    <Modal
      isOpen={result !== null}
      onClose={onClose}
      title="테스트 결과"
      size="sm"
      footer={
        <Button variant="secondary" onClick={onClose}>
          닫기
        </Button>
      }
    >
      {result ? (
        <div className={`test-result ${result.success ? 'success' : 'failed'}`}>
          <div className="result-status">
            {result.success ? '✅ 성공' : '❌ 실패'}
          </div>
          {result.statusCode ? (
            <div className="result-detail">
              <span className="label">응답 코드:</span> {result.statusCode}
            </div>
          ) : null}
          {result.latencyMs ? (
            <div className="result-detail">
              <span className="label">지연:</span> {result.latencyMs}ms
            </div>
          ) : null}
          {result.errorMessage ? (
            <div className="result-detail error">
              <span className="label">에러:</span> {result.errorMessage}
            </div>
          ) : null}
        </div>
      ) : null}
    </Modal>
  )
}
